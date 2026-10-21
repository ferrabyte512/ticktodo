package app.ticktodo.data.repository

/**
 * Side effects of a local change: refresh reminders, update the widget and
 * schedule a sync. Kept behind an interface so repository tests can observe
 * them without WorkManager or AlarmManager.
 */
interface TaskChangeEffects {
    fun tasksChanged(taskIds: Collection<String>)
    fun listsChanged()
}
