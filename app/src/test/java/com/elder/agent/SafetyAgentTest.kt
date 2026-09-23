package com.elder.android.agent

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.9.0 SafetyAgent 测试：纯本地判定，**不调 LLM**。
 * 对应 prd.md §3.1.4 B1-B4 + §A.16。
 */
class SafetyAgentTest {

    @Test
    fun `emergency keywords return EMERGENCY verdict`() {
        listOf("我摔了", "喘不上气", "胸口疼", "叫救护车").forEach {
            assertEquals("\"$it\" 应判为 EMERGENCY", SafetyAgent.Verdict.EMERGENCY, SafetyAgent.check(it))
        }
    }

    @Test
    fun `medical keywords return MEDICAL verdict and priority over MONEY`() {
        // 医疗关键词优先于金钱（医疗更具体）
        assertEquals(SafetyAgent.Verdict.MEDICAL, SafetyAgent.check("这个药吃多少"))
        assertEquals(SafetyAgent.Verdict.MEDICAL, SafetyAgent.check("这是不是病"))
    }

    @Test
    fun `money keywords return MONEY verdict`() {
        // 纯金钱关键词（无医疗交叉）
        assertEquals(SafetyAgent.Verdict.MONEY, SafetyAgent.check("转账"))
        assertEquals(SafetyAgent.Verdict.MONEY, SafetyAgent.check("给我验证码"))
    }

    @Test
    fun `explicit close returns EXPLICIT_CLOSE verdict`() {
        listOf("就到这", "不聊了", "够了", "就这样吧").forEach {
            assertEquals("\"$it\" 应判为 EXPLICIT_CLOSE", SafetyAgent.Verdict.EXPLICIT_CLOSE, SafetyAgent.check(it))
        }
    }

    @Test
    fun `normal text returns SAFE verdict`() {
        listOf("今天和老张下棋", "挺好的", "然后呢").forEach {
            assertEquals("\"$it\" 应判为 SAFE", SafetyAgent.Verdict.SAFE, SafetyAgent.check(it))
        }
    }

    @Test
    fun `empty or blank text returns SAFE verdict (not EMERGENCY)`() {
        assertEquals(SafetyAgent.Verdict.SAFE, SafetyAgent.check(""))
        assertEquals(SafetyAgent.Verdict.SAFE, SafetyAgent.check("   "))
    }

    @Test
    fun `greetingFallback returns time-of-day specific Chinese`() {
        assertEquals("早上好，今天想聊什么？", SafetyAgent.greetingFallback(TimeOfDay.MORNING))
        assertEquals("中午好，今天过得怎样？", SafetyAgent.greetingFallback(TimeOfDay.NOON))
        assertEquals("晚上好，今天有什么想说的？", SafetyAgent.greetingFallback(TimeOfDay.EVENING))
    }
}
