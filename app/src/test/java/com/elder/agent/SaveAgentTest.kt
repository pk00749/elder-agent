package com.elder.android.agent

import com.elder.android.data.db.LlmProvider
import com.elder.android.data.llm.LlmClient
import com.elder.android.data.llm.LlmClientFactory
import com.elder.android.data.llm.LlmCredentials
import com.elder.android.data.llm.LlmMessage
import com.elder.android.data.llm.LlmResult
import com.elder.android.data.llm.LlmTool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.9.0 SaveAgent 测试：saveDiary 收尾 + summarize 兼容路径。
 * 对应 prd.md §3.1.4 C / D1 / D2 + §A.16。
 */
class SaveAgentTest {

    private val savePrompts = object : SavePrompts {
        override fun save(): String = "save-v3"
        override fun saveForBackgroundLearning(): String = "save-v3"
    }

    @Test
    fun `saveDiary parses text and summary from JSON response`() = runTest {
        val fake = FakeLlmClient(responses = listOf(
            LlmResult(content = """{"text":"今天和老张下咗棋，赢咗","summary":"同老张下棋，赢咗"}"""),
        ))
        val agent = SaveAgent(llmFactory = factoryFor(fake), prompts = savePrompts)
        val session = sessionWithTurns(listOf("今天和老张下咗棋", "赢咗一局"))

        val result = agent.saveDiary(credentials(), session)

        assertEquals("今天和老张下咗棋，赢咗", result.text)
        assertEquals("同老张下棋，赢咗", result.summary)
    }

    @Test
    fun `saveDiary truncates text to MAX_TEXT_CHARS (100)`() = runTest {
        val longText = "很".repeat(120)
        val fake = FakeLlmClient(responses = listOf(
            LlmResult(content = """{"text":"$longText","summary":"$longText"}"""),
        ))
        val agent = SaveAgent(llmFactory = factoryFor(fake), prompts = savePrompts)
        val session = sessionWithTurns(listOf("turn 1"))

        val result = agent.saveDiary(credentials(), session)

        assertEquals(SaveAgent.MAX_TEXT_CHARS, result.text.length)
    }

    @Test
    fun `saveDiary truncates summary to MAX_SUMMARY_CHARS (60)`() = runTest {
        val longSummary = "好".repeat(80)
        val fake = FakeLlmClient(responses = listOf(
            LlmResult(content = """{"text":"ok","summary":"$longSummary"}"""),
        ))
        val agent = SaveAgent(llmFactory = factoryFor(fake), prompts = savePrompts)
        val session = sessionWithTurns(listOf("turn 1"))

        val result = agent.saveDiary(credentials(), session)

        assertEquals(SaveAgent.MAX_SUMMARY_CHARS, result.summary.length)
    }

    @Test
    fun `saveDiary falls back to text when summary is blank`() = runTest {
        val fake = FakeLlmClient(responses = listOf(
            LlmResult(content = """{"text":"今天和老张下棋","summary":""}"""),
        ))
        val agent = SaveAgent(llmFactory = factoryFor(fake), prompts = savePrompts)
        val session = sessionWithTurns(listOf("今天和老张下棋"))

        val result = agent.saveDiary(credentials(), session)

        // 空 summary 时回退到 text 前 60 字
        assertTrue(result.summary.isNotBlank())
        assertEquals(result.text.take(SaveAgent.MAX_SUMMARY_CHARS), result.summary)
    }

    @Test
    fun `summarize compat path parses text and summary`() = runTest {
        val fake = FakeLlmClient(responses = listOf(
            LlmResult(content = """{"text":"和老张下棋","summary":"同老张下棋"}"""),
        ))
        val agent = SaveAgent(llmFactory = factoryFor(fake), prompts = savePrompts)

        val (text, summary) = agent.summarize(credentials(), "今天和老张下棋赢了一局")

        assertEquals("和老张下棋", text)
        assertEquals("同老张下棋", summary)
    }

    @Test
    fun `saveDiary with empty turns returns empty SavedDiary without calling LLM`() = runTest {
        val fake = FakeLlmClient(responses = emptyList())
        val agent = SaveAgent(llmFactory = factoryFor(fake), prompts = savePrompts)
        val emptySession = com.elder.android.data.InterviewSession(
            id = "s",
            status = com.elder.android.data.InterviewStatus.ACTIVE,
            createdAt = 0L,
            updatedAt = 0L,
            turns = emptyList(),
        )

        val result = agent.saveDiary(credentials(), emptySession)

        assertEquals("", result.text)
        assertEquals("", result.summary)
        assertEquals("空 session 不应浪费 LLM 调用", 0, fake.calls)
    }

    // ===== 工具 =====

    private fun credentials() = LlmCredentials(provider = LlmProvider.MINIMAX, minimaxApiKey = "key")

    private fun sessionWithTurns(texts: List<String>) = com.elder.android.data.InterviewSession(
        id = "s",
        status = com.elder.android.data.InterviewStatus.ACTIVE,
        createdAt = 0L,
        updatedAt = 0L,
        turns = texts.mapIndexed { i, t ->
            com.elder.android.data.InterviewTurn(
                turnNo = i + 1,
                elderText = t,
                assistantText = "",
                createdAt = i.toLong(),
            )
        },
    )

    private class FakeLlmClient(
        responses: List<LlmResult>,
    ) : LlmClient {
        private val responses: MutableList<LlmResult> = responses.toMutableList()
        var calls: Int = 0
            private set

        override suspend fun complete(
            apiKey: String,
            messages: List<LlmMessage>,
            tools: List<LlmTool>,
            onDelta: suspend (String) -> Unit,
        ): LlmResult {
            calls += 1
            return responses.removeAt(0)
        }
    }

    private class FakeLlmClientFactory(private val client: LlmClient) : LlmClientFactory() {
        override fun clientFor(provider: LlmProvider): LlmClient = client
    }

    private fun factoryFor(fake: LlmClient): LlmClientFactory = FakeLlmClientFactory(fake)
}
