package app.ticktodo.data.remote

import app.ticktodo.data.local.TaskEntity
import app.ticktodo.data.local.TaskListEntity
import app.ticktodo.data.local.TagCodec
import app.ticktodo.domain.TaskValidator
import app.ticktodo.domain.focus.FocusPlan

/**
 * Converts between Room rows and the document shape stored in Firestore.
 * The shape is mirrored by the validation functions in firestore.rules;
 * change both together and bump SCHEMA_VERSION.
 *
 * Incoming documents are treated as untrusted: values are type-checked,
 * clamped and re-validated, and a malformed document is skipped rather than
 * crashing sync.
 */
object RemoteMapper {
    const val SCHEMA_VERSION = 1L

    fun taskToMap(t: TaskEntity): Map<String, Any?> = mapOf(
        "id" to t.id,
        "schema" to SCHEMA_VERSION,
        "listId" to t.listId,
        "parentId" to t.parentId,
        "title" to t.title,
        "notes" to t.notes,
        "priority" to t.priority.toLong(),
        "dueEpochDay" to t.dueEpochDay,
        "dueMinuteOfDay" to t.dueMinuteOfDay?.toLong(),
        "reminderOffsetMinutes" to t.reminderOffsetMinutes?.toLong(),
        "recurrence" to t.recurrenceRule,
        "tags" to TagCodec.decode(t.tags),
        "focusPlan" to t.focusPlan,
        "completed" to t.completed,
        "completedAt" to t.completedAt,
        "createdAt" to t.createdAt,
        "updatedAt" to t.updatedAt,
        "deleted" to t.deleted,
    ) + (if (t.sortOrder != null) mapOf("sortOrder" to t.sortOrder) else emptyMap())

    fun listToMap(l: TaskListEntity): Map<String, Any?> = mapOf(
        "id" to l.id,
        "schema" to SCHEMA_VERSION,
        "name" to l.name,
        "colorIndex" to l.colorIndex.toLong(),
        "createdAt" to l.createdAt,
        "updatedAt" to l.updatedAt,
        "deleted" to l.deleted,
    )

    /** Returns null if the document is malformed. Local-only fields are taken from [existing]. */
    fun taskFromRemote(doc: RemoteStore.RemoteDoc, uid: String, existing: TaskEntity?): TaskEntity? {
        val d = doc.data
        val title = (d["title"] as? String)?.take(TaskValidator.MAX_TITLE) ?: return null
        val updatedAt = d.long("updatedAt") ?: return null
        val tags = (d["tags"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        return TaskEntity(
            id = doc.id,
            ownerUid = uid,
            listId = d["listId"] as? String,
            parentId = d["parentId"] as? String,
            title = title,
            notes = (d["notes"] as? String)?.take(TaskValidator.MAX_NOTES).orEmpty(),
            priority = (d.long("priority") ?: 0L).toInt().coerceIn(0, 3),
            dueEpochDay = d.long("dueEpochDay"),
            dueMinuteOfDay = d.long("dueMinuteOfDay")?.toInt()?.takeIf { it in 0 until 1_440 },
            reminderOffsetMinutes = d.long("reminderOffsetMinutes")?.toInt()?.takeIf { it in 0..10_080 },
            recurrenceRule = (d["recurrence"] as? String)?.take(100),
            tags = TagCodec.encode(TaskValidator.cleanTags(tags)),
            focusPlan = (d["focusPlan"] as? String)?.takeIf { FocusPlan.parse(it) != null },
            sortOrder = (d["sortOrder"] as? Number)?.toDouble()?.takeIf { it.isFinite() },
            completed = d["completed"] as? Boolean ?: false,
            completedAt = d.long("completedAt"),
            createdAt = d.long("createdAt") ?: updatedAt,
            updatedAt = updatedAt,
            deleted = d["deleted"] as? Boolean ?: false,
            dirty = false,
            localRevision = existing?.localRevision ?: 0,
            serverUpdatedAt = doc.serverUpdatedAt,
            snoozedUntil = existing?.snoozedUntil,
            lastNotifiedTrigger = existing?.lastNotifiedTrigger,
        )
    }

    fun listFromRemote(doc: RemoteStore.RemoteDoc, uid: String, existing: TaskListEntity?): TaskListEntity? {
        val d = doc.data
        val name = (d["name"] as? String)?.take(TaskValidator.MAX_LIST_NAME) ?: return null
        val updatedAt = d.long("updatedAt") ?: return null
        return TaskListEntity(
            id = doc.id,
            ownerUid = uid,
            name = name,
            colorIndex = (d.long("colorIndex") ?: 0L).toInt().coerceIn(0, 11),
            createdAt = d.long("createdAt") ?: updatedAt,
            updatedAt = updatedAt,
            deleted = d["deleted"] as? Boolean ?: false,
            dirty = false,
            localRevision = existing?.localRevision ?: 0,
            serverUpdatedAt = doc.serverUpdatedAt,
        )
    }

    fun updatedAtOf(doc: RemoteStore.RemoteDoc): Long? = doc.data.long("updatedAt")

    private fun Map<String, Any?>.long(key: String): Long? = when (val v = this[key]) {
        is Long -> v
        is Int -> v.toLong()
        is Double -> v.toLong()
        else -> null
    }
}
