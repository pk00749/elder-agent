package com.elder.android.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.9.0 SafetyAgent 测试：纯本地判定，**不调 LLM**。
 * 对应 prd.md §3.1.4 B1-B4 + §A.16。
 *
 * v0.10.0 §4: greetingFallback 改为粵語;新增 3 条断言 + 1 条锁定不再用普通话。
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

    // v0.10.0 §4: greetingFallback 改为粵語静态口語;每条必须包含 ≥1 个粵語词
    @Test
    fun `greetingFallback returns Cantonese greeting for MORNING`() {
        val s = SafetyAgent.greetingFallback(TimeOfDay.MORNING)
        assertEquals("早晨,今日想去边度?", s)
        // 含粵語词(早晨/食咗/点/边度/啦/嘅/咩/冇 等任一)
        assertTrue("MORNING greeting 必须包含粵語词; actual=$s", s.matches(Regex(".*[嘅嗰啲咗咩㗎喔啦冇早晨点边度呀].*")))
        // 不再含普通话短语
        assertFalse("MORNING greeting 不再含『今天想聊什么』普通话短语", s.contains("今天想聊什么"))
    }

    @Test
    fun `greetingFallback returns Cantonese greeting for NOON`() {
        val s = SafetyAgent.greetingFallback(TimeOfDay.NOON)
        assertEquals("中午,食咗饭未呀?", s)
        assertTrue("NOON greeting 必须包含粵語词; actual=$s", s.matches(Regex(".*[嘅嗰啲咗咩㗎喔啦冇早晨点边度呀].*")))
        assertFalse("NOON greeting 不再含『今天过得怎样』普通话短语", s.contains("今天过得怎样"))
    }

    @Test
    fun `greetingFallback returns Cantonese greeting for EVENING`() {
        val s = SafetyAgent.greetingFallback(TimeOfDay.EVENING)
        assertEquals("今晚,今日过得点呀?", s)
        assertTrue("EVENING greeting 必须包含粵語词; actual=$s", s.matches(Regex(".*[嘅嗰啲咗咩㗎喔啦冇早晨点边度呀].*")))
        assertFalse("EVENING greeting 不再含『今天有什么想说的』普通话短语", s.contains("今天有什么想说的"))
    }
}
