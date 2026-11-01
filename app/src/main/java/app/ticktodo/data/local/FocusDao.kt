package app.ticktodo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {
    @Insert
    suspend fun insert(session: FocusSessionEntity)

    @Query("SELECT COALESCE(SUM(focusMs), 0) FROM focus_sessions WHERE ownerUid = :uid AND taskId = :taskId")
    fun observeTaskTotal(uid: String, taskId: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(focusMs), 0) FROM focus_sessions WHERE ownerUid = :uid AND endedAt >= :since")
    fun observeTotalSince(uid: String, since: Long): Flow<Long>

    @Query("DELETE FROM focus_sessions WHERE ownerUid != :uid")
    suspend fun deleteOtherOwners(uid: String): Int
}
