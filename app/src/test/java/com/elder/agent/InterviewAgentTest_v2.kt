// 对应 PRD §3.1.4 v0.6.0 行为测试：ack/probe 拆分 + 新工具 + C1 维度跟踪。
// 旧 InterviewAgentTest 保留测 v0.5.0 行为；本文件专测 v0.6.0 新行为。
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

    // ===== A6 ack/probe 拆分 =====

    @Test
    fun `A6 ack and probe split at first Chinese punctuation`() = runTest {
        // 输入 "嗯，挺高兴的，后来呢？" → 第一个标点在 index=1（"，"）
        // 按 splitAckProbe 算法：ack = "嗯，" (substring 0..1+1)，probe = "挺高兴的，后来呢？"
        val fake = FakeLlmClient(listOf(LlmResult("嗯，挺高兴的，后来呢？")))
        val agent = InterviewAgent(factoryFor(fake), prompts)
        val result = agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), session(), "今天和老伴去公园了")
        assertTrue(result is AgentTurnResult.Reply)
        val reply = (result as AgentTurnResult.Reply).value
        assertEquals("嗯，", reply.ackText)
        assertEquals("挺高兴的，后来呢？", reply.probeText)
        // assistantText = ack + probe（≤35）
        assertEquals("嗯，挺高兴的，后来呢？", reply.assistantText)
    }

    @Test
    fun `ack length capped at 10 chars`() = runTest {
        // 中文标点在第 18 字之后；回退到前 10 字作 ack
        val fake = FakeLlmClient(listOf(LlmResult("今天听你说起一段很长很长的往事，那一年发生了什么？")))
        val agent = InterviewAgent(factoryFor(fake), prompts)
        val result = agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), session(), "我想起一件事")
        assertTrue(result is AgentTurnResult.Reply)
        val reply = (result as AgentTurnResult.Reply).value
        assertTrue("ack length <= 10", reply.ackText.length <= 10)
        assertTrue("probe length <= 25", reply.probeText.length <= 25)
        assertTrue("total <= 35", reply.assistantText.length <= 35)
    }

    @Test
    fun `total reply length capped at 35 chars per A6`() = runTest {
        val fake = FakeLlmClient(listOf(LlmResult("这是非常非常非常非常非常非常非常非常非常非常长的一段话，但应该被截断")))
        val agent = InterviewAgent(factoryFor(fake), prompts)
        val result = agent.respond(LlmCredentials(provider = com.elder.android.data.db.LlmProvider.MINIMAX, minimaxApiKey = "key"), session(), "今天")
        val reply = (result as AgentTurnResult.Reply).value
        assertTrue("total <= 35 chars", reply.assistantText.length <= 35)
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
