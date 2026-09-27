// 对应 PRD §3.1.4 v0.9.1 行为测试：单段回复 ≤25 字（A2）+ 工具集 + C1 维度跟踪。
// v0.6.0/v0.7.0/v0.8.x/v0.9.0 的 ack/probe 双段拆段测试已删除（见 ChatAgent.MAX_REPLY_CHARS）。
// 旧 InterviewAgentTest 保留测 v0.5.0 行为；本文件专测 v0.6.0 / v0.9.1 行为。
package com.elder.android.agent

import com.elder.android.data.InterviewSession
import com.elder.android.data.InterviewTurn
import com.elder.android.data.llm.LlmClient
import com.elder.android.data.llm.LlmClientFactory
import com.elder.android.data.llm.LlmCredentials
import com.elder.android.data.llm.LlmMessage
import com.elder.android.data.llm.LlmResult
import com.elder.android.data.llm.LlmTool
import com.elder.android.data.llm.LlmToolCall
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterviewAgentTest_v2 {

    private val prompts = object : PromptProvider {
        override fun system(): String = "system"
        override fun save(): String = "save"
        // systemWithContext 使用 interface default impl（= system()）
    }

    /** 模拟 LLM 返回固定序列结果（v0.8.0 §A.15.5）。 */
    private class FakeLlmClient(private val responses: List<LlmResult>) : LlmClient {
        var calls = 0
        val recordedMessages = mutableListOf<List<LlmMessage>>()

        override suspend fun complete(
            apiKey: String,
            messages: List<LlmMessage>,
            tools: List<LlmTool>,
            onDelta: suspend (String) -> Unit,
        ): LlmResult {
            recordedMessages.add(messages.toList())
            val r = responses.getOrElse(calls) { responses.last() }
            calls += 1
            return r
        }
    }

    /**
     * v0.8.0 §A.15.5：FakeLlmClient 不能直接当 LlmClientFactory；包成 factory 让 InterviewAgent 通过
     * factory.current(credentials).complete(...) 拿到同一个 fake。
     */
    private class FakeLlmClientFactory(private val client: LlmClient) : LlmClientFactory() {
        override fun clientFor(provider: com.elder.android.data.db.LlmProvider): LlmClient = client
        
    }

    /** 简化构造：传入 FakeLlmClient 直接产出配套 factory。 */
    private fun factoryFor(fake: LlmClient): LlmClientFactory = FakeLlmClientFactory(fake)

    private fun session(vararg turns: Pair<String, String> = arrayOf()): InterviewSession {
        val t = turns.mapIndexed { i, (elder, assistant) ->
            InterviewTurn(
                turnNo = i + 1,
                elderText = elder,
                assistantText = assistant,
                createdAt = System.currentTimeMillis(),
            )
        }
        return InterviewSession(
            id = "session-1",
            status = com.elder.android.data.InterviewStatus.ACTIVE,
            turns = t,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun toolCall(name: String, args: String, id: String = "call-1"): LlmToolCall =
        LlmToolCall(id = id, name = name, arguments = args)

    // ===== v0.9.1 单段回复（替代 v0.6.0 ack/probe 拆段） =====

    @Test
    fun `v0_9_1 single segment reply preserved up to 25 chars`() = runTest {
        // LLM 输出 "嗯，挺高兴的，后来呢？" → 单段原样截断至 ≤25 字（A2）
        val fake = FakeLlmClient(listOf(LlmResult("嗯，挺高兴的，后来呢？")))
        val agent = InterviewAgent(factoryFor(fake), prompts)
        val result = agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), session(), "今天和老伴去公园了")
        assertTrue(result is AgentTurnResult.Reply)
        val reply = (result as AgentTurnResult.Reply).value
        // assistantText 单段，原样保留 ≤25 字；不再拆 ack/probe
        assertEquals("嗯，挺高兴的，后来呢？", reply.assistantText)
        assertTrue("assistantText length <= 25", reply.assistantText.length <= 25)
    }

    @Test
    fun `v0_9_1 total reply length capped at 25 chars per A2`() = runTest {
        // LLM 输出超长串 → 截断到 25 字（A2 单段回复上限）
        val fake = FakeLlmClient(listOf(LlmResult("这是非常非常非常非常非常非常非常非常非常非常长的一段话，但应该被截断")))
        val agent = InterviewAgent(factoryFor(fake), prompts)
        val result = agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), session(), "今天")
        val reply = (result as AgentTurnResult.Reply).value
        assertTrue("assistantText <= 25 chars (A2)", reply.assistantText.length <= 25)
    }

    // ===== F3 remember_fact 工具 =====

    @Test
    fun `remember_fact tool call writes to elder_facts`() = runTest {
        val fake = FakeLlmClient(
            listOf(
                LlmResult(
                    content = "",
                    toolCalls = listOf(
                        toolCall(
                            name = "remember_fact",
                            args = """{"type":"person","content":"儿子叫张伟","confidence":"high"}""",
                        ),
                    ),
                ),
                LlmResult(content = "好的，记住了"),
            ),
        )
        val fakeDao = FakeElderFactDao()
        val repo = com.elder.android.data.db.ElderFactRepository(fakeDao)
        val agent = InterviewAgent(factoryFor(fake), prompts, repo)
        agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), session(), "我儿子叫张伟")
        assertEquals(1, fakeDao.upserts.size)
        assertEquals("儿子叫张伟", fakeDao.upserts[0].content)
        assertEquals("person", fakeDao.upserts[0].type)
    }

    @Test
    fun `remember_fact unsupported type falls back to noop`() = runTest {
        val fake = FakeLlmClient(
            listOf(
                LlmResult(
                    content = "",
                    toolCalls = listOf(
                        toolCall(
                            name = "remember_fact",
                            args = """{"type":"unknown","content":"x","confidence":"high"}""",
                        ),
                    ),
                ),
                LlmResult(content = "嗯"),
            ),
        )
        val fakeDao = FakeElderFactDao()
        val repo = com.elder.android.data.db.ElderFactRepository(fakeDao)
        val agent = InterviewAgent(factoryFor(fake), prompts, repo)
        // 不应崩；upsert 应不被调用（type 校验失败抛 IllegalArgumentException，被 agent catch 静默）
        agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), session(), "随便说点什么")
        assertEquals(0, fakeDao.upserts.size)
    }

    // ===== C1 mark_dimension_covered 工具 =====

    @Test
    fun `mark_dimension_covered tracks 2 dimensions and triggers finalize when feeling included`() = runTest {
        val fake = FakeLlmClient(
            listOf(
                // turn 1: mark time
                LlmResult(
                    content = "",
                    toolCalls = listOf(toolCall(name = "mark_dimension_covered", args = """{"dim":"time"}""")),
                ),
                LlmResult(content = "然后呢？"),
                // turn 2: mark feeling → 满足 C1（≥2 项含 feeling）
                LlmResult(
                    content = "",
                    toolCalls = listOf(toolCall(name = "mark_dimension_covered", args = """{"dim":"feeling"}""")),
                ),
                // 后续 LLM 应触发 save_diary
                LlmResult(
                    content = "",
                    toolCalls = listOf(
                        toolCall(
                            name = "save_diary",
                            args = """{"text":"今天和老伴去公园了，挺高兴。","summary":"和老伴去公园"}""",
                            id = "call-save",
                        ),
                    ),
                ),
            ),
        )
        val agent = InterviewAgent(factoryFor(fake), prompts)
        val s = session()
        // turn 1
        val r1 = agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), s, "今天去了公园")
        assertTrue(r1 is AgentTurnResult.Reply)
        // turn 2
        val s2 = (r1 as AgentTurnResult.Reply).value.session
        val r2 = agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), s2, "挺高兴的")
        assertTrue("expected Finalize", r2 is AgentTurnResult.Finalize)
    }

    // ===== B1/B4 兜底（与 v0.5.0 一致） =====

    @Test
    fun `money keyword bypasses LLM and returns clarify reply`() = runTest {
        val fake = FakeLlmClient(listOf(LlmResult("不应被调用")))
        val agent = InterviewAgent(factoryFor(fake), prompts)
        val result = agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), session(), "这个药吃多少")
        assertTrue(result is AgentTurnResult.Reply)
        assertEquals(0, fake.calls)
        assertEquals("嗯，咱们聊点别的吧。", (result as AgentTurnResult.Reply).value.assistantText)
    }
}

/** 测试用 ElderFactDao stub：记录所有 upsert 调用，配合 ElderFactRepository 包装 */
private class FakeElderFactDao : com.elder.android.data.db.ElderFactDao {
    val upserts = mutableListOf<com.elder.android.data.db.ElderFactEntity>()

    override suspend fun searchByContent(query: String, typeFilter: String?, limit: Int): List<com.elder.android.data.db.ElderFactEntity> =
        upserts.filter { (typeFilter == null || it.type == typeFilter) && it.content.contains(query) }.take(limit)

    override suspend fun findAll(): List<com.elder.android.data.db.ElderFactEntity> = upserts.toList()
    override suspend fun findByType(type: String): List<com.elder.android.data.db.ElderFactEntity> = upserts.filter { it.type == type }
    override suspend fun count(): Int = upserts.size
    override suspend fun upsert(fact: com.elder.android.data.db.ElderFactEntity) { upserts.add(fact) }
    override suspend fun deleteById(id: String) { upserts.removeAll { it.id == id } }
    override suspend fun clear() { upserts.clear() }
}
