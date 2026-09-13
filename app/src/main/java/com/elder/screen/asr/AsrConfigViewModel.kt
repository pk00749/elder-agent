// §3.1.9 ASR API 配置页 ViewModel（v3.0.1 §A.1.b：只剩百炼 API Key，WorkspaceId/model 硬编码）
package com.elder.android.screen.asr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.data.AsrConfig
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.asr.AsrApiClient
import com.elder.android.di.ServiceLocator
import com.elder.android.error.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class AsrConfigUiState(
    val apiKey: String = "",
    val isTesting: Boolean = false,
    val lastTestResult: String? = null,
    val isSaving: Boolean = false,
    val savedOk: Boolean = false,
    val topError: String? = null,
) {
    val allRequiredValid: Boolean get() = apiKey.isNotBlank()
}

class AsrConfigViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: AsrConfigRepository = ServiceLocator.asrConfigRepo
    private val api: AsrApiClient = ServiceLocator.asrApi

    private val _state = MutableStateFlow(AsrConfigUiState())
    val state: StateFlow<AsrConfigUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.current()?.let { applyConfig(it) }
        }
    }

    private fun applyConfig(c: AsrConfig) {
        _state.update {
            it.copy(
                apiKey = c.apiKey,
                lastTestResult = c.lastTestResult,
            )
        }
    }

    fun setApiKey(v: String) = _state.update { it.copy(apiKey = v) }

    fun dismissError() = _state.update { it.copy(topError = null) }

    fun test(onDone: () -> Unit = {}) {
        val s = _state.value
        if (!s.allRequiredValid) {
            _state.update { it.copy(topError = "请先填 API Key") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isTesting = true, topError = null) }
            val sampleFile = createSineWaveSample(getApplication<Application>().cacheDir)
            val result = runCatching { api.transcribe(apiKey = s.apiKey, audioFile = sampleFile) }
            sampleFile.delete()
            result.onSuccess { r ->
                _state.update {
                    it.copy(
                        isTesting = false,
                        lastTestResult = """{"text":"${r.text}","latency_ms":${System.currentTimeMillis() - (it.lastTestResult?.length ?: 0)}}""",
                        topError = null,
                    )
                }
            }.onFailure { e ->
                // 空转写 = "服务端成功接收 + 音频非语音"，对"测试 API Key 是否通"来说已经算成功；不要让用户误以为失败
                val (msg, topErr) = when (e) {
                    is AppError.AsrEmptyTranscript ->
                        "✓ 连接正常（测试音频无语音，正式录音可识别）" to null
                    is AppError -> (e.message ?: "测试失败") to (e.message ?: "测试失败")
                    else -> (e.message ?: "测试失败") to (e.message ?: "测试失败")
                }
                _state.update {
                    it.copy(
                        isTesting = false,
                        lastTestResult = """{"error":"$msg"}""",
                        topError = topErr,
                    )
                }
            }
            onDone()
        }
    }

    fun save(onDone: () -> Unit = {}) {
        val s = _state.value
        if (!s.allRequiredValid) {
            _state.update { it.copy(topError = "请先填 API Key") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, topError = null) }
            try {
                repo.save(
                    AsrConfig(
                        apiKey = s.apiKey,
                        lastTestResult = s.lastTestResult,
                    )
                )
                _state.update { it.copy(isSaving = false, savedOk = true) }
                onDone()
            } catch (t: Throwable) {
                _state.update { it.copy(isSaving = false, topError = t.message ?: "保存失败") }
            }
        }
    }

    /**
     * 生成一段 5 秒 440Hz 正弦波 WAV（PCM 16-bit/16kHz/mono）作为测试音频（§3.1.9 安全边界）
     * Realtime 服务端按 PCM 接收；该测试音频无语音，回空文本由 test() 视为连接成功。
     */
    private fun createSineWaveSample(cacheDir: File): File {
        val out = File(cacheDir, "asr_test_${System.currentTimeMillis()}.wav")
        val sampleRate = 16000
        val durationSec = 5
        val samples = sampleRate * durationSec
        val pcm = ShortArray(samples)
        for (i in 0 until samples) {
            val angle = i.toDouble() / sampleRate * 2.0 * Math.PI * 440.0
            pcm[i] = (Math.sin(angle) * Short.MAX_VALUE * 0.3).toInt().toShort()
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

    private fun writeWavHeader(out: java.io.OutputStream, pcmSize: Int, sampleRate: Int, channels: Int) {
        val byteRate = sampleRate * channels * 2
        val dataSize = pcmSize * 2
        val totalSize = 36 + dataSize
        out.write("RIFF".toByteArray())
        out.write(intToLe(totalSize))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intToLe(16))
        out.write(shortToLe(1))                 // PCM
        out.write(shortToLe(channels.toShort()))
        out.write(intToLe(sampleRate))
        out.write(intToLe(byteRate))
        out.write(shortToLe((channels * 2).toShort()))  // block align
        out.write(shortToLe(16))                // bits per sample
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
