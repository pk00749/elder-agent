// §3.1.9 / §A.14 TTS Provider 子页（v0.7.0 + v0.8.1 整改）
package com.elder.android.screen.asr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elder.android.R
import com.elder.android.data.db.TtsProvider

@Composable
fun TtsProviderScreen(
    onBack: () -> Unit,
    vm: TtsProviderViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedOk) { if (state.savedOk) onBack() }

    ProviderSelectorScreen(
        title = stringResource(R.string.tts_provider_title),
        options = listOf(
            ProviderOption(
                raw = TtsProvider.QWEN.raw,
                title = stringResource(R.string.tts_provider_qwen_title),
                subtitle = stringResource(R.string.tts_provider_qwen_subtitle),
                keyGuideUrl = "https://dashscope.console.aliyun.com/",
            ),
            ProviderOption(
                raw = TtsProvider.MINIMAX.raw,
                title = stringResource(R.string.tts_provider_minimax_title),
                subtitle = stringResource(R.string.tts_provider_minimax_subtitle),
                keyGuideUrl = "https://platform.minimaxi.com/user-center/apikeys",
            ),
        ),
        selectedRaw = state.provider.raw,
        apiKey = state.apiKey,
        isTesting = state.isTesting,
        lastTestResult = state.lastTestResult,
        topError = state.topError,
        onSelect = { vm.setProvider(TtsProvider.fromRaw(it)) },
        onChangeKey = vm::setApiKey,
        onTest = vm::test,
        onCancelTest = vm::cancelTest,
        onSave = { vm.saveAndBack(onBack) },
        onBack = onBack,
        onDismissError = vm::dismissError,
    )
}
