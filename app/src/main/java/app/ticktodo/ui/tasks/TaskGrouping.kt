package app.ticktodo.ui.tasks

import app.ticktodo.domain.model.TaskItem
import app.ticktodo.domain.model.TaskScope
import app.ticktodo.domain.model.TaskSort
import java.time.LocalDate

sealed interface SectionHeader {
    data object Overdue : SectionHeader
    data object Today : SectionHeader
    data class Day(val date: LocalDate) : SectionHeader
}

data class TaskSection(val key: String, val header: SectionHeader?, val items: List<TaskItem>)

/** Pure sorting and grouping for task lists, unit tested in TaskGroupingTest. */
object TaskGrouping {

    fun sort(items: List<TaskItem>, sort: TaskSort): List<TaskItem> = when (sort) {
        TaskSort.DUE_DATE -> items.sortedWith(
            compareBy<TaskItem>(
                { it.task.dueDate == null },
                { it.task.dueDate },
                { it.task.dueTime == null },
                { it.task.dueTime },
            ).thenByDescending { it.task.priority.level }.thenBy { it.task.createdAt },
        )
        TaskSort.PRIORITY -> items.sortedWith(
            compareByDescending<TaskItem> { it.task.priority.level }
                .thenBy { it.task.dueDate == null }.thenBy { it.task.dueDate }.thenBy { it.task.createdAt },
        )
        TaskSort.CREATED -> items.sortedByDescending { it.task.createdAt }
        TaskSort.TITLE -> items.sortedBy { it.task.title.lowercase() }
    }

    fun group(scope: TaskScope, items: List<TaskItem>, today: LocalDate, sort: TaskSort): List<TaskSection> = when (scope) {
        TaskScope.Today -> {
            val (overdue, due) = items.partition { it.task.dueDate?.isBefore(today) == true }
            listOfNotNull(
                overdue.takeIf { it.isNotEmpty() }?.let { TaskSection("overdue", SectionHeader.Overdue, sort(it, sort)) },
                due.takeIf { it.isNotEmpty() }?.let { TaskSection("today", SectionHeader.Today, sort(it, sort)) },
            )
        }
        TaskScope.Upcoming -> items.groupBy { it.task.dueDate }
            .toSortedMap(nullsLast(naturalOrder()))
            .map { (date, group) ->
                TaskSection(date?.toString() ?: "none", date?.let { SectionHeader.Day(it) }, sort(group, sort))
            }
        // Completed keeps its completion order.
        TaskScope.Completed -> if (items.isEmpty()) emptyList() else listOf(TaskSection("all", null, items))
        else -> if (items.isEmpty()) emptyList() else listOf(TaskSection("all", null, sort(items, sort)))
    }
}
