package app.ticktodo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val COUNTS = """
    SELECT t.*,
      (SELECT COUNT(*) FROM tasks s WHERE s.ownerUid = t.ownerUid AND s.parentId = t.id AND s.deleted = 0) AS subtaskTotal,
      (SELECT COUNT(*) FROM tasks s WHERE s.ownerUid = t.ownerUid AND s.parentId = t.id AND s.deleted = 0 AND s.completed = 1) AS subtaskDone
    FROM tasks t
"""

private const val ACTIVE_TOP = "t.ownerUid = :uid AND t.deleted = 0 AND t.parentId IS NULL"

/** Hard cap so a single list query can never load an unbounded number of rows. */
const val LIST_LIMIT = 1_000

@Dao
interface TaskDao {

    // ---- Screens -----------------------------------------------------------------------------

    /** Overdue and due today. */
    @Query("$COUNTS WHERE $ACTIVE_TOP AND t.completed = 0 AND t.dueEpochDay IS NOT NULL AND t.dueEpochDay <= :today ORDER BY t.dueEpochDay, t.dueMinuteOfDay IS NULL, t.dueMinuteOfDay, t.priority DESC LIMIT $LIST_LIMIT")
    fun observeToday(uid: String, today: Long): Flow<List<TaskWithCounts>>

    @Query("$COUNTS WHERE $ACTIVE_TOP AND t.completed = 0 AND t.dueEpochDay > :today ORDER BY t.dueEpochDay, t.dueMinuteOfDay IS NULL, t.dueMinuteOfDay LIMIT $LIST_LIMIT")
    fun observeUpcoming(uid: String, today: Long): Flow<List<TaskWithCounts>>

    @Query("$COUNTS WHERE $ACTIVE_TOP AND t.completed = 0 AND t.listId IS NULL ORDER BY t.createdAt DESC LIMIT $LIST_LIMIT")
    fun observeInbox(uid: String): Flow<List<TaskWithCounts>>

    @Query("$COUNTS WHERE $ACTIVE_TOP AND t.completed = 0 ORDER BY t.createdAt DESC LIMIT $LIST_LIMIT")
    fun observeAllActive(uid: String): Flow<List<TaskWithCounts>>

    @Query("$COUNTS WHERE $ACTIVE_TOP AND t.completed = 0 AND t.listId = :listId ORDER BY t.createdAt DESC LIMIT $LIST_LIMIT")
    fun observeInList(uid: String, listId: String): Flow<List<TaskWithCounts>>

    @Query("$COUNTS WHERE $ACTIVE_TOP AND t.completed = 0 AND t.tags LIKE '%,' || :tag || ',%' ORDER BY t.createdAt DESC LIMIT $LIST_LIMIT")
    fun observeWithTag(uid: String, tag: String): Flow<List<TaskWithCounts>>

    @Query("$COUNTS WHERE $ACTIVE_TOP AND t.completed = 1 ORDER BY t.completedAt DESC LIMIT :limit")
    fun observeCompleted(uid: String, limit: Int): Flow<List<TaskWithCounts>>

    /** [pattern] must already be escaped with TaskValidator.likePattern. */
    @Query("$COUNTS WHERE $ACTIVE_TOP AND (t.title LIKE :pattern ESCAPE '\\' OR t.notes LIKE :pattern ESCAPE '\\' OR t.tags LIKE :pattern ESCAPE '\\') ORDER BY t.completed, t.updatedAt DESC LIMIT 200")
    fun search(uid: String, pattern: String): Flow<List<TaskWithCounts>>

    @Query("SELECT * FROM tasks WHERE ownerUid = :uid AND id = :id AND deleted = 0")
    fun observeTask(uid: String, id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE ownerUid = :uid AND parentId = :parentId AND deleted = 0 ORDER BY createdAt")
    fun observeSubtasks(uid: String, parentId: String): Flow<List<TaskEntity>>

    @Query("SELECT tags FROM tasks WHERE ownerUid = :uid AND deleted = 0 AND tags != ''")
    fun observeTagStrings(uid: String): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM tasks WHERE ownerUid = :uid AND dirty = 1")
    fun observeDirtyCount(uid: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks t WHERE $ACTIVE_TOP AND t.completed = 0 AND t.listId = :listId")
    suspend fun countOpenInList(uid: String, listId: String): Int

    @Query("SELECT COUNT(*) FROM tasks t WHERE $ACTIVE_TOP AND t.completed = 0 AND t.dueEpochDay IS NOT NULL AND t.dueEpochDay <= :today")
    suspend fun countOpenDueBy(uid: String, today: Long): Int

    /** Top-level tasks completed within [from, to): the Today ring. */
    @Query("SELECT COUNT(*) FROM tasks t WHERE $ACTIVE_TOP AND t.completed = 1 AND t.completedAt >= :from AND t.completedAt < :to")
    fun observeCompletedBetween(uid: String, from: Long, to: Long): Flow<Int>

    /** Open-task counts for the Browse screen, in one query. */
    @Query(
        "SELECT COALESCE(SUM(t.listId IS NULL), 0) AS inbox, " +
            "COALESCE(SUM(t.dueEpochDay IS NOT NULL AND t.dueEpochDay <= :today), 0) AS today, " +
            "COALESCE(SUM(t.dueEpochDay > :today), 0) AS upcoming, COUNT(*) AS total " +
            "FROM tasks t WHERE $ACTIVE_TOP AND t.completed = 0",
    )
    fun observeScopeCounts(uid: String, today: Long): Flow<ScopeCounts>

    @Query("SELECT t.listId AS listId, COUNT(*) AS count FROM tasks t WHERE $ACTIVE_TOP AND t.completed = 0 AND t.listId IS NOT NULL GROUP BY t.listId")
    fun observeListCounts(uid: String): Flow<List<ListCount>>

    /** True while any open task sits in a list, i.e. while Inbox shows something different from All tasks. */
    @Query("SELECT EXISTS(SELECT 1 FROM tasks t WHERE $ACTIVE_TOP AND t.completed = 0 AND t.listId IS NOT NULL)")
    fun observeHasListedTasks(uid: String): Flow<Boolean>


    // ---- Widgets (live streams, so an open widget session always shows current data) ------

    @Query("SELECT * FROM tasks WHERE ownerUid = :uid AND deleted = 0 AND completed = 0 AND parentId IS NULL AND dueEpochDay IS NOT NULL AND dueEpochDay <= :today ORDER BY dueEpochDay, dueMinuteOfDay IS NULL, dueMinuteOfDay, priority DESC LIMIT :limit")
    fun widgetToday(uid: String, today: Long, limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE ownerUid = :uid AND deleted = 0 AND completed = 0 AND parentId IS NULL AND dueEpochDay > :today AND dueEpochDay <= :until ORDER BY dueEpochDay, dueMinuteOfDay IS NULL, dueMinuteOfDay, priority DESC LIMIT :limit")
    fun widgetUpcoming(uid: String, today: Long, until: Long, limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE ownerUid = :uid AND deleted = 0 AND completed = 0 AND parentId IS NULL AND listId IS NULL ORDER BY dueEpochDay IS NULL, dueEpochDay, priority DESC, createdAt DESC LIMIT :limit")
    fun widgetInbox(uid: String, limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE ownerUid = :uid AND deleted = 0 AND completed = 0 AND parentId IS NULL ORDER BY dueEpochDay IS NULL, dueEpochDay, priority DESC, createdAt DESC LIMIT :limit")
    fun widgetAll(uid: String, limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE ownerUid = :uid AND deleted = 0 AND completed = 0 AND parentId IS NULL AND listId = :listId ORDER BY dueEpochDay IS NULL, dueEpochDay, priority DESC, createdAt DESC LIMIT :limit")
    fun widgetInList(uid: String, listId: String, limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE ownerUid = :uid AND deleted = 0 AND completed = 0 AND parentId IS NULL AND tags LIKE '%,' || :tag || ',%' ORDER BY dueEpochDay IS NULL, dueEpochDay, priority DESC, createdAt DESC LIMIT :limit")
    fun widgetWithTag(uid: String, tag: String, limit: Int): Flow<List<TaskEntity>>

    // ---- Single rows -------------------------------------------------------------------------

    @Query("SELECT * FROM tasks WHERE ownerUid = :uid AND id = :id")
    suspend fun get(uid: String, id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE ownerUid = :uid AND id IN (:ids)")
    suspend fun getMany(uid: String, ids: Collection<String>): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE ownerUid = :uid AND parentId IN (:parentIds)")
    suspend fun getChildren(uid: String, parentIds: Collection<String>): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity)

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Upsert
    suspend fun upsertAll(tasks: List<TaskEntity>)

    // ---- Reminders ---------------------------------------------------------------------------

    @Query("SELECT * FROM tasks WHERE ownerUid = :uid AND deleted = 0 AND completed = 0 AND dueEpochDay IS NOT NULL AND (reminderOffsetMinutes IS NOT NULL OR snoozedUntil IS NOT NULL)")
    suspend fun withReminders(uid: String): List<TaskEntity>

    @Query("SELECT id FROM tasks WHERE ownerUid = :uid AND (reminderOffsetMinutes IS NOT NULL OR snoozedUntil IS NOT NULL)")
    suspend fun idsWithAnyReminder(uid: String): List<String>

    @Query("UPDATE tasks SET lastNotifiedTrigger = :trigger, snoozedUntil = NULL WHERE ownerUid = :uid AND id = :id")
    suspend fun markNotified(uid: String, id: String, trigger: Long)

    @Query("UPDATE tasks SET snoozedUntil = :until WHERE ownerUid = :uid AND id = :id")
    suspend fun setSnooze(uid: String, id: String, until: Long?)

    // ---- Sync --------------------------------------------------------------------------------

    @Query("SELECT * FROM tasks WHERE ownerUid = :uid AND dirty = 1 ORDER BY updatedAt LIMIT :limit")
    suspend fun dirty(uid: String, limit: Int): List<TaskEntity>

    @Query("UPDATE tasks SET dirty = 0 WHERE ownerUid = :uid AND id = :id AND localRevision = :revision")
    suspend fun markClean(uid: String, id: String, revision: Long): Int

    @Query("SELECT id FROM tasks WHERE ownerUid = :uid AND dirty = 0")
    suspend fun cleanIds(uid: String): List<String>

    @Query("DELETE FROM tasks WHERE ownerUid = :uid AND id IN (:ids) AND dirty = 0")
    suspend fun deleteCleanByIds(uid: String, ids: Collection<String>)

    /** Removes tombstones the server already has; they are not needed locally. */
    @Query("DELETE FROM tasks WHERE ownerUid = :uid AND deleted = 1 AND dirty = 0 AND updatedAt < :before")
    suspend fun purgeTombstones(uid: String, before: Long): Int

    @Query("DELETE FROM tasks WHERE ownerUid != :uid")
    suspend fun deleteOtherOwners(uid: String): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE ownerUid = :uid AND dirty = 1")
    suspend fun dirtyCount(uid: String): Int
}

data class ScopeCounts(val inbox: Int, val today: Int, val upcoming: Int, val total: Int)

data class ListCount(val listId: String, val count: Int)
