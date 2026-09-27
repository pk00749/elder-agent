// §A.13 / §A.11：TTS PCM sink 共用接口 + Android AudioTrack 实现
// 千问 / MiniMax TTS 客户端都通过此接口喂 PCM 字节；下沉到顶层文件便于跨 Provider 复用。
package com.elder.android.data.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/** PCM 字节写入接口（play / write / drain / release）。 */
interface PcmSink {
    fun play()
    fun write(bytes: ByteArray)
    fun drain()
    fun release()
}

/** 基于 [AudioTrack] 的 PCM sink；24kHz / mono / 16-bit。 */
class AndroidPcmSink private constructor(private val track: AudioTrack) : PcmSink {
    // 累计写入字节，用于 drain()/release() 等 playbackHeadPosition 追平再 stop()。
    private val bytesWritten: AtomicLong = AtomicLong(0)

    override fun play() {
        track.play()
    }

    override fun write(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val n = track.write(bytes, 0, bytes.size)
        if (n > 0) bytesWritten.addAndGet(n.toLong())
    }

    override fun drain() {
        drainBuffer(
            headSupplier = { track.playbackHeadPosition },
            totalSamples = (bytesWritten.get() / BYTES_PER_SAMPLE).toInt(),
        )
        runCatching { track.stop() }
    }

    override fun release() {
        drainBuffer(
            headSupplier = { track.playbackHeadPosition },
            totalSamples = (bytesWritten.get() / BYTES_PER_SAMPLE).toInt(),
        )
        runCatching { track.stop() }
        runCatching { track.release() }
    }

    companion object {
        private const val TAG = "PcmSink"
        private const val BYTES_PER_SAMPLE = 2  // PCM 16-bit mono
        internal const val MAX_DRAIN_MS = 1_500L  // v0.10.0 §2: 24kHz mono 25 字 TTS ≈ 3s 音频,1.5s 已足 + 兜底防 HAL 卡
        internal const val POLL_INTERVAL_MS = 10L

        /**
         * 等 AudioTrack 内部 buffer 排空（playbackHeadPosition 追平 bytesWritten / 2），
         * 让最后一帧 PCM 出 buffer 再返回。避免外层 [drain]/[release] 调 track.stop() 时
         * 砍掉未播放字节导致 TTS 尾音丢失（v0.x 已知 bug）。
         *
         * maxDrainMs 是兜底：HAL 卡顿时强制返回，让 stop()/release() 走正常收尾，
         * 不让单次 TTS hang 整个 IO 线程超过 5 秒。
         */
        internal fun drainBuffer(
            headSupplier: () -> Int,
            totalSamples: Int,
            sleep: (Long) -> Unit = { Thread.sleep(it) },
            maxDrainMs: Long = MAX_DRAIN_MS,
            pollIntervalMs: Long = POLL_INTERVAL_MS,
        ) {
            if (totalSamples <= 0) return
            val deadline = System.currentTimeMillis() + maxDrainMs
            while (headSupplier() < totalSamples) {
                if (System.currentTimeMillis() > deadline) return
                sleep(pollIntervalMs)
            }
        }

        fun create(sampleRate: Int = TTS_PCM_SAMPLE_RATE): AndroidPcmSink {
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuffer * 2, sampleRate))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            return AndroidPcmSink(track)
        }
    }
}

/** 默认采样率（与千问 / MiniMax TTS 一致）。 */
const val TTS_PCM_SAMPLE_RATE: Int = 24_000
