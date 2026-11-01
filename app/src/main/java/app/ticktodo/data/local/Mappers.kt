package app.ticktodo.data.local

import app.ticktodo.domain.focus.FocusPlan
import app.ticktodo.domain.model.Priority
import app.ticktodo.domain.model.Recurrence
import app.ticktodo.domain.model.Task
import app.ticktodo.domain.model.TaskItem
import app.ticktodo.domain.model.TaskList
import java.time.LocalDate
import java.time.LocalTime

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    listId = listId,
    parentId = parentId,
    title = title,
    notes = notes,
    priority = Priority.fromLevel(priority),
    dueDate = dueEpochDay?.let(LocalDate::ofEpochDay),
    dueTime = dueMinuteOfDay?.let { LocalTime.of(it / 60, it % 60) },
    reminderOffsetMinutes = reminderOffsetMinutes,
    recurrence = Recurrence.parse(recurrenceRule),
    tags = TagCodec.decode(tags),
    focusPlan = FocusPlan.parse(focusPlan),
    sortOrder = sortOrder,
    isCompleted = completed,
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pendingSync = dirty,
)

fun TaskWithCounts.toDomain(): TaskItem = TaskItem(task.toDomain(), subtaskTotal, subtaskDone)

fun TaskListEntity.toDomain(): TaskList = TaskList(id, name, colorIndex, createdAt, updatedAt)

fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute
