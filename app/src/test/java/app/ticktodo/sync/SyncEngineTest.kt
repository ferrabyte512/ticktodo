package app.ticktodo.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ticktodo.data.local.AppDatabase
import app.ticktodo.data.remote.RemoteException
import app.ticktodo.data.remote.RemoteStore.Collection
import app.ticktodo.data.repository.TaskRepository
import app.ticktodo.domain.model.TaskDraft
import app.ticktodo.testing.FakeClock
import app.ticktodo.testing.FakeRemoteStore
import app.ticktodo.testing.FakeSession
import app.ticktodo.testing.RecordingEffects
import app.ticktodo.testing.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** One simulated phone: its own database and repository, sharing the fake server. */
private class Device(val remote: FakeRemoteStore, uid: String, val clock: FakeClock = FakeClock()) {
    val db: AppDatabase = inMemoryDb()
    val session = FakeSession(uid)
    val repo = TaskRepository(db, session, clock, RecordingEffects())
    val engine = SyncEngine(db, remote, session, clock, SyncLock())
    suspend fun sync(throttlePull: Boolean = false) = engine.sync(session.uid.value!!, throttlePull = throttlePull)
    suspend fun task(id: String) = db.taskDao().get(session.uid.value!!, id)
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SyncEngineTest {
    private val remote = FakeRemoteStore()
    private val devices = mutableListOf<Device>()
    private fun device(uid: String = "alice", clock: FakeClock = FakeClock()) = Device(remote, uid, clock).also { devices += it }

    @After fun close() = devices.forEach { it.db.close() }

    @Test fun `local changes are pushed and marked clean`() = runBlocking<Unit> {
        val a = device()
        val id = a.repo.create(TaskDraft("Buy milk"))
        assertTrue(a.task(id)!!.dirty)

        val outcome = a.sync()

        assertIs<SyncEngine.Outcome.Success>(outcome)
        assertEquals(1, outcome.pushed)
        assertEquals("Buy milk", remote.get("alice", Collection.TASKS, id)!!.data["title"])
        assertEquals(false, a.task(id)!!.dirty)
    }

    @Test fun `a second device receives the task`() = runBlocking<Unit> {
        val a = device(); val b = device()
        val id = a.repo.create(TaskDraft("Shared"))
        a.sync(); b.sync()
        assertEquals("Shared", b.task(id)!!.title)
    }

    @Test fun `deletion propagates to other devices`() = runBlocking<Unit> {
        val a = device(); val b = device()
        val id = a.repo.create(TaskDraft("Temp"))
        a.sync(); b.sync()
        a.repo.delete(listOf(id))
        a.sync(); b.sync()
        assertTrue(b.task(id)!!.deleted)
        assertNull(b.repo.observeTask(id).first())
    }

    @Test fun `two devices edit the same task offline - the later edit wins everywhere`() = runBlocking<Unit> {
        val clockA = FakeClock(1_000_000); val clockB = FakeClock(1_000_000)
        val a = device(clock = clockA); val b = device(clock = clockB)
        val id = a.repo.create(TaskDraft("Original"))
        a.sync(); b.sync()

        // Both offline: A edits first, B edits later.
        clockA.advance(10_000)
        a.repo.update(a.repo.observeTask(id).first()!!.copy(title = "From A"))
        clockB.advance(20_000)
        b.repo.update(b.repo.observeTask(id).first()!!.copy(title = "From B"))

        // A reconnects first and pushes; then B; then A pulls.
        a.sync(); b.sync(); a.sync()

        assertEquals("From B", a.task(id)!!.title)
        assertEquals("From B", b.task(id)!!.title)
        assertEquals("From B", remote.get("alice", Collection.TASKS, id)!!.data["title"])
    }

    @Test fun `an older offline edit does not overwrite a newer synced one`() = runBlocking<Unit> {
        val clockA = FakeClock(1_000_000); val clockB = FakeClock(1_000_000)
        val a = device(clock = clockA); val b = device(clock = clockB)
        val id = a.repo.create(TaskDraft("Original"))
        a.sync(); b.sync()

        clockA.advance(5_000)
        a.repo.update(a.repo.observeTask(id).first()!!.copy(title = "Old offline edit"))
        clockB.advance(50_000)
        b.repo.update(b.repo.observeTask(id).first()!!.copy(title = "Newer edit"))
        b.sync()
        val outcome = a.sync()

        assertIs<SyncEngine.Outcome.Success>(outcome)
        assertTrue(outcome.conflicts > 0)
        assertEquals("Newer edit", a.task(id)!!.title)
        assertEquals(false, a.task(id)!!.dirty)
    }

    @Test fun `repeating task completed on two devices offline creates one next occurrence`() = runBlocking<Unit> {
        val a = device(); val b = device()
        val id = a.repo.create(
            TaskDraft("Water plants", dueDate = java.time.LocalDate.of(2027, 1, 15),
                recurrence = app.ticktodo.domain.model.Recurrence(app.ticktodo.domain.model.Recurrence.Frequency.DAILY)),
        )
        a.sync(); b.sync()
        val nextA = a.repo.setCompleted(id, true)!!.nextOccurrenceId
        val nextB = b.repo.setCompleted(id, true)!!.nextOccurrenceId
        assertEquals(nextA, nextB)
        a.sync(); b.sync(); a.sync()
        assertEquals(2, remote.all("alice", Collection.TASKS).size)
    }

    @Test fun `users never see each other's data`() = runBlocking<Unit> {
        val alice = device("alice"); val bob = device("bob")
        alice.repo.create(TaskDraft("Alice's secret"))
        alice.sync(); bob.sync()
        assertEquals(0, remote.all("bob", Collection.TASKS).size)
        assertEquals(emptyList(), bob.repo.observeScope(app.ticktodo.domain.model.TaskScope.All, java.time.LocalDate.now()).first())
    }

    @Test fun `sync is skipped when the signed-in user changed`() = runBlocking<Unit> {
        val a = device()
        a.repo.create(TaskDraft("x"))
        a.session.uid.value = "someone-else"
        assertEquals(SyncEngine.Outcome.Skipped, a.engine.sync("alice"))
        assertEquals(0, remote.pushCalls)
    }

    @Test fun `network failure keeps changes and records a retryable error`() = runBlocking<Unit> {
        val a = device()
        val id = a.repo.create(TaskDraft("Offline"))
        remote.failWith = RemoteException.Code.NETWORK
        val outcome = a.sync()
        assertIs<SyncEngine.Outcome.Failed>(outcome)
        assertTrue(outcome.retryable)
        assertTrue(a.task(id)!!.dirty)
        assertEquals("NETWORK", a.db.syncStateDao().get("alice")!!.lastErrorCode)

        remote.failWith = null
        assertIs<SyncEngine.Outcome.Success>(a.sync())
        assertNull(a.db.syncStateDao().get("alice")!!.lastErrorCode)
    }

    @Test fun `permission denied is not retried and is reported`() = runBlocking<Unit> {
        val a = device()
        a.repo.create(TaskDraft("x"))
        remote.failWith = RemoteException.Code.PERMISSION_DENIED
        val outcome = a.sync()
        assertIs<SyncEngine.Outcome.Failed>(outcome)
        assertEquals(false, outcome.retryable)
        assertEquals(SyncError.ACCESS_DENIED, SyncStatus(lastErrorCode = a.db.syncStateDao().get("alice")!!.lastErrorCode).error)
    }

    @Test fun `a refused document does not block the others`() = runBlocking<Unit> {
        val a = device()
        val bad = a.repo.create(TaskDraft("bad"))
        val good = a.repo.create(TaskDraft("good"))
        remote.rejectIds += bad
        val outcome = a.sync()
        assertIs<SyncEngine.Outcome.Success>(outcome)
        assertEquals(1, outcome.rejected)
        assertNotNull(remote.get("alice", Collection.TASKS, good))
        assertTrue(a.task(bad)!!.dirty)
    }

    @Test fun `running sync again changes nothing`() = runBlocking<Unit> {
        val a = device()
        a.repo.create(TaskDraft("x"))
        a.sync()
        val pushes = remote.pushCalls
        val second = a.sync()
        assertIs<SyncEngine.Outcome.Success>(second)
        assertEquals(0, second.pushed)
        assertEquals(pushes, remote.pushCalls)
    }

    @Test fun `my order position syncs with the task`() = runBlocking<Unit> {
        val a = device(); val b = device()
        val id = a.repo.create(TaskDraft("Pinned"))
        a.repo.setSortOrders(mapOf(id to -42.5))
        a.sync(); b.sync()
        assertEquals(-42.5, b.repo.observeTask(id).first()!!.sortOrder)
    }

    @Test fun `focus plan syncs with the task`() = runBlocking<Unit> {
        val a = device(); val b = device()
        val plan = app.ticktodo.domain.focus.FocusPlan(50, 10)
        val id = a.repo.create(TaskDraft("Deep work", focusPlan = plan))
        a.sync(); b.sync()
        assertEquals(plan, b.repo.observeTask(id).first()!!.focusPlan)
    }

    // ---- Free-tier read behaviour --------------------------------------------------------------

    @Test fun `pages split inside a group of equal commit times lose nothing and are never re-read`() = runBlocking<Unit> {
        val a = device(); val b = device()
        remote.freezeServerClock = true
        val ids = (1..5).map { a.repo.create(TaskDraft("T$it")) }
        a.sync()
        remote.freezeServerClock = false

        remote.pageSizeOverride = 2
        val first = b.sync()
        assertIs<SyncEngine.Outcome.Success>(first)
        ids.forEach { assertNotNull(b.task(it), "b should have $it") }

        val before = remote.billedReads
        val second = b.sync()
        assertIs<SyncEngine.Outcome.Success>(second)
        assertEquals(0, second.pulled)
        // Nothing new: one empty page per collection, nothing at the cursor read again.
        assertEquals(2, remote.billedReads - before)
    }

    @Test fun `a device's own push is not reported as a change when it comes back`() = runBlocking<Unit> {
        val a = device()
        val id = a.repo.create(TaskDraft("Mine"))
        a.sync()
        assertNull(a.task(id)!!.serverUpdatedAt)

        val again = a.sync()
        assertIs<SyncEngine.Outcome.Success>(again)
        assertEquals(0, again.pulled)
        assertTrue(again.changedTaskIds.isEmpty())
        assertNotNull(a.task(id)!!.serverUpdatedAt, "server time recorded quietly")
        assertEquals(false, a.task(id)!!.dirty)
    }

    @Test fun `a throttled run pushes at once but pulls only after ten minutes`() = runBlocking<Unit> {
        val a = device(); val b = device()
        a.sync()
        val fromB = b.repo.create(TaskDraft("From B"))
        b.sync()

        a.clock.advance(60_000)
        val fromA = a.repo.create(TaskDraft("From A"))
        val pulls = remote.pullCalls
        val throttled = a.sync(throttlePull = true)
        assertIs<SyncEngine.Outcome.Success>(throttled)
        assertEquals(false, throttled.pulledFromServer)
        assertEquals(pulls, remote.pullCalls)
        assertNotNull(remote.get("alice", Collection.TASKS, fromA), "pushed without waiting")
        assertEquals(false, a.task(fromA)!!.dirty)
        assertNull(a.task(fromB), "pull skipped")

        a.clock.advance(SyncEngine.PULL_THROTTLE_MS)
        val later = a.sync(throttlePull = true)
        assertIs<SyncEngine.Outcome.Success>(later)
        assertEquals(true, later.pulledFromServer)
        assertNotNull(a.task(fromB))

        // An explicit sync is never throttled.
        val fromB2 = b.repo.create(TaskDraft("From B again")); b.sync()
        a.sync()
        assertNotNull(a.task(fromB2))
    }

    @Test fun `a deletion made during a throttled run reaches the other device`() = runBlocking<Unit> {
        val a = device(); val b = device()
        val id = a.repo.create(TaskDraft("Soon gone"))
        a.sync(); b.sync()
        assertNotNull(b.task(id))
        a.repo.delete(listOf(id))
        a.sync(throttlePull = true)
        b.sync()
        assertEquals(true, b.task(id)!!.deleted)
    }

    @Test fun `after a failed run the next one resumes from the saved cursor`() = runBlocking<Unit> {
        val a = device(); val b = device()
        repeat(3) { a.repo.create(TaskDraft("Old $it")) }
        a.sync(); b.sync()
        val new1 = a.repo.create(TaskDraft("New 1")); val new2 = a.repo.create(TaskDraft("New 2"))
        a.sync()

        remote.failWith = RemoteException.Code.NETWORK
        assertIs<SyncEngine.Outcome.Failed>(b.sync())
        remote.failWith = null

        val before = remote.billedReads
        assertIs<SyncEngine.Outcome.Success>(b.sync())
        assertNotNull(b.task(new1)); assertNotNull(b.task(new2))
        // Two new task documents plus one empty lists page: the three old tasks were not read again.
        assertEquals(3, remote.billedReads - before)
    }

    @Test fun `a typical day of use stays small in reads`() = runBlocking<Unit> {
        // One device, already synced: 8 app opens, 6 edits spread over the day, 2 periodic runs.
        val a = device()
        a.repo.create(TaskDraft("Existing")); a.sync()
        val before = remote.billedReads
        var writes = remote.pushCalls
        repeat(8) { open ->
            a.clock.advance(90 * 60_000L)
            a.sync(throttlePull = true)                      // app opened
            if (open % 4 == 1) a.sync()                      // periodic run
            if (open < 6) { a.repo.create(TaskDraft("Edit $open")); a.sync(throttlePull = true) }
        }
        val reads = remote.billedReads - before
        writes = remote.pushCalls - writes
        println("READS_PER_DAY=$reads WRITES_PER_DAY=$writes")
        assertEquals(6, writes)
        assertTrue(reads <= 40, "reads for a busy day should stay well under 50, got $reads")
    }
}
