// §3.1.9 / §A.14 ASR Provider 子页（v0.7.0）
package com.elder.android.screen.asr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elder.android.R
import com.elder.android.data.db.AsrProvider

@Composable
fun AsrProviderScreen(
    onBack: () -> Unit,
    vm: AsrProviderViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedOk) { if (state.savedOk) onBack() }

    ProviderSelectorScreen(
        title = stringResource(R.string.asr_provider_title),
        options = listOf(
            ProviderOption(
                raw = AsrProvider.BAILIAN.raw,
                title = stringResource(R.string.asr_provider_bailian_title),
                subtitle = stringResource(R.string.asr_provider_bailian_subtitle),
            ),
            ProviderOption(
                raw = AsrProvider.MINIMAX_REALTIME.raw,
                title = stringResource(R.string.asr_provider_minimax_title),
                subtitle = stringResource(R.string.asr_provider_minimax_subtitle),
            ),
        ),
        selectedRaw = state.provider.raw,
        apiKey = state.apiKey,
        isTesting = state.isTesting,
        lastTestResult = state.lastTestResult,
        topError = state.topError,
        onSelect = { vm.setProvider(AsrProvider.fromRaw(it)) },
        onChangeKey = vm::setApiKey,
        onTest = vm::test,
        onSave = { vm.saveAndBack(onBack) },
        onBack = onBack,
        onDismissError = vm::dismissError,
    )
}
