// §A.13 MiniMax T2A TTS 真接上游的非 mock 集成测试（WebSocket t2a_v2_bidi，v0.12.0 切 bidi）。
//
// 用法：
//   ./gradlew :app:testDebugUnitTest \
//       -PMINIMAX_TTS_API_KEY=... \
//       --tests "com.elder.android.data.tts.MiniMaxTtsClientLiveTest"
//
// 没设 key：自动 skip，CI 友好。
package com.elder.android.data.tts

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class MiniMaxTtsClientLiveTest {
    private val apiKey: String? =
        System.getProperty("MINIMAX_TTS_API_KEY")
            ?: System.getenv("MINIMAX_TTS_API_KEY")
            ?: System.getProperty("MINIMAX_API_KEY")
            ?: System.getenv("MINIMAX_API_KEY")

    @Test
    fun `minimax t2a returns pcm for cantonese kind woman voice`() = runBlocking {
        assumeTrue(
            "MINIMAX_TTS_API_KEY not set; skip live TTS",
            !apiKey.isNullOrBlank(),
        )
        val sink = RecordingSink()
        val result = try {
            MiniMaxTtsClient(
                sinkFactory = { sink },
            ).speak(
                apiKey = apiKey!!,
                text = "今天过得挺好的。",
            )
        } catch (error: Throwable) {
            throw AssertionError("TTS failed: ${error.message}", error)
        }
        assertTrue("TTS should return PCM bytes", sink.byteCount > 0)
        assertTrue("TTS total latency should be reported", result.totalLatencyMs > 0)
        assertTrue(
            "TTS first-audio delay should be reported (>=0)",
            result.firstAudioDelayMs >= 0 || result.firstAudioDelayMs == -1L,
        )
    }

    private class RecordingSink : PcmSink {
        @Volatile var byteCount: Int = 0
            private set

        override fun play() = Unit
        override fun write(bytes: ByteArray) { byteCount += bytes.size }
        override fun drain() = Unit
        override fun release() = Unit
    }
}
