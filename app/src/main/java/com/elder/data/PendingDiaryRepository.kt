package com.elder.android.data

import android.content.Context
import com.elder.android.data.db.ElderDatabase
import com.elder.android.data.db.PendingDiaryDao
import com.elder.android.data.db.PendingDiaryEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class PendingDiaryRepository(private val dao: PendingDiaryDao) {
    fun observeAll(): Flow<List<PendingDiaryEntity>> = dao.observeAll()

    suspend fun all(): List<PendingDiaryEntity> = dao.all()

    suspend fun enqueue(
        date: String,
        audioPath: String,
        durationMs: Int,
        now: Long = System.currentTimeMillis(),
    ): PendingDiaryEntity {
        val item = PendingDiaryEntity(
            id = UUID.randomUUID().toString(),
            date = date,
            audioPath = audioPath,
            durationMs = durationMs,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(item)
        return item
    }

    suspend fun update(item: PendingDiaryEntity, error: String?) {
        dao.upsert(
            item.copy(
                attempts = item.attempts + 1,
                lastError = error,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun remove(id: String) = dao.delete(id)

    suspend fun clearAll() = dao.clearAll()

    companion object {
        fun get(context: Context): PendingDiaryRepository =
            PendingDiaryRepository(ElderDatabase.get(context).pendingDiaryDao())
    }
}
