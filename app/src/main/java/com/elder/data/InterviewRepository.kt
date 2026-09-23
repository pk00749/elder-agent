package com.elder.android.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.elder.android.data.db.ElderDatabase
import com.elder.android.data.db.InterviewSessionDao
import com.elder.android.data.db.InterviewSessionEntity
import java.util.UUID

/**
 * 单轮对话元素。v0.9.1 起取消 v0.6.0 ack + probe 双段字段；
 * assistantText 单段 ≤25 字（A2），Room 序列化时忽略老字段（ignoreUnknownKeys = true）。
 */
@Serializable
data class InterviewTurn(
    val turnNo: Int,
    val elderText: String,
    val assistantText: String,
    val audioPath: String? = null,
    val durationMs: Int = 0,
    val createdAt: Long,
)

enum class InterviewStatus {
    ACTIVE,
    REVIEWING,
    SAVED,
    ABANDONED,
}

@Serializable
data class InterviewSession(
    val id: String,
    val status: InterviewStatus,
    val turns: List<InterviewTurn> = emptyList(),
    val draftText: String? = null,
    val draftSummary: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

class InterviewRepository(
    private val dao: InterviewSessionDao,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun observeActive(): Flow<InterviewSession?> = dao.observeActive().map { it?.toModel() }

    suspend fun active(): InterviewSession? = dao.active()?.toModel()

    suspend fun create(now: Long = System.currentTimeMillis()): InterviewSession {
        val session = InterviewSession(
            id = UUID.randomUUID().toString(),
            status = InterviewStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
        save(session)
        return session
    }

    suspend fun save(session: InterviewSession) {
        dao.upsert(session.toEntity(json))
    }

    suspend fun clearAll() = dao.clearAll()

    private fun InterviewSessionEntity.toModel(): InterviewSession =
        InterviewSession(
            id = id,
            status = runCatching { InterviewStatus.valueOf(status) }.getOrDefault(InterviewStatus.ABANDONED),
            turns = runCatching { json.decodeFromString<List<InterviewTurn>>(turnsJson) }.getOrDefault(emptyList()),
            draftText = draftText,
            draftSummary = draftSummary,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun InterviewSession.toEntity(json: Json): InterviewSessionEntity =
        InterviewSessionEntity(
            id = id,
            status = status.name,
            turnsJson = json.encodeToString(turns),
            draftText = draftText,
            draftSummary = draftSummary,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        fun get(context: android.content.Context): InterviewRepository =
            InterviewRepository(ElderDatabase.get(context).interviewSessionDao())
    }
}
