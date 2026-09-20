// §A.12 MiniMax ASR 真接上游的非 mock 集成测试（HTTP REST + multipart + SSE）。
//
// 用法：
//   ./gradlew :app:testDebugUnitTest \
//       -PMINIMAX_API_KEY=... \
//       --tests "com.elder.android.data.asr.MiniMaxAsrClientLiveTest"
//
// 没设 key：自动 skip，CI 友好。
// ASR_LIVE_AUDIO_FILE=<path.wav> 可指定外部 wav；否则用 sine wave 生成测试音。
package com.elder.android.data.asr

import com.elder.android.error.AppError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.PI
import kotlin.math.sin

class MiniMaxAsrClientLiveTest {
    private val apiKey: String? =
        System.getProperty("MINIMAX_API_KEY")
            ?: System.getenv("MINIMAX_API_KEY")

    @Test fun `minimax asr reaches upstream with real wav upload`() = runBlocking {
        assumeTrue(
            "MINIMAX_API_KEY not set (env or -PMINIMAX_API_KEY=...); skip live test",
            !apiKey.isNullOrBlank(),
        )
        val key = apiKey!!
        val explicitAudioPath = System.getenv("ASR_LIVE_AUDIO_FILE")
        val sample = explicitAudioPath?.let(::File) ?: makeSineWaveWav()
        try {
            println("MiniMaxLiveTest using REST_URL=" + MiniMaxAsrClient.REST_URL)
            val client = MiniMaxAsrClient()
            val r = client.transcribe(apiKey = key, audioFile = sample)
            println(
                "MiniMaxLiveTest OK text='" + r.text + "' httpStatus=" + r.httpStatus,
            )
            assertTrue(r.text.isNotBlank())
        } catch (e: AppError.AsrUpstream) {
            fail("MiniMaxLiveTest got AppError.AsrUpstream — 可能是 endpoint / multipart 字段名与 §A.12 占位符不一致: ${e.message}")
        } catch (e: AppError.AsrAuthFailed) {
            fail("MiniMaxLiveTest got ASR_AUTH_FAILED — API key 无效或过期")
        } catch (e: AppError.AsrEmptyTranscript) {
            println("MiniMaxLiveTest OK (AsrEmptyTranscript expected for sine wave)")
        } finally {
            if (explicitAudioPath == null) sample.delete()
        }
    }

    private fun makeSineWaveWav(): File {
        val out = File.createTempFile("minimax_asr_live_", ".wav")
        val sampleRate = 16000
        val durationSec = 5
        val samples = sampleRate * durationSec
        val pcm = ShortArray(samples)
        for (i in 0 until samples) {
            val angle = i.toDouble() / sampleRate * 2.0 * PI * 440.0
            pcm[i] = (sin(angle) * Short.MAX_VALUE * 0.3).toInt().toShort()
        }
        FileOutputStream(out).use { fos ->
            writeWavHeader(fos, samples, sampleRate, 1)
            for (s in pcm) {
                fos.write(s.toInt() and 0xff)
                fos.write((s.toInt() shr 8) and 0xff)
            }
        }
        return out
    }

    private fun writeWavHeader(out: OutputStream, pcmSize: Int, sampleRate: Int, channels: Int) {
        val byteRate = sampleRate * channels * 2
        val dataSize = pcmSize * 2
        val totalSize = 36 + dataSize
        out.write("RIFF".toByteArray())
        out.write(intToLe(totalSize))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intToLe(16))
        out.write(shortToLe(1))
        out.write(shortToLe(channels.toShort()))
        out.write(intToLe(sampleRate))
        out.write(intToLe(byteRate))
        out.write(shortToLe((channels * 2).toShort()))
        out.write(shortToLe(16))
        out.write("data".toByteArray())
        out.write(intToLe(dataSize))
    }

    private fun intToLe(v: Int): ByteArray = byteArrayOf(
        (v and 0xff).toByte(),
        ((v shr 8) and 0xff).toByte(),
        ((v shr 16) and 0xff).toByte(),
        ((v shr 24) and 0xff).toByte(),
    )

    private fun shortToLe(v: Short): ByteArray = byteArrayOf(
        (v.toInt() and 0xff).toByte(),
        ((v.toInt() shr 8) and 0xff).toByte(),
    )
}
