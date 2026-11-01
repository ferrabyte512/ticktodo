package app.ticktodo.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, TaskListEntity::class, SyncStateEntity::class, FocusSessionEntity::class],
    version = 3,
    exportSchema = true,
    // v2: tasks.focusPlan column and the focus_sessions table. v3: tasks.sortOrder column.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskListDao(): TaskListDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun focusDao(): FocusDao

    companion object {
        const val NAME = "ticktodo.db"
    }
}
