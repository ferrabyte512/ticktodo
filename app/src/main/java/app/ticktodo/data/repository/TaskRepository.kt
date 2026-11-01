package app.ticktodo.data.repository

import androidx.room.withTransaction
import app.ticktodo.core.Clock
import app.ticktodo.data.auth.UserSession
import app.ticktodo.data.local.AppDatabase
import app.ticktodo.data.local.TagCodec
import app.ticktodo.data.local.TaskEntity
import app.ticktodo.data.local.TaskListEntity
import app.ticktodo.data.local.ScopeCounts
import app.ticktodo.data.local.toDomain
import app.ticktodo.data.local.toMinuteOfDay
import app.ticktodo.domain.ConflictResolver
import app.ticktodo.domain.RecurrenceCalculator
import app.ticktodo.domain.TaskValidator
import app.ticktodo.domain.model.Priority
import app.ticktodo.domain.model.Recurrence
import app.ticktodo.domain.model.Task
import app.ticktodo.domain.model.TaskDraft
import app.ticktodo.domain.model.TaskItem
import app.ticktodo.domain.model.TaskList
import app.ticktodo.domain.model.TaskScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

class InvalidInputException(val error: TaskValidator.Error) : IllegalArgumentException(error.toString())

/** Result of completing a task, kept so the UI can offer Undo. */
data class CompletionChange(val taskId: String, val completed: Boolean, val nextOccurrenceId: String?)

/**
 * All task reads and writes go through here. Writes are local-first: they hit
 * Room immediately, are marked dirty, and a sync is scheduled. Every query is
 * scoped to the signed-in uid.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TaskRepository @Inject constructor(
    private val db: AppDatabase,
    private val session: UserSession,
    private val clock: Clock,
    private val effects: TaskChangeEffects,
) {
    private val taskDao = db.taskDao()
    private val listDao = db.taskListDao()

    private fun <T> perUser(empty: T, block: (String) -> Flow<T>): Flow<T> =
        session.uid.flatMapLatest { uid -> if (uid == null) flowOf(empty) else block(uid) }

    // ---- Reads ---------------------------------------------------------------------------------

    fun observeScope(scope: TaskScope, today: LocalDate): Flow<List<TaskItem>> = perUser(emptyList()) { uid ->
        val day = today.toEpochDay()
        when (scope) {
            TaskScope.Today -> taskDao.observeToday(uid, day)
            TaskScope.Upcoming -> taskDao.observeUpcoming(uid, day)
            TaskScope.Inbox -> taskDao.observeInbox(uid)
            TaskScope.All -> taskDao.observeAllActive(uid)
            TaskScope.Completed -> taskDao.observeCompleted(uid, COMPLETED_LIMIT)
            is TaskScope.InList -> taskDao.observeInList(uid, scope.listId)
            is TaskScope.WithTag -> taskDao.observeWithTag(uid, scope.tag)
        }.map { rows -> rows.map { it.toDomain() } }
    }

    fun search(query: String): Flow<List<TaskItem>> = perUser(emptyList()) { uid ->
        if (query.isBlank()) flowOf(emptyList())
        else taskDao.search(uid, TaskValidator.likePattern(query.take(100))).map { rows -> rows.map { it.toDomain() } }
    }

    fun observeTask(id: String): Flow<Task?> = perUser(null) { uid -> taskDao.observeTask(uid, id).map { it?.toDomain() } }

    fun observeSubtasks(parentId: String): Flow<List<Task>> = perUser(emptyList()) { uid ->
        taskDao.observeSubtasks(uid, parentId).map { rows -> rows.map { it.toDomain() } }
    }

    fun observeLists(): Flow<List<TaskList>> = perUser(emptyList()) { uid ->
        listDao.observeAll(uid).map { rows -> rows.map { it.toDomain() } }
    }

    fun observeTags(): Flow<List<String>> = perUser(emptyList()) { uid ->
        taskDao.observeTagStrings(uid).map { rows -> rows.flatMap(TagCodec::decode).distinct().sorted() }
    }

    /** Top-level tasks completed on [day] (device time zone). */
    fun observeCompletedOn(day: LocalDate): Flow<Int> = perUser(0) { uid ->
        val start = day.atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        taskDao.observeCompletedBetween(uid, start, end)
    }

    fun observeScopeCounts(today: LocalDate): Flow<ScopeCounts> = perUser(ScopeCounts(0, 0, 0, 0)) { uid ->
        taskDao.observeScopeCounts(uid, today.toEpochDay())
    }

    fun observeListCounts(): Flow<Map<String, Int>> = perUser(emptyMap()) { uid ->
        taskDao.observeListCounts(uid).map { rows -> rows.associate { it.listId to it.count } }
    }

    fun observeHasListedTasks(): Flow<Boolean> = perUser(false) { uid -> taskDao.observeHasListedTasks(uid) }

    fun observePendingChanges(): Flow<Int> = perUser(0) { uid ->
        combine(taskDao.observeDirtyCount(uid), listDao.observeDirtyCount(uid)) { a, b -> a + b }
    }

    // ---- Task writes ---------------------------------------------------------------------------

    suspend fun create(draft: TaskDraft): String {
        val uid = session.requireUid()
        val title = TaskValidator.cleanTitle(draft.title)
        val notes = TaskValidator.cleanNotes(draft.notes)
        TaskValidator.validateTitle(title)?.let { throw InvalidInputException(it) }
        TaskValidator.validateNotes(notes)?.let { throw InvalidInputException(it) }
        TaskValidator.validateReminder(draft.reminderOffsetMinutes)?.let { throw InvalidInputException(it) }
        val now = clock.now()
        val id = UUID.randomUUID().toString()
        db.withTransaction {
            val parentId = draft.parentId?.takeIf { taskDao.get(uid, it)?.takeUnless(TaskEntity::deleted) != null }
            val hasDate = draft.dueDate != null && parentId == null
            taskDao.insert(
                TaskEntity(
                    id = id,
                    ownerUid = uid,
                    listId = draft.listId?.takeIf { parentId == null && listDao.get(uid, it)?.deleted == false },
                    parentId = parentId,
                    title = title,
                    notes = notes,
                    priority = draft.priority.level,
                    dueEpochDay = if (hasDate) draft.dueDate!!.toEpochDay() else null,
                    dueMinuteOfDay = if (hasDate) draft.dueTime?.toMinuteOfDay() else null,
                    reminderOffsetMinutes = if (hasDate) draft.reminderOffsetMinutes else null,
                    recurrenceRule = if (hasDate) draft.recurrence?.encode() else null,
                    tags = TagCodec.encode(TaskValidator.cleanTags(draft.tags)),
                    focusPlan = draft.focusPlan?.encode(),
                    completed = false,
                    completedAt = null,
                    createdAt = now,
                    updatedAt = now,
                    deleted = false,
                    dirty = true,
                    localRevision = 1,
                    serverUpdatedAt = null,
                ),
            )
        }
        effects.tasksChanged(listOf(id))
        return id
    }

    /** Saves the editable fields of [task]. Recurrence and reminders need a due date and are dropped without one. */
    suspend fun update(task: Task) {
        val uid = session.requireUid()
        val title = TaskValidator.cleanTitle(task.title)
        val notes = TaskValidator.cleanNotes(task.notes)
        TaskValidator.validateTitle(title)?.let { throw InvalidInputException(it) }
        TaskValidator.validateNotes(notes)?.let { throw InvalidInputException(it) }
        TaskValidator.validateReminder(task.reminderOffsetMinutes)?.let { throw InvalidInputException(it) }
        db.withTransaction {
            val current = taskDao.get(uid, task.id)?.takeUnless { it.deleted } ?: return@withTransaction
            val hasDate = task.dueDate != null && current.parentId == null
            val listId = task.listId?.takeIf { current.parentId == null && listDao.get(uid, it)?.deleted == false }
            val changed = current.copy(
                title = title,
                notes = notes,
                listId = listId,
                priority = task.priority.level,
                dueEpochDay = if (hasDate) task.dueDate!!.toEpochDay() else null,
                dueMinuteOfDay = if (hasDate) task.dueTime?.toMinuteOfDay() else null,
                reminderOffsetMinutes = if (hasDate) task.reminderOffsetMinutes else null,
                recurrenceRule = if (hasDate) task.recurrence?.encode() else null,
                tags = TagCodec.encode(TaskValidator.cleanTags(task.tags)),
                focusPlan = task.focusPlan?.encode(),
            )
            if (changed == current) return@withTransaction
            // A changed schedule means the old snooze and "already notified" marker no longer apply.
            val scheduleChanged = changed.dueEpochDay != current.dueEpochDay ||
                changed.dueMinuteOfDay != current.dueMinuteOfDay ||
                changed.reminderOffsetMinutes != current.reminderOffsetMinutes
            taskDao.upsert(
                changed.edited().let {
                    if (scheduleChanged) it.copy(snoozedUntil = null, lastNotifiedTrigger = null) else it
                },
            )
        }
        effects.tasksChanged(listOf(task.id))
    }

    suspend fun setCompleted(id: String, completed: Boolean): CompletionChange? {
        val uid = session.requireUid()
        var change: CompletionChange? = null
        val touched = mutableListOf(id)
        db.withTransaction {
            val task = taskDao.get(uid, id)?.takeUnless { it.deleted } ?: return@withTransaction
            if (task.completed == completed) return@withTransaction
            val now = clock.now()
            taskDao.upsert(
                task.edited().copy(completed = completed, completedAt = if (completed) now else null, snoozedUntil = null),
            )
            var nextId: String? = null
            if (completed && task.parentId == null) {
                nextId = createNextOccurrence(uid, task)?.also { touched += it }
            }
            change = CompletionChange(id, completed, nextId)
        }
        if (change != null) effects.tasksChanged(touched)
        return change
    }

    suspend fun undoCompletion(change: CompletionChange) {
        setCompleted(change.taskId, !change.completed)
        change.nextOccurrenceId?.let { delete(listOf(it)) }
    }

    /** Soft-deletes the tasks and their subtasks. Returns every affected id, for Undo. */
    suspend fun delete(ids: Collection<String>): List<String> {
        val uid = session.requireUid()
        if (ids.isEmpty()) return emptyList()
        val affected = mutableListOf<String>()
        db.withTransaction {
            val rows = taskDao.getMany(uid, ids) + taskDao.getChildren(uid, ids)
            rows.distinctBy { it.id }.filterNot { it.deleted }.forEach {
                taskDao.upsert(it.edited().copy(deleted = true, snoozedUntil = null))
                affected += it.id
            }
        }
        if (affected.isNotEmpty()) effects.tasksChanged(affected)
        return affected
    }

    suspend fun restore(ids: Collection<String>) {
        val uid = session.requireUid()
        if (ids.isEmpty()) return
        db.withTransaction {
            taskDao.getMany(uid, ids).filter { it.deleted }.forEach { taskDao.upsert(it.edited().copy(deleted = false)) }
        }
        effects.tasksChanged(ids)
    }

    /** Sets each task's position in "My order". */
    suspend fun setSortOrders(orders: Map<String, Double>) {
        if (orders.isEmpty()) return
        bulkEdit(orders.keys) { row -> orders[row.id]?.let { row.copy(sortOrder = it) } ?: row }
    }

    suspend fun setPriority(ids: Collection<String>, priority: Priority) = bulkEdit(ids) { it.copy(priority = priority.level) }

    suspend fun moveToList(ids: Collection<String>, listId: String?) {
        val uid = session.requireUid()
        val target = listId?.takeIf { listDao.get(uid, it)?.deleted == false }
        bulkEdit(ids) { if (it.parentId == null) it.copy(listId = target) else it }
    }

    suspend fun completeMany(ids: Collection<String>): List<CompletionChange> =
        ids.mapNotNull { setCompleted(it, true) }

    private suspend fun bulkEdit(ids: Collection<String>, transform: (TaskEntity) -> TaskEntity) {
        val uid = session.requireUid()
        val changed = mutableListOf<String>()
        db.withTransaction {
            taskDao.getMany(uid, ids).filterNot { it.deleted }.forEach { row ->
                val updated = transform(row)
                if (updated != row) {
                    taskDao.upsert(updated.edited())
                    changed += row.id
                }
            }
        }
        if (changed.isNotEmpty()) effects.tasksChanged(changed)
    }

    /**
     * Creates the next instance of a repeating task. The id is derived from the
     * completed task's id, so two devices completing the same task offline
     * produce the same next instance instead of duplicates.
     */
    private suspend fun createNextOccurrence(uid: String, task: TaskEntity): String? {
        val rule = Recurrence.parse(task.recurrenceRule) ?: return null
        val due = task.dueEpochDay ?: return null
        val nextId = nextOccurrenceId(task.id)
        val existing = taskDao.get(uid, nextId)
        if (existing != null && !existing.deleted) return null
        val now = clock.now()
        val nextDue = RecurrenceCalculator.nextDueDate(LocalDate.ofEpochDay(due), rule, clock.today())
        taskDao.upsert(
            task.copy(
                id = nextId,
                dueEpochDay = nextDue.toEpochDay(),
                completed = false,
                completedAt = null,
                createdAt = now,
                updatedAt = ConflictResolver.nextEditTime(now, existing?.updatedAt),
                deleted = false,
                dirty = true,
                localRevision = (existing?.localRevision ?: 0) + 1,
                serverUpdatedAt = existing?.serverUpdatedAt,
                snoozedUntil = null,
                lastNotifiedTrigger = null,
            ),
        )
        taskDao.getChildren(uid, listOf(task.id)).filterNot { it.deleted }.forEach { child ->
            val childId = nextOccurrenceId(child.id)
            val prior = taskDao.get(uid, childId)
            taskDao.upsert(
                child.copy(
                    id = childId,
                    parentId = nextId,
                    completed = false,
                    completedAt = null,
                    createdAt = now,
                    updatedAt = ConflictResolver.nextEditTime(now, prior?.updatedAt),
                    deleted = false,
                    dirty = true,
                    localRevision = (prior?.localRevision ?: 0) + 1,
                    serverUpdatedAt = prior?.serverUpdatedAt,
                ),
            )
        }
        return nextId
    }

    // ---- Lists ---------------------------------------------------------------------------------

    suspend fun createList(name: String, colorIndex: Int): String {
        val uid = session.requireUid()
        val clean = TaskValidator.cleanTitle(name)
        TaskValidator.validateListName(clean)?.let { throw InvalidInputException(it) }
        val now = clock.now()
        val id = UUID.randomUUID().toString()
        listDao.upsert(
            TaskListEntity(id, uid, clean, colorIndex.coerceIn(0, 11), now, now, deleted = false, dirty = true, localRevision = 1, serverUpdatedAt = null),
        )
        effects.listsChanged()
        return id
    }

    suspend fun updateList(id: String, name: String, colorIndex: Int) {
        val uid = session.requireUid()
        val clean = TaskValidator.cleanTitle(name)
        TaskValidator.validateListName(clean)?.let { throw InvalidInputException(it) }
        val current = listDao.get(uid, id)?.takeUnless { it.deleted } ?: return
        listDao.upsert(current.copy(name = clean, colorIndex = colorIndex.coerceIn(0, 11)).edited())
        effects.listsChanged()
    }

    /** Deletes the list; its tasks move to the Inbox rather than being deleted. */
    suspend fun deleteList(id: String) {
        val uid = session.requireUid()
        val moved = mutableListOf<String>()
        db.withTransaction {
            val current = listDao.get(uid, id)?.takeUnless { it.deleted } ?: return@withTransaction
            listDao.upsert(current.copy(deleted = true).edited())
            taskDao.getMany(uid, taskIdsInList(uid, id)).forEach {
                taskDao.upsert(it.copy(listId = null).edited())
                moved += it.id
            }
        }
        effects.listsChanged()
        if (moved.isNotEmpty()) effects.tasksChanged(moved)
    }

    private suspend fun taskIdsInList(uid: String, listId: String): List<String> =
        db.query("SELECT id FROM tasks WHERE ownerUid = ? AND listId = ?", arrayOf(uid, listId)).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    // ---- Helpers -------------------------------------------------------------------------------

    private fun TaskEntity.edited(): TaskEntity = copy(
        updatedAt = ConflictResolver.nextEditTime(clock.now(), updatedAt),
        dirty = true,
        localRevision = localRevision + 1,
    )

    private fun TaskListEntity.edited(): TaskListEntity = copy(
        updatedAt = ConflictResolver.nextEditTime(clock.now(), updatedAt),
        dirty = true,
        localRevision = localRevision + 1,
    )

    companion object {
        const val COMPLETED_LIMIT = 300

        fun nextOccurrenceId(taskId: String): String =
            UUID.nameUUIDFromBytes("$taskId:next".toByteArray()).toString()
    }
}
