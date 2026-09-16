package com.elder.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InterviewSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: InterviewSessionEntity)

    @Query("SELECT * FROM interview_session WHERE id = :id LIMIT 1")
    suspend fun get(id: String): InterviewSessionEntity?

    @Query("SELECT * FROM interview_session WHERE status IN ('ACTIVE', 'REVIEWING') ORDER BY updated_at DESC LIMIT 1")
    suspend fun active(): InterviewSessionEntity?

    @Query("SELECT * FROM interview_session WHERE status IN ('ACTIVE', 'REVIEWING') ORDER BY updated_at DESC LIMIT 1")
    fun observeActive(): Flow<InterviewSessionEntity?>

    @Query("DELETE FROM interview_session")
    suspend fun clearAll()
}
