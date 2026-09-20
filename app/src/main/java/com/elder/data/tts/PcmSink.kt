// §A.13 / §A.11：TTS PCM sink 共用接口 + Android AudioTrack 实现
// 千问 / MiniMax TTS 客户端都通过此接口喂 PCM 字节；下沉到顶层文件便于跨 Provider 复用。
package com.elder.android.data.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** PCM 字节写入接口（play / write / drain / release）。 */
interface PcmSink {
    fun play()
    fun write(bytes: ByteArray)
    fun drain()
    fun release()
}

/** 基于 [AudioTrack] 的 PCM sink；24kHz / mono / 16-bit。 */
class AndroidPcmSink private constructor(private val track: AudioTrack) : PcmSink {
    override fun play() {
        track.play()
    }

    override fun write(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        track.write(bytes, 0, bytes.size)
    }

    override fun drain() {
        runCatching { track.stop() }
    }

    override fun release() {
        runCatching { track.stop() }
        runCatching { track.release() }
    }

    companion object {
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
