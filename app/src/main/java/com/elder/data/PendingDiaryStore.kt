// 未绑定前暂存日记（§3.2.9 §F.5 / §H.24）—— MVP 用 DataStore + JSON 序列化
// §11.24：本地 Room 暂暂存；PR 4 简化用 DataStore + JSON（结构相同）
package com.elder.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.pendingDiaryStore by preferencesDataStore(name = "elder_pending_diary")

data class PendingDiaryRecord(
    val pendingId: String = UUID.randomUUID().toString(),
    val audioCosKey: String,
    val turns: List<Turn>,
    val text: String,
    val summary: String,
) {
    data class Turn(
        val elderText: String,
        val elderAudioCosKey: String,
    )
}

class PendingDiaryStore(private val context: Context) {
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(
        List::class.java,
        PendingDiaryRecord::class.java,
    )
    private val jsonAdapter: JsonAdapter<List<PendingDiaryRecord>> = moshi.adapter(listType)
    private val key = stringPreferencesKey("pending_diaries_json")

    suspend fun current(): List<PendingDiaryRecord> {
        val raw = context.pendingDiaryStore.data.first()[key] ?: return emptyList()
        return runCatching { jsonAdapter.fromJson(raw) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    suspend fun add(record: PendingDiaryRecord) {
        val current = current()
        val next = current + record
        context.pendingDiaryStore.edit { it[key] = jsonAdapter.toJson(next) }
    }

    suspend fun clear() {
        context.pendingDiaryStore.edit { it.remove(key) }
    }

    suspend fun remove(pendingId: String) {
        val next = current().filterNot { it.pendingId == pendingId }
        context.pendingDiaryStore.edit { it[key] = jsonAdapter.toJson(next) }
    }
}
