// §A.8 / §3.1.9：真接 DashScope MaaS endpoint 的非 mock 集成测试。
// 与 AsrApiClientTest（MockWebServer）的区别：本类直接打 wss://llm-...maas.aliyuncs.com。
//
// 用法（API key 由调用方提供，不进 git、不进 CI）：
//
//   # 方式 1：Gradle system property（推荐，CI 友好）
//   ./gradlew :app:testDebugUnitTest \
//       -PDASHSCOPE_API_KEY=sk-xxx \
//       --tests "com.elder.android.data.asr.AsrApiClientLiveTest"
//
//   # 方式 2：环境变量
//   DASHSCOPE_API_KEY=sk-xxx ./gradlew :app:testDebugUnitTest \
//       --tests "com.elder.android.data.asr.AsrApiClientLiveTest"
//
//   # 没设 key：测试自动 skip，不影响 CI 默认跑
//
// 这个测试守住 Realtime 会话链路：session.update / append / delta / commit / completed。
//
// ⚠ 不要把任何真实 API key 写进文件、commit、或贴进聊天 —— 共享后必须 rotate。
package com.elder.android.data.asr

import com.elder.android.error.AppError
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.sin

class AsrApiClientLiveTest {

    private val apiKey: String? =
        System.getProperty("DASHSCOPE_API_KEY")
            ?: System.getenv("DASHSCOPE_API_KEY")

    @Test fun `transcribe reaches real DashScope MaaS without ASR_UPSTREAM`() = runBlocking {
        // 没设 key 就 skip；这是「非 mock」的测试，必须真能打网络才能跑
        assumeTrue(
            "DASHSCOPE_API_KEY not set (env or -PDASHSCOPE_API_KEY=...); skip live test",
            !apiKey.isNullOrBlank(),
        )
        val key = apiKey!!

        val explicitAudioPath = System.getenv("ASR_LIVE_AUDIO_FILE")
        val sample = explicitAudioPath?.let(::File) ?: makeSineWaveWav()
        try {
            println("LiveTest using WS_URL=" + AsrApiClient.WS_URL)
            println("LiveTest model=" + AsrApiClient.BAILIAN_MODEL)
            val client = AsrApiClient()
            val r = client.transcribe(apiKey = key, audioFile = sample)
            println(
                "LiveTest OK text='" + r.text + "' confidence=" + r.confidence +
                    " httpStatus=" + r.httpStatus,
            )
            assertNotNull("transcribe should return non-null result", r)
            if (explicitAudioPath != null) {
                assertTrue("explicit speech file should produce transcript", r.text.isNotBlank())

                val retryResult = client.transcribe(apiKey = key, audioFile = sample)
                assertEquals("retry should reuse the same audio and produce the same text", r.text, retryResult.text)
                println("LiveTest retry OK text='" + retryResult.text + "'")
            }
        } catch (e: AppError.AsrUpstream) {
            fail(
                buildString {
                    appendLine("LiveTest got AppError.AsrUpstream (对方服务器没响应).")
                    appendLine("  message=" + (e.message ?: "null"))
                    appendLine("  cause_type=" + (e.cause?.javaClass?.name ?: "null"))
                    appendLine("  cause_message=" + (e.cause?.message ?: "null"))
                    appendLine("  WS_URL=" + AsrApiClient.WS_URL)
                    appendLine("  model=" + AsrApiClient.BAILIAN_MODEL)
                    appendLine()
                    appendLine("  常见来源：WebSocket 断连、session.updated 超时、transcription.completed 超时")
                },
            )
        } catch (e: AppError.AsrAuthFailed) {
            fail("LiveTest got ASR_AUTH_FAILED — API key 无效或过期，请 rotate 一把新的")
        } catch (e: AppError.AsrEmptyTranscript) {
            if (explicitAudioPath == null) {
                println("LiveTest OK (AsrEmptyTranscript expected for sine wave)")
            } else {
                fail("explicit speech file unexpectedly produced an empty transcript")
            }
        } finally {
            if (explicitAudioPath == null) sample.delete()
        }
    }

    @Test fun `realtime session emits partial transcript before stop`() = runBlocking {
        assumeTrue(
            "DASHSCOPE_API_KEY not set; skip live streaming test",
            !apiKey.isNullOrBlank(),
        )
        val audioPath = System.getenv("ASR_LIVE_AUDIO_FILE")
        assumeTrue(
            "ASR_LIVE_AUDIO_FILE not set; skip live streaming test",
            !audioPath.isNullOrBlank(),
        )

        val partials = CopyOnWriteArrayList<String>()
        val client = AsrApiClient()
        val session = client.openSession(apiKey!!) { partials.add(it) }
        try {
            FileInputStream(File(audioPath!!)).use { input ->
                val buffer = ByteArray(3200)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    session.appendAudio(buffer.copyOf(read))
                    delay(100)
                }
            }
            assertTrue("expected transcription.delta before stop", partials.isNotEmpty())
            println("LiveTest partials=" + partials.takeLast(3))

            val result = session.finish()
            assertTrue("final transcript should not be blank", result.text.isNotBlank())
            println("LiveTest streaming final text='" + result.text + "'")
        } finally {
            session.close()
        }
    }

    /** 与 AsrConfigViewModel.createSineWaveSample 等价：5 秒 440Hz PCM WAV */
    private fun makeSineWaveWav(): File {
        val out = File.createTempFile("asr_live_", ".wav")
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
