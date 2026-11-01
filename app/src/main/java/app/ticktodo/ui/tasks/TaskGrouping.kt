package app.ticktodo.ui.tasks

import app.ticktodo.domain.model.Task
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

    /** Gap left between neighbours when a task is moved to either end of "My order". */
    private const val ORDER_STEP = 60_000.0

    /**
     * Position in "My order", smallest first. Tasks never moved sort by creation
     * time, newest first, which matches how new tasks appear before any drag.
     */
    fun manualKey(task: Task): Double = task.sortOrder ?: -task.createdAt.toDouble()

    /**
     * New "My order" positions after [movedId] was dropped at its place in
     * [items] (the section in its new on-screen order). Usually only the moved
     * task changes; if its neighbours are too close to fit between, the whole
     * section is renumbered.
     */
    fun manualOrderUpdates(items: List<TaskItem>, movedId: String): Map<String, Double> {
        val index = items.indexOfFirst { it.task.id == movedId }
        if (index < 0 || items.size < 2) return emptyMap()
        val above = items.getOrNull(index - 1)?.let { manualKey(it.task) }
        val below = items.getOrNull(index + 1)?.let { manualKey(it.task) }
        val value = when {
            above != null && below != null -> if (below - above > 1e-3) (above + below) / 2 else null
            above != null -> above + ORDER_STEP
            below != null -> below - ORDER_STEP
            else -> null
        }
        if (value != null) return mapOf(movedId to value)
        val base = items.minOf { manualKey(it.task) }
        return items.mapIndexed { i, item -> item.task.id to base + i * ORDER_STEP }.toMap()
    }

    fun sort(items: List<TaskItem>, sort: TaskSort): List<TaskItem> = when (sort) {
        TaskSort.MANUAL -> items.sortedBy { manualKey(it.task) }
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
