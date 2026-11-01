package app.ticktodo.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskListDao {
    @Query("SELECT * FROM task_lists WHERE ownerUid = :uid AND deleted = 0 ORDER BY createdAt LIMIT 500")
    fun observeAll(uid: String): Flow<List<TaskListEntity>>

    @Query("SELECT * FROM task_lists WHERE ownerUid = :uid AND deleted = 0 ORDER BY createdAt LIMIT 500")
    suspend fun all(uid: String): List<TaskListEntity>

    @Query("SELECT * FROM task_lists WHERE ownerUid = :uid AND id = :id")
    fun observe(uid: String, id: String): Flow<TaskListEntity?>

    @Query("SELECT * FROM task_lists WHERE ownerUid = :uid AND id = :id")
    suspend fun get(uid: String, id: String): TaskListEntity?

    @Upsert
    suspend fun upsert(list: TaskListEntity)

    @Query("SELECT * FROM task_lists WHERE ownerUid = :uid AND dirty = 1 ORDER BY updatedAt LIMIT :limit")
    suspend fun dirty(uid: String, limit: Int): List<TaskListEntity>

    @Query("UPDATE task_lists SET dirty = 0 WHERE ownerUid = :uid AND id = :id AND localRevision = :revision")
    suspend fun markClean(uid: String, id: String, revision: Long): Int

    @Query("SELECT id FROM task_lists WHERE ownerUid = :uid AND dirty = 0")
    suspend fun cleanIds(uid: String): List<String>

    @Query("DELETE FROM task_lists WHERE ownerUid = :uid AND id IN (:ids) AND dirty = 0")
    suspend fun deleteCleanByIds(uid: String, ids: Collection<String>)

    @Query("DELETE FROM task_lists WHERE ownerUid = :uid AND deleted = 1 AND dirty = 0 AND updatedAt < :before")
    suspend fun purgeTombstones(uid: String, before: Long): Int

    @Query("DELETE FROM task_lists WHERE ownerUid != :uid")
    suspend fun deleteOtherOwners(uid: String): Int

    @Query("SELECT COUNT(*) FROM task_lists WHERE ownerUid = :uid AND dirty = 1")
    fun observeDirtyCount(uid: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM task_lists WHERE ownerUid = :uid AND dirty = 1")
    suspend fun dirtyCount(uid: String): Int
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE ownerUid = :uid")
    suspend fun get(uid: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE ownerUid = :uid")
    fun observe(uid: String): Flow<SyncStateEntity?>

    @Upsert
    suspend fun upsert(state: SyncStateEntity)
}
