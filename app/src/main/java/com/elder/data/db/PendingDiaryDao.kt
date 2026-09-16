package com.elder.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingDiaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PendingDiaryEntity)

    @Query("SELECT * FROM pending_diary ORDER BY created_at ASC")
    suspend fun all(): List<PendingDiaryEntity>

    @Query("SELECT * FROM pending_diary ORDER BY created_at ASC")
    fun observeAll(): Flow<List<PendingDiaryEntity>>

    @Query("DELETE FROM pending_diary WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pending_diary")
    suspend fun clearAll()
}
