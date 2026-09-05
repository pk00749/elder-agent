// MediaRecorder 包装（§3.1.2 §L.3：MediaRecorder / 16kHz / 单声道 / m4a / 60s 硬限）
package com.elder.android.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startedAtMs: Long = 0

    fun start(): File {
        stop()
        val out = File(context.cacheDir, "elder_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(16000)
        r.setAudioChannels(1)
        r.setAudioEncodingBitRate(64000)
        r.setOutputFile(out.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        currentFile = out
        startedAtMs = System.currentTimeMillis()
        return out
    }

    fun elapsedMs(): Long = System.currentTimeMillis() - startedAtMs

    fun stop(): File? {
        val r = recorder ?: return null
        val f = currentFile
        try {
            r.stop()
        } catch (_: RuntimeException) {
            return null
        } finally {
            r.release()
            recorder = null
        }
        return f
    }

    fun cancel() {
        recorder?.runCatching { stop() }
        recorder?.release()
        recorder = null
        currentFile?.delete()
        currentFile = null
    }
}
