package app.ticktodo.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/** Current schema version; bump together with a new entry in app/schemas and an AutoMigration. */
const val DB_VERSION = 5

@Database(
    entities = [TaskEntity::class, TaskListEntity::class, SyncStateEntity::class, FocusSessionEntity::class],
    version = DB_VERSION,
    exportSchema = true,
    // v2: tasks.focusPlan column and the focus_sessions table. v3: tasks.sortOrder column.
    // v4: index on completion time. v5: exact sync cursors and last pull time.
    // Every step is additive, so Room generates it and keeps the data.
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5),
    ],
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
