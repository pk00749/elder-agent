// §5.10 diary_entry_local 仓储
// v0.10.0 §7:DiaryDetailViewModel 用 findById 加载单条日记
package com.elder.android.data

import android.content.Context
import com.elder.android.data.db.DiaryDao
import com.elder.android.data.db.DiaryEntryEntity
import com.elder.android.data.db.ElderDatabase
import kotlinx.coroutines.flow.Flow

class DiaryRepository(private val dao: DiaryDao) {
    fun observeAll(): Flow<List<DiaryEntryEntity>> = dao.observeAll()

    fun observeByDate(date: String): Flow<List<DiaryEntryEntity>> = dao.observeByDate(date)

    fun observeByDateRange(startDate: String, endDate: String): Flow<List<DiaryEntryEntity>> =
        dao.observeByDateRange(startDate, endDate)

    suspend fun insert(entry: DiaryEntryEntity): Long = dao.insert(entry)

    suspend fun updateText(id: Long, newText: String, now: Long) = dao.updateText(id, newText, now)

    suspend fun hasAnyOnDate(date: String): Boolean = dao.hasAnyOnDate(date)

    suspend fun audioPath(id: Long): String? = dao.audioPath(id)

    suspend fun findByPendingId(pendingId: String): DiaryEntryEntity? = dao.findByPendingId(pendingId)

    /** v0.10.0 §7: 日记详情页加载单条;不存在返回 null。 */
    suspend fun findById(id: Long): DiaryEntryEntity? = dao.findById(id)

    suspend fun clearAll() = dao.clearAll()

    companion object {
        fun get(context: Context): DiaryRepository =
            DiaryRepository(ElderDatabase.get(context).diaryDao())
    }
}
