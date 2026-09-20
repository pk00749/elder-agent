package com.elder.android.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSafetyTest {
    @Test
    fun `emergency keywords bypass model path`() {
        assertTrue(AgentSafety.isEmergency("我刚才摔了"))
        assertTrue(AgentSafety.isEmergency("胸口疼，喘不上气"))
    }

    @Test
    fun `money and medical questions are redirected locally`() {
        assertTrue(AgentSafety.isMoneyOrMedical("这个药吃多少"))
        assertTrue(AgentSafety.isMoneyOrMedical("要不要给他转账"))
    }

    @Test
    fun `dimension counting v0_6_0 only checks feeling fallback`() {
        // v0.6.0: dimensionCount 已 @Deprecated，维度判定改 LLM 显式 mark_dimension_covered 工具调用。
        // 该函数仅作 feeling 维度兜底（returns 0 or 1），不再 ≥2 触发收尾。
        assertEquals(0, AgentSafety.dimensionCount(listOf("今天和老张下棋")))
        assertEquals(0, AgentSafety.dimensionCount(listOf("嗯，然后呢")))
        assertEquals(1, AgentSafety.dimensionCount(listOf("今天挺高兴的")))
    }

    @Test
    fun `explicit close is recognized`() {
        assertTrue(AgentSafety.isExplicitClose("今天就到这儿"))
        assertTrue(AgentSafety.isExplicitClose("不聊了"))
    }
}
