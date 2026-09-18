// §3.1.9 v0.8.0 + §A.15：LLM Provider 子页（v0.8.0）
package com.elder.android.screen.asr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elder.android.R
import com.elder.android.data.db.LlmProvider

@Composable
fun LlmProviderScreen(
    onBack: () -> Unit,
    vm: LlmProviderViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedOk) { if (state.savedOk) onBack() }

    ProviderSelectorScreen(
        title = stringResource(R.string.llm_provider_title),
        options = listOf(
            ProviderOption(
                raw = LlmProvider.MINIMAX.raw,
                title = stringResource(R.string.llm_provider_minimax_title),
                subtitle = stringResource(R.string.llm_provider_minimax_subtitle),
            ),
            ProviderOption(
                raw = LlmProvider.QWEN.raw,
                title = stringResource(R.string.llm_provider_qwen_title),
                subtitle = stringResource(R.string.llm_provider_qwen_subtitle),
            ),
            ProviderOption(
                raw = LlmProvider.DEEPSEEK.raw,
                title = stringResource(R.string.llm_provider_deepseek_title),
                subtitle = stringResource(R.string.llm_provider_deepseek_subtitle),
            ),
        ),
        selectedRaw = state.provider.raw,
        apiKey = state.apiKey,
        isTesting = state.isTesting,
        lastTestResult = state.lastTestResult,
        topError = state.topError,
        onSelect = { vm.setProvider(LlmProvider.fromRaw(it)) },
        onChangeKey = vm::setApiKey,
        onTest = vm::test,
        onSave = { vm.saveAndBack(onBack) },
        onBack = onBack,
        onDismissError = vm::dismissError,
    )
}
