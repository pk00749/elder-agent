// §3.1.7 今日记录图标 / §5.10 date 字段：设备本地时区的 YYYY-MM-DD
package com.elder.android.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object LocalDate {
    fun today(): String = format(Date().time, TimeZone.getDefault())

    fun format(epochMs: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = tz
        return fmt.format(Date(epochMs))
    }

    fun nowMs(): Long = System.currentTimeMillis()
}
