package com.elder.android.agent

import com.elder.android.data.InterviewSession
import com.elder.android.data.InterviewStatus
import com.elder.android.data.InterviewTurn
import com.elder.android.data.llm.LlmClient
import com.elder.android.data.llm.LlmClientFactory
import com.elder.android.data.llm.LlmCredentials
import com.elder.android.data.llm.LlmMessage
import com.elder.android.data.llm.LlmResult
import com.elder.android.data.llm.LlmTool
import com.elder.android.data.llm.LlmToolCall
import com.elder.android.error.AppError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterviewAgentTest {
    private val prompts = object : PromptProvider {
        override fun system(): String = "system"
        override fun save(): String = "save"
    }

    @Test
    fun `money or medical path does not call llm`() = runTest {
        val fake = FakeLlmClient(listOf(LlmResult("不应调用")))
        val agent = InterviewAgent(factoryFor(fake), prompts)
        val session = session()

        val result = agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), session, "这个药吃多少")

        assertTrue(result is AgentTurnResult.Reply)
        assertEquals(0, fake.calls)
        assertEquals("嗯，咱们聊点别的吧。", (result as AgentTurnResult.Reply).value.assistantText)
    }

    @Test
    fun `save_diary tool produces bounded review draft`() = runTest {
        val fake = FakeLlmClient(
            listOf(
                LlmResult(
                    content = "",
                    toolCalls = listOf(
                        LlmToolCall(
                            id = "call-1",
                            name = InterviewAgent.TOOL_SAVE_DIARY,
                            arguments = """{"text":"${"很".repeat(120)}","summary":"${"好".repeat(80)}"}""",
                        ),
                    ),
                ),
            ),
        )
        val agent = InterviewAgent(factoryFor(fake), prompts)

        val result = agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), session(), "今天和老张下棋了")

        assertTrue(result is AgentTurnResult.Finalize)
        val final = (result as AgentTurnResult.Finalize).value
        assertEquals(InterviewAgent.MAX_TEXT_CHARS, final.text.length)
        assertEquals(InterviewAgent.MAX_SUMMARY_CHARS, final.summary.length)
        assertEquals(InterviewStatus.REVIEWING, final.session.status)
    }

    @Test
    fun `dimension completion requests final json then reviews`() = runTest {
        // v0.6.0 C1 修订：维度判定改 LLM 显式 mark_dimension_covered 工具调用。
        // 测试期望：LLM 调 mark_dimension_covered(event) + mark_dimension_covered(feeling) 触发 save_diary。
        val fake = FakeLlmClient(
            listOf(
                LlmResult(
                    content = "",
                    toolCalls = listOf(
                        LlmToolCall(
                            id = "call-1",
                            name = InterviewAgent.TOOL_MARK_DIMENSION,
                            arguments = "{\"dim\":\"event\"}",
                        ),
                    ),
                ),
                LlmResult(
                    content = "",
                    toolCalls = listOf(
                        LlmToolCall(
                            id = "call-2",
                            name = InterviewAgent.TOOL_MARK_DIMENSION,
                            arguments = "{\"dim\":\"feeling\"}",
                        ),
                    ),
                ),
                LlmResult(
                    content = "",
                    toolCalls = listOf(
                        LlmToolCall(
                            id = "call-3",
                            name = InterviewAgent.TOOL_SAVE_DIARY,
                            arguments = "{\"text\":\"今天和老张下棋，赢了。\",\"summary\":\"和老张下棋赢了\"}",
                        ),
                    ),
                ),
            ),
        )
        val agent = InterviewAgent(factoryFor(fake), prompts)

        val result = agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), session(), "今天和老张下棋了")

        assertTrue("expected Finalize after dimensions covered", result is AgentTurnResult.Finalize)
        assertEquals(
            "今天和老张下棋，赢了。",
            (result as AgentTurnResult.Finalize).value.text,
        )
        assertEquals(3, fake.calls)
    }

    @Test
    fun `llm transient failures retry up to three times`() = runTest {
        val fake = object : LlmClient {
            var calls = 0
            override suspend fun complete(
                apiKey: String,
                messages: List<LlmMessage>,
                tools: List<LlmTool>,
                onDelta: suspend (String) -> Unit,
            ): LlmResult {
                calls += 1
                if (calls < 3) throw AppError.LlmUpstream()
                return LlmResult("嗯，然后呢？")
            }
        }
        val agent = InterviewAgent(factoryFor(fake), prompts)

        val result = agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), session(), "嗯")

        assertTrue(result is AgentTurnResult.Reply)
        assertEquals(3, fake.calls)
    }

    @Test
    fun `explicit close triggers deterministic finalization`() = runTest {
        val fake = FakeLlmClient(
            listOf(
                LlmResult("好，那今天先记到这里。"),
                LlmResult("""{"text":"今天在家休息。","summary":"在家休息"}"""),
            ),
        )
        val agent = InterviewAgent(factoryFor(fake), prompts)

        val result = agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), session(), "不聊了")

        assertTrue(result is AgentTurnResult.Finalize)
        assertEquals(InterviewStatus.REVIEWING, (result as AgentTurnResult.Finalize).value.session.status)
    }

    @Test
    fun `hard turn cap never creates a ninth turn`() = runTest {
        val full = session().copy(
            turns = (1..InterviewAgent.MAX_TURNS).map {
                InterviewTurn(
                    turnNo = it,
                    elderText = "第 $it 轮",
                    assistantText = "嗯",
                    createdAt = it.toLong(),
                )
            },
        )
        val fake = FakeLlmClient(
            listOf(LlmResult("""{"text":"八轮内容","summary":"八轮"}""")),
        )
        val agent = InterviewAgent(factoryFor(fake), prompts)

        val result = agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), full, "还想再说")

        assertTrue(result is AgentTurnResult.Finalize)
        assertEquals(InterviewAgent.MAX_TURNS, (result as AgentTurnResult.Finalize).value.session.turns.size)
    }

    private fun session() = InterviewSession(
        id = "session-1",
        status = InterviewStatus.ACTIVE,
        createdAt = 1L,
        updatedAt = 1L,
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

    /** v0.8.0 §A.15.5：FakeLlmClient 不能直接当 LlmClientFactory；包成 factory 让 InterviewAgent 通过
     * factory.current(credentials).complete(...) 拿到同一个 fake。 */
    private class FakeLlmClientFactory(private val client: LlmClient) : LlmClientFactory() {
        override fun clientFor(provider: com.elder.android.data.db.LlmProvider): LlmClient = client
    }

    /** 简化构造：传入 FakeLlmClient 直接产出配套 factory。 */
    private fun factoryFor(fake: LlmClient): LlmClientFactory = FakeLlmClientFactory(fake)
}
