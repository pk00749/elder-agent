// 对应 PcmSink.kt drainBuffer()：v0.x 尾音截断 bug 修复回归测试。
// 用 headSupplier + sleep 注入式桩避免 Robolectric mock AudioTrack，
// 直接覆盖 drain 等待 / 立即返回 / 超时兜底三条路径。
package com.elder.android.data.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class AndroidPcmSinkDrainTest {

    @Test
    fun `drainBuffer returns immediately when nothing written`() {
        var sleepCount = 0
        val elapsed = measureTimeMillis {
            AndroidPcmSink.drainBuffer(
                headSupplier = { 0 },
                totalSamples = 0,
                sleep = { sleepCount++ },
            )
        }
        assertEquals("未写入时不应轮询 sleep", 0, sleepCount)
        assertTrue("未写入时不应阻塞（实测 ${elapsed}ms）", elapsed < 50)
    }

    @Test
    fun `drainBuffer waits until playback head catches up`() {
        // 模拟 HAL 每 10ms 消费 200 样本（24kHz 下相当于 24000 * 0.01 = 240/帧，向上取整简化）
        var head = 0
        val total = 1_000
        var sleepCount = 0
        AndroidPcmSink.drainBuffer(
            headSupplier = { head },
            totalSamples = total,
            sleep = {
                sleepCount++
                head += 200
            },
            maxDrainMs = 1_000,
            pollIntervalMs = 5,
        )
        // 1000 / 200 = 5 次 sleep 才能追上；放宽到 ≥ 4 容忍偶发调度抖动
        assertTrue("至少轮询 4 次才追上，实际 $sleepCount", sleepCount >= 4)
        assertEquals("head 应该追上 totalSamples", total, head)
    }

    @Test
    fun `drainBuffer times out when head never advances`() {
        // 模拟 HAL 卡住：headSupplier 永远返回 0；兜底超时强制返回
        var head = 0
        val total = 10_000
        var sleepCount = 0
        val elapsed = measureTimeMillis {
            AndroidPcmSink.drainBuffer(
                headSupplier = { head },
                totalSamples = total,
                sleep = { sleepCount++ },
                maxDrainMs = 100,
                pollIntervalMs = 5,
            )
        }
        // HAL 卡住时应在 maxDrainMs 附近返回，不应 hang 整个 IO 线程
        assertTrue("HAL 卡住时应在 maxDrainMs 内返回（实测 ${elapsed}ms）", elapsed < 300)
        assertTrue("应至少轮询一次才超时，实际 $sleepCount", sleepCount > 0)
    }

    @Test
    fun `drainBuffer handles head exceeding totalSamples`() {
        // 罕见但理论上可能出现：playbackHeadPosition 已超过 bytesWritten（HAL 抢占、空闲推进等）
        // 此时循环立刻退出，不应 hang
        var head = 2_000
        var sleepCount = 0
        AndroidPcmSink.drainBuffer(
            headSupplier = { head },
            totalSamples = 1_000,
            sleep = { sleepCount++ },
        )
        assertEquals("head > totalSamples 时不应轮询", 0, sleepCount)
    }
}
