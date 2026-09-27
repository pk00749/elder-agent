// 对应 prd.md §3.1.4 C 收尾 + D1/D2 + §A.16；v0.9.0 SaveAgent 拆分
// v0.9.0 SaveAgent 走 save_v3.txt；产 text (≤100 字) + summary (≤60 字)
// PendingDiaryBackfill 仍通过 summarize() 兼容老路径
package com.elder.android.agent

import com.elder.android.data.InterviewSession
import com.elder.android.data.InterviewTurn
import com.elder.android.data.llm.LlmClientFactory
import com.elder.android.data.llm.LlmCredentials
import com.elder.android.data.llm.LlmMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * v0.9.0 SaveAgent — 收尾落库（saveDiary）+ 老 summarize() 兼容路径。
 *
 * ChatAgent 主循环检测 shouldFinalize=true 时，ViewModel 调本类 saveDiary()；
 * 不再通过工具调用（v0.9.0 工具集 6 → 3，save_diary 已移除）。
 *
 * save_v3.txt 同时支持普通 save_diary + background_learning 双模式，
 * 普通调用按 {text, summary} 解析。
 */
class SaveAgent(
    private val llmFactory: LlmClientFactory,
    private val prompts: SavePrompts,
    private val retryCount: Int = MAX_RETRIES,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 主循环收尾：基于整段访谈 transcript 走 save_v3 产 text + summary。
     * text 截断 ≤100 字 (D1);summary 截断 ≤60 字 (D2)。
     */
    suspend fun saveDiary(
        credentials: LlmCredentials,
        session: InterviewSession,
    ): SavedDiary {
        val transcript = session.turns
            .map(InterviewTurn::elderText)
            .filter(String::isNotBlank)
            .joinToString("\n") { "老人：$it" }
            .ifBlank { return SavedDiary(text = "", summary = "") }

        val result = completeWithRetry(
            credentials = credentials,
            messages = listOf(
                LlmMessage(role = "system", content = prompts.save()),
                LlmMessage(role = "user", content = transcript),
            ),
            tools = emptyList(),
        )
        val parsed = parseFinalJson(result.content)
        val text = parsed?.first?.take(MAX_TEXT_CHARS).orEmpty()
        val summary = parsed?.second?.take(MAX_SUMMARY_CHARS).orEmpty()
            .ifBlank { text.take(MAX_SUMMARY_CHARS) }
        return SavedDiary(text = text, summary = summary)
    }

    /**
     * v0.5.0 兼容接口：单次录音 → summarize 为 text + summary。
     * 走 save_v3.txt prompt（仍按 text/summary 解析，不走 background_learning 模式）。
     * v0.9.0 PendingDiaryBackfill 唯一调用方。
     */
    suspend fun summarize(
        credentials: LlmCredentials,
        transcript: String,
    ): Pair<String, String> {
        val result = completeWithRetry(
            credentials = credentials,
            messages = listOf(
                LlmMessage(role = "system", content = prompts.save()),
                LlmMessage(role = "user", content = transcript.take(MAX_TRANSCRIPT_CHARS)),
            ),
            tools = emptyList(),
        )
        val parsed = parseFinalJson(result.content)
        val text = parsed?.first?.take(MAX_TEXT_CHARS).orEmpty()
            .ifBlank { transcript.take(MAX_TRANSCRIPT_CHARS) }
        val summary = parsed?.second?.take(MAX_SUMMARY_CHARS).orEmpty()
            .ifBlank { text.take(MAX_SUMMARY_CHARS) }
        return text to summary
    }

    // ===== 内部 =====

    private fun parseFinalJson(raw: String): Pair<String, String>? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching {
            json.parseToJsonElement(raw.substring(start, end + 1)) as JsonObject
        }.getOrNull() ?: return null
        val text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val summary = obj["summary"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return text to summary
    }

    private suspend fun completeWithRetry(
        credentials: LlmCredentials,
        messages: List<LlmMessage>,
        tools: List<com.elder.android.data.llm.LlmTool>,
    ): com.elder.android.data.llm.LlmResult {
        val apiKey = credentials.apiKeyForCurrentProvider()
        var lastError: Throwable? = null
        repeat(retryCount) { attempt ->
            try {
                return llmFactory.current(credentials).complete(apiKey, messages, tools)
            } catch (e: com.elder.android.error.AppError.LlmAuthFailed) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                if (attempt < retryCount - 1) kotlinx.coroutines.delay(RETRY_DELAYS_MS[attempt])
            }
        }
        throw lastError ?: com.elder.android.error.AppError.LlmUpstream()
    }

    /** SaveAgent 主循环输出 */
    data class SavedDiary(val text: String, val summary: String)

    companion object {
        const val MAX_TEXT_CHARS = 100  // D1
        const val MAX_SUMMARY_CHARS = 60  // D2
        const val MAX_TRANSCRIPT_CHARS = 4_000
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS_MS = longArrayOf(500L, 1_000L)
    }
}
