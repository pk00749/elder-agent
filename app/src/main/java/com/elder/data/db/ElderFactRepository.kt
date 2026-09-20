// 对应 PRD §3.1.4 F3 elder_facts Repository
// 把 Room Entity 与 Agent 层 DTO 隔开；remember_fact 走 Kotlin 校验 + 去重；search_memory 走 Room DAO。
package com.elder.android.data.db

import android.content.Context
import com.elder.android.agent.ElderFact
import java.util.UUID

class ElderFactRepository(private val dao: ElderFactDao) {

    /**
     * F3 remember_fact 服务端实现：
     * 1. 校验 type ∈ enum、confidence ∈ enum、content ≤ MAX_CONTENT_CHARS
     * 2. 查找 content 高度重叠的已有 fact（substring overlap > 0.6）
     * 3a. 命中 → 更新 mentionCount + lastUsedAt + updatedAt，返回 "已合并"
     * 3b. 不命中 → 新建 fact（UUID），返回 "已新增"
     * 4. 任何异常抛 AppError.ValidationError，由 §4 全局异常处理器映射
     */
    suspend fun rememberFact(
        type: String,
        content: String,
        confidence: String,
        sourceSessionId: String? = null,
    ): ElderFactPersistResult {
        require(type in ElderFactEntity.VALID_TYPES) {
            "type must be one of ${ElderFactEntity.VALID_TYPES}, got '$type'"
        }
        require(confidence in ElderFactEntity.VALID_CONFIDENCES) {
            "confidence must be one of ${ElderFactEntity.VALID_CONFIDENCES}, got '$confidence'"
        }
        require(content.length <= ElderFactEntity.MAX_CONTENT_CHARS) {
            "content length ${content.length} > ${ElderFactEntity.MAX_CONTENT_CHARS}"
        }
        require(content.isNotBlank()) { "content must not be blank" }

        val now = System.currentTimeMillis()
        val existing = findSimilar(type, content)
        return if (existing != null) {
            val updated = existing.copy(
                mentionCount = existing.mentionCount + 1,
                lastUsedAt = now,
                updatedAt = now,
            )
            dao.upsert(updated)
            ElderFactPersistResult(existing.id, isNew = false)
        } else {
            val newFact = ElderFactEntity(
                id = UUID.randomUUID().toString(),
                type = type,
                content = content,
                confidence = confidence,
                lastUsedAt = now,
                mentionCount = 1,
                sourceSessionId = sourceSessionId,
                createdAt = now,
                updatedAt = now,
            )
            dao.upsert(newFact)
            ElderFactPersistResult(newFact.id, isNew = true)
        }
    }

    /**
     * F3 search_memory 服务端实现：纯 LIKE 查询。
     * 返回 list 大小 = min(limit, 命中数)。不读 MD 文件、不做 embedding。
     */
    suspend fun searchMemory(
        query: String,
        typeFilter: String? = null,
        limit: Int = 5,
    ): List<ElderFact> {
        if (query.isBlank()) return emptyList()
        val rows = dao.searchByContent(query.trim(), typeFilter, limit)
        return rows.map { it.toAgentDto() }
    }

    /**
     * F4 prompt 注入：取全部 facts（应用层 caller 自己 take(50) 截断）。
     */
    suspend fun loadAllForInjection(): List<ElderFact> =
        dao.findAll().map { it.toAgentDto() }

    /**
     * 纯文本 substring overlap 去重（无 embedding）。
     * F3 remember_fact 调用：与同 type 的已有 fact 计算 overlap。
     * 算法：取较短 content 字符串，检查它是否是较长 content 的子串；或反之。
     * 阈值 = 0.6（重叠字符数 / 较短字符串长度 > 0.6）。
     */
    private suspend fun findSimilar(type: String, content: String): ElderFactEntity? {
        val sameType = dao.findByType(type)
        val candidate = content.trim()
        return sameType.firstOrNull { existing ->
            substringOverlap(existing.content.trim(), candidate) > 0.6
        }
    }

    private fun substringOverlap(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val (shorter, longer) = if (a.length <= b.length) a to b else b to a
        if (shorter.isEmpty()) return 0.0
        // 简化算法：shorter 在 longer 中连续出现的最长子串长度
        // 不要求严格子串（避免空格差异），而是滑动窗口最长公共子串
        val lcsLen = longestCommonSubstring(shorter, longer)
        return lcsLen.toDouble() / shorter.length
    }

    /**
     * 最长公共子串（DP，O(m*n) 时间 / O(min(m,n)) 空间）。
     * 用滚动数组优化空间：对 ≤100 字字符串够用。
     */
    private fun longestCommonSubstring(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        val (s, t) = if (a.length <= b.length) a to b else b to a
        var prev = IntArray(s.length + 1)
        var curr = IntArray(s.length + 1)
        var best = 0
        for (i in 1..t.length) {
            for (j in 1..s.length) {
                curr[j] = if (s[j - 1] == t[i - 1]) prev[j - 1] + 1 else 0
                if (curr[j] > best) best = curr[j]
            }
            val tmp = prev
            prev = curr
            curr = tmp
            curr.fill(0)
        }
        return best
    }

    /**
     * Entity → Agent DTO 映射。仅暴露 prompt 需要的字段，不暴露 source_session_id（PII 风险）。
     */
    private fun ElderFactEntity.toAgentDto(): ElderFact = ElderFact(
        id = id,
        type = type,
        confidence = confidence,
        content = content,
        mentionCount = mentionCount,
        lastUsedAt = lastUsedAt,
    )

    companion object {
        fun get(context: Context): ElderFactRepository =
            ElderFactRepository(ElderDatabase.get(context).elderFactDao())
    }
}

data class ElderFactPersistResult(val id: String, val isNew: Boolean)
