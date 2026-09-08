// §3.1.7 今日记录图标 + §5.10 date 字段的设备本地时区逻辑
package com.elder.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LocalDateTest {
    @Test fun `format produces YYYY-MM-DD with system timezone by default`() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.SEPTEMBER, 6, 14, 30, 0)
        val s = LocalDate.format(cal.timeInMillis)
        assertTrue("expected 2026-09-06, got $s", s.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
        assertEquals("2026-09-06", s)
    }

    @Test fun `format respects explicit timezone offset`() {
        // 2026-09-06 23:30 UTC == 2026-09-07 07:30 +08:00
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2026, Calendar.SEPTEMBER, 6, 23, 30, 0)
        val shanghai = LocalDate.format(cal.timeInMillis, TimeZone.getTimeZone("Asia/Shanghai"))
        assertEquals("2026-09-07", shanghai)
    }

    @Test fun `today returns a YYYY-MM-DD string of system date`() {
        val t = LocalDate.today()
        assertTrue(t.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
        assertEquals(10, t.length)
    }

    @Test fun `nowMs is monotonic increasing`() {
        val a = LocalDate.nowMs()
        Thread.sleep(2)
        val b = LocalDate.nowMs()
        assertTrue("expected b > a", b > a)
    }

    @Test fun `ISO date strings sort lexicographically equal to chronological order`() {
        // §3.1.7 / §5.10 都依赖 ISO 日期格式可字典序比较
        val dates = listOf("2026-01-15", "2025-12-31", "2026-09-06").sorted()
        assertEquals(listOf("2025-12-31", "2026-01-15", "2026-09-06"), dates)
    }
}
