package app.ticktodo.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Every row carries [ownerUid] and every query filters on it, so data from a
 * previous account can never be shown to the signed-in user even if a wipe
 * was interrupted.
 *
 * Sync bookkeeping (local only, never uploaded):
 *  - [dirty]: the row has local changes the server has not confirmed.
 *  - [localRevision]: bumped on every local edit; a push only clears [dirty]
 *    if the revision is unchanged, so edits made during a push are not lost.
 *  - [serverUpdatedAt]: server commit time of the last version we received.
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["ownerUid", "deleted", "completed", "dueEpochDay"]),
        Index(value = ["ownerUid", "listId"]),
        Index(value = ["ownerUid", "parentId"]),
        Index(value = ["ownerUid", "dirty"]),
    ],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val listId: String?,
    val parentId: String?,
    val title: String,
    val notes: String,
    val priority: Int,
    val dueEpochDay: Long?,
    val dueMinuteOfDay: Int?,
    val reminderOffsetMinutes: Int?,
    val recurrenceRule: String?,
    /** Tags stored as ",a,b," so a single LIKE matches whole tags. Empty string when none. */
    val tags: String,
    val completed: Boolean,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
    val dirty: Boolean,
    val localRevision: Long,
    val serverUpdatedAt: Long?,
    /** Encoded FocusPlan; null when the task does not use a focus timer. Synced. */
    val focusPlan: String? = null,
    /** Local only: snoozed reminder time. */
    val snoozedUntil: Long? = null,
    /** Local only: trigger time of the last reminder shown, to avoid repeats. */
    val lastNotifiedTrigger: Long? = null,
)

@Entity(
    tableName = "task_lists",
    indices = [Index(value = ["ownerUid", "deleted"]), Index(value = ["ownerUid", "dirty"])],
)
data class TaskListEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val name: String,
    val colorIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
    val dirty: Boolean,
    val localRevision: Long,
    val serverUpdatedAt: Long?,
)

/** One finished (or stopped) focus period. Local only: statistics for this device. */
@Entity(
    tableName = "focus_sessions",
    indices = [Index(value = ["ownerUid", "taskId"]), Index(value = ["ownerUid", "endedAt"])],
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUid: String,
    val taskId: String,
    val startedAt: Long,
    val endedAt: Long,
    val focusMs: Long,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val ownerUid: String,
    val tasksCursor: Long = 0,
    val listsCursor: Long = 0,
    val lastSuccessAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val lastErrorCode: String? = null,
    val lastRemotePurgeAt: Long? = null,
)

data class TaskWithCounts(
    @Embedded val task: TaskEntity,
    val subtaskTotal: Int,
    val subtaskDone: Int,
)

object TagCodec {
    fun encode(tags: List<String>): String = if (tags.isEmpty()) "" else tags.joinToString(",", prefix = ",", postfix = ",")
    fun decode(value: String): List<String> = value.split(',').filter { it.isNotBlank() }
}
