// §3.1.2 §L.3 录音 → Realtime ASR。
// AudioRecord 输出 16kHz/16bit/mono PCM：一份写本地 WAV，一份按帧实时发送 ASR。
package com.elder.android.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecorder(private val context: Context) {
    private var recorder: AudioRecord? = null
    private var outputStream: FileOutputStream? = null
    private var currentFile: File? = null
    private var startedAtMs: Long = 0
    @Volatile private var dataSize: Int = 0
    private var readThread: Thread? = null
    private val recording = AtomicBoolean(false)
    @Volatile private var onAudioFrame: ((ByteArray) -> Unit)? = null

    fun start(frameListener: ((ByteArray) -> Unit)? = null): File {
        stop()
        onAudioFrame = frameListener
        val out = File(context.cacheDir, "elder_${System.currentTimeMillis()}.wav")
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, AUDIO_FORMAT_PCM)
        val bufSize = maxOf(minBuf, 4096)
        val ar = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNELS,
            AUDIO_FORMAT_PCM,
            bufSize,
        )
        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            ar.release()
            throw IllegalStateException("AudioRecord init failed (state=${ar.state})")
        }
        val fos = FileOutputStream(out)
        writeWavHeader(fos)
        ar.startRecording()
        recorder = ar
        outputStream = fos
        currentFile = out
        startedAtMs = System.currentTimeMillis()
        dataSize = 0
        recording.set(true)
        readThread = Thread({ readLoop(ar, bufSize) }, "AudioRecorder-Read").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return out
    }

    private fun readLoop(ar: AudioRecord, bufSize: Int) {
        val buf = ShortArray(bufSize / BYTES_PER_SAMPLE)
        try {
            while (recording.get()) {
                val n = ar.read(buf, 0, buf.size)
                if (n > 0) {
                    val bytes = ByteArray(n * BYTES_PER_SAMPLE)
                    for (i in 0 until n) {
                        val s = buf[i].toInt()
                        bytes[i * 2] = (s and 0xff).toByte()
                        bytes[i * 2 + 1] = ((s shr 8) and 0xff).toByte()
                    }
                    try {
                        outputStream?.write(bytes)
                        dataSize += bytes.size
                        try {
                            onAudioFrame?.invoke(bytes)
                        } catch (_: Throwable) {
                            // 网络发送失败不打断本地录音；停止时由 ASR finish 统一报错。
                        }
                    } catch (_: java.io.IOException) {
                        break
                    }
                }
            }
        } catch (_: Throwable) {
            // ar.stop() 会触发 read() 抛异常，正常路径
        }
    }

    fun elapsedMs(): Long = System.currentTimeMillis() - startedAtMs

    fun stop(): File? {
        val ar = recorder ?: return null
        val f = currentFile
        recorder = null
        currentFile = null
        recording.set(false)
        try { ar.stop() } catch (_: Throwable) {}
        ar.release()
        try { readThread?.join(500) } catch (_: InterruptedException) {}
        readThread = null
        try { outputStream?.close() } catch (_: Throwable) {}
        outputStream = null
        onAudioFrame = null
        f?.let { updateWavHeader(it, dataSize) }
        return f
    }

    fun cancel() {
        recording.set(false)
        try { recorder?.stop() } catch (_: Throwable) {}
        recorder?.release()
        recorder = null
        try { readThread?.join(200) } catch (_: InterruptedException) {}
        readThread = null
        try { outputStream?.close() } catch (_: Throwable) {}
        outputStream = null
        onAudioFrame = null
        currentFile?.delete()
        currentFile = null
    }

    private fun writeWavHeader(out: FileOutputStream) {
        val byteRate = SAMPLE_RATE * BYTES_PER_SAMPLE
        val blockAlign = BYTES_PER_SAMPLE
        out.write("RIFF".toByteArray())
        out.write(intToLe(36))             // file size - 8（占位，stop 时回填）
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intToLe(16))             // fmt chunk size
        out.write(shortToLe(1))            // PCM format
        out.write(shortToLe(1))            // mono
        out.write(intToLe(SAMPLE_RATE))
        out.write(intToLe(byteRate))
        out.write(shortToLe(blockAlign.toShort()))
        out.write(shortToLe(BITS_PER_SAMPLE.toShort()))
        out.write("data".toByteArray())
        out.write(intToLe(0))              // data size（占位，stop 时回填）
    }

    private fun updateWavHeader(file: File, size: Int) {
        if (size <= 0) return
        try {
            RandomAccessFile(file, "rw").use { rf ->
                rf.seek(4)
                rf.write(intToLe(36 + size))
                rf.seek(40)
                rf.write(intToLe(size))
            }
        } catch (_: Throwable) {
            // 录音没拿到任何数据，header 占位也不更新；下游读到空 wav 会返 AsrEmptyTranscript
        }
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

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNELS = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT_PCM = AudioFormat.ENCODING_PCM_16BIT
        private const val BITS_PER_SAMPLE = 16
        private const val BYTES_PER_SAMPLE = 2
    }
}
