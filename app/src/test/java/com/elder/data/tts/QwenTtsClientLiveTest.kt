package com.elder.android.data.tts

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class QwenTtsClientLiveTest {
    private val apiKey: String? =
        System.getProperty("DASHSCOPE_API_KEY")
            ?: System.getenv("DASHSCOPE_API_KEY")

    @Test
    fun `qwen realtime tts kiki returns pcm`() = runBlocking {
        assumeTrue("DASHSCOPE_API_KEY not set; skip live TTS", !apiKey.isNullOrBlank())
        val sink = RecordingSink()
        val events = mutableListOf<String>()
        val result = try {
            QwenTtsClient(
                sinkFactory = { sink },
                eventObserver = events::add,
            ).speak(
                apiKey = apiKey!!,
                text = "今天过得挺好的。",
            )
        } catch (error: Throwable) {
            throw AssertionError("TTS events=$events", error)
        }

        assertTrue("TTS should return PCM bytes", sink.byteCount > 0)
        assertTrue("TTS first audio delay should be reported", result.totalLatencyMs > 0)
    }

    private class RecordingSink : com.elder.android.data.tts.PcmSink {
        @Volatile var byteCount: Int = 0
            private set

        override fun play() = Unit

        override fun write(bytes: ByteArray) {
            byteCount += bytes.size
        }

        override fun drain() = Unit

        override fun release() = Unit
    }
}
