// §3.1.9 ASR Provider 子页 ViewModel（v0.7.0 §A.14）
// Provider 选项 + 当前 Provider 所需 Key + 测试按钮。
// Provider 切换后通过 [setProviderAndPersist] 落库；测试按钮调用 ServiceLocator.asrClient()。
package com.elder.android.screen.asr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.data.AsrConfig
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.asr.AsrApiClient
import com.elder.android.data.asr.MiniMaxAsrClient
import com.elder.android.data.db.AsrProvider
import com.elder.android.di.ServiceLocator
import com.elder.android.error.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class AsrProviderUiState(
    val provider: AsrProvider = AsrProvider.MINIMAX_REALTIME,
    val apiKey: String = "",
    val isTesting: Boolean = false,
    val lastTestResult: String? = null,
    val topError: String? = null,
    val savedOk: Boolean = false,
) {
    val allRequiredValid: Boolean get() = apiKey.isNotBlank()
}

class AsrProviderViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: AsrConfigRepository = ServiceLocator.asrConfigRepo

    private val _state = MutableStateFlow(AsrProviderUiState())
    val state: StateFlow<AsrProviderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cfg = repo.current() ?: return@launch
            _state.update {
                it.copy(
                    provider = cfg.asrProvider,
                    apiKey = cfg.asrKey(),
                    lastTestResult = cfg.lastTestResult,
                )
            }
        }
    }

    fun setProvider(p: AsrProvider) = _state.update { it.copy(provider = p) }

    fun setApiKey(v: String) = _state.update { it.copy(apiKey = v) }

    fun dismissError() = _state.update { it.copy(topError = null) }

    fun saveAndBack(onDone: () -> Unit) {
        val s = _state.value
        if (!s.allRequiredValid) {
            _state.update { it.copy(topError = "请先填 Key") }
            return
        }
        viewModelScope.launch {
            // 对应 §3.1.9：未在顶层初始化配置时，子页也能独立保存 —— 用当前选中的 Provider
            // 反推一份默认 AsrConfig；避免上层未写时这里静默 return 让老人觉得没反应。
            val existing = repo.current()
            val cfg = existing ?: AsrConfig(
                apiKey = if (s.provider == AsrProvider.BAILIAN) s.apiKey else "",
                minimaxApiKey = if (s.provider == AsrProvider.MINIMAX_REALTIME) s.apiKey else "",
            )
            val updated = cfg.copy(asrProvider = s.provider)
            try {
                when (s.provider) {
                    AsrProvider.BAILIAN -> {
                        // 千问 Key 写入 api_key_enc
                        repo.save(updated.copy(apiKey = s.apiKey))
                    }
                    AsrProvider.MINIMAX_REALTIME -> {
                        // MiniMax ASR 复用 minimax_api_key_enc
                        repo.save(updated.copy(minimaxApiKey = s.apiKey))
                    }
                }
                _state.update { it.copy(savedOk = true, topError = null) }
                onDone()
            } catch (t: Throwable) {
                // 对应 §4.8：保存失败 / Keystore 不可用走顶部红条兜底
                _state.update { it.copy(topError = t.message ?: "保存失败") }
            }
        }
    }

    fun test() {
        val s = _state.value
        if (s.apiKey.isBlank()) {
            _state.update { it.copy(topError = "请先填 Key") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isTesting = true, topError = null) }
            val client = when (s.provider) {
                AsrProvider.BAILIAN -> AsrApiClient()
                AsrProvider.MINIMAX_REALTIME -> MiniMaxAsrClient()
            }
            val sampleFile = createSineWaveSample(getApplication<Application>().cacheDir)
            val started = System.currentTimeMillis()
            val result = runCatching { client.transcribe(apiKey = s.apiKey, audioFile = sampleFile) }
            sampleFile.delete()
            result.onSuccess { r ->
                _state.update {
                    it.copy(
                        isTesting = false,
                        lastTestResult = """{"text":"${r.text}","latency_ms":${System.currentTimeMillis() - started}}""",
                        topError = null,
                    )
                }
            }.onFailure { e ->
                val msg = when (e) {
                    is AppError.AsrEmptyTranscript -> "✓ 连接正常（测试音频无语音，正式录音可识别）"
                    is AppError -> e.message ?: "测试失败"
                    else -> e.message ?: "測試失敗"
                }
                _state.update {
                    it.copy(
                        isTesting = false,
                        lastTestResult = """{"error":"$msg"}""",
                        topError = if (e is AppError.AsrEmptyTranscript) null else msg,
                    )
                }
            }
        }
    }

    /** 与 AsrConfigViewModel 同样的 sine wave 测试音频生成。 */
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
