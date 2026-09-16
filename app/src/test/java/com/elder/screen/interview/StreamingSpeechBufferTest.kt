package com.elder.android.screen.interview

import com.elder.android.data.tts.TtsResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingSpeechBufferTest {
    @Test
    fun `starts tts at sentence boundary before llm completion`() = runTest {
        val spoken = mutableListOf<String>()
        val buffer = StreamingSpeechBuffer(
            apiKey = "key",
            enabled = true,
            speak = {
                spoken += it
                TtsResult(firstAudioDelayMs = 1, totalLatencyMs = 2)
            },
            onFailure = {},
        )

        buffer.onDelta("今天过得", allowStreaming = true)
        assertFalse(buffer.streamed)

        buffer.onDelta("挺好的？后面还有", allowStreaming = true)

        assertTrue(buffer.streamed)
        assertEquals(listOf("今天过得挺好的？"), spoken)
    }

    @Test
    fun `disabled tts never starts streaming`() = runTest {
        var called = false
        val buffer = StreamingSpeechBuffer(
            apiKey = "key",
            enabled = false,
            speak = {
                called = true
                TtsResult(1, 1)
            },
            onFailure = {},
        )

        buffer.onDelta("你好？", allowStreaming = true)

        assertFalse(buffer.streamed)
        assertFalse(called)
    }
}
