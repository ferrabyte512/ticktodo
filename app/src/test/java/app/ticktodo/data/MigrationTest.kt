package app.ticktodo.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ticktodo.data.local.AppDatabase
import app.ticktodo.data.local.DB_VERSION
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every schema version an installed app may still have must upgrade to the
 * current one without losing rows. Uses the exported schemas in app/schemas.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    // Room 2.8's helper compares the requested path with the configured name, so the
    // full path is used for both calls.
    private val dbName: String = ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath("migration-test").absolutePath

    @get:Rule
    val helper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `v1 data survives every migration up to the current version`() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO tasks (id, ownerUid, listId, parentId, title, notes, priority, dueEpochDay, dueMinuteOfDay, " +
                    "reminderOffsetMinutes, recurrenceRule, tags, completed, completedAt, createdAt, updatedAt, deleted, dirty, " +
                    "localRevision, serverUpdatedAt, snoozedUntil, lastNotifiedTrigger) VALUES " +
                    "('t1', 'alice', NULL, NULL, 'Keep me', 'note', 2, 20000, 540, 15, NULL, ',home,', 0, NULL, 1, 1, 0, 1, 1, NULL, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO task_lists (id, ownerUid, name, colorIndex, createdAt, updatedAt, deleted, dirty, localRevision, serverUpdatedAt) " +
                    "VALUES ('l1', 'alice', 'Work', 3, 1, 1, 0, 1, 1, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, DB_VERSION, true)

        db.query("SELECT title, priority, tags, focusPlan, sortOrder FROM tasks WHERE id = 't1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Keep me", c.getString(0))
            assertEquals(2, c.getInt(1))
            assertEquals(",home,", c.getString(2))
            assertTrue(c.isNull(3), "focusPlan added as null")
            assertTrue(c.isNull(4), "sortOrder added as null")
        }
        db.query("SELECT name FROM task_lists WHERE id = 'l1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Work", c.getString(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'tasks'").use { c ->
            val names = buildList { while (c.moveToNext()) add(c.getString(0)) }
            assertTrue(names.any { it.contains("completed_completedAt") }, "v4 index present: $names")
        }
        db.query("SELECT COUNT(*) FROM focus_sessions").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)) }
        db.close()
    }
}
