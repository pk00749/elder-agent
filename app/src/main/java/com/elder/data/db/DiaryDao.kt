// §5.10 diary_entry_local DAO
package com.elder.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiaryEntryEntity): Long

    @Update
    suspend fun update(entry: DiaryEntryEntity)

    @Query("UPDATE diary_entry_local SET text = :newText, source = 'ASR_EDITED', updated_at = :now WHERE id = :id")
    suspend fun updateText(id: Long, newText: String, now: Long)

    @Query("SELECT * FROM diary_entry_local WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entry_local WHERE date = :date AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeByDate(date: String): Flow<List<DiaryEntryEntity>>

    @Query("SELECT * FROM diary_entry_local WHERE date BETWEEN :startDate AND :endDate AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeByDateRange(startDate: String, endDate: String): Flow<List<DiaryEntryEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM diary_entry_local WHERE date = :date AND deleted_at IS NULL)")
    suspend fun hasAnyOnDate(date: String): Boolean

    @Query("SELECT audio_path FROM diary_entry_local WHERE id = :id")
    suspend fun audioPath(id: Long): String?

    @Query("UPDATE diary_entry_local SET deleted_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("DELETE FROM diary_entry_local")
    suspend fun clearAll()
}
