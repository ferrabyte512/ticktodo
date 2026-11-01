package app.ticktodo.domain.model

import app.ticktodo.domain.focus.FocusPlan
import java.time.LocalDate
import java.time.LocalTime

enum class Priority(val level: Int) {
    NONE(0), LOW(1), MEDIUM(2), HIGH(3);

    companion object {
        fun fromLevel(level: Int): Priority = entries.firstOrNull { it.level == level } ?: NONE
    }
}

data class Task(
    val id: String,
    val listId: String?,
    val parentId: String?,
    val title: String,
    val notes: String,
    val priority: Priority,
    val dueDate: LocalDate?,
    val dueTime: LocalTime?,
    /** Minutes before the due date/time to remind. Null means no reminder. */
    val reminderOffsetMinutes: Int?,
    val recurrence: Recurrence?,
    val tags: List<String>,
    /** Null when the task does not use a focus timer. */
    val focusPlan: FocusPlan? = null,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val pendingSync: Boolean,
    /** Position in "My order"; see TaskGrouping.manualKey. */
    val sortOrder: Double? = null,
)

/** A task plus the derived information list rows need. */
data class TaskItem(
    val task: Task,
    val subtaskTotal: Int,
    val subtaskDone: Int,
)

data class TaskList(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Input for creating a task; everything except the title is optional. */
data class TaskDraft(
    val title: String,
    val notes: String = "",
    val listId: String? = null,
    val parentId: String? = null,
    val priority: Priority = Priority.NONE,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val reminderOffsetMinutes: Int? = null,
    val recurrence: Recurrence? = null,
    val tags: List<String> = emptyList(),
    val focusPlan: FocusPlan? = null,
)

/** The set of tasks a screen shows. */
sealed interface TaskScope {
    data object Today : TaskScope
    data object Upcoming : TaskScope
    data object Inbox : TaskScope
    data object All : TaskScope
    data object Completed : TaskScope
    data class InList(val listId: String) : TaskScope
    data class WithTag(val tag: String) : TaskScope

    /** Stable string form used in navigation arguments. */
    fun key(): String = when (this) {
        Today -> "today"
        Upcoming -> "upcoming"
        Inbox -> "inbox"
        All -> "all"
        Completed -> "completed"
        is InList -> "list:$listId"
        is WithTag -> "tag:$tag"
    }

    companion object {
        fun fromKey(key: String): TaskScope = when {
            key == "today" -> Today
            key == "upcoming" -> Upcoming
            key == "inbox" -> Inbox
            key == "all" -> All
            key == "completed" -> Completed
            key.startsWith("list:") -> InList(key.removePrefix("list:"))
            key.startsWith("tag:") -> WithTag(key.removePrefix("tag:"))
            else -> Today
        }
    }
}

enum class TaskSort { MANUAL, DUE_DATE, PRIORITY, CREATED, TITLE }
