// §3.1.9 AI 服务配置页（v0.7.0 §A.14）
// 顶层页：两个入口卡（语音识别 ASR / 语音播报 TTS），各自承载 Provider 选择 + Key 输入 + 测试。
// Provider 选择子页在 AsrProviderScreen / TtsProviderScreen。
package com.elder.android.screen.asr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import com.elder.android.ui.component.ElderToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsrConfigScreen(
    onBack: () -> Unit,
    onOpenAsrProvider: () -> Unit,
    onOpenTtsProvider: () -> Unit,
    onOpenLlmProvider: () -> Unit,
    vm: AsrConfigViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedOk) {
        if (state.savedOk) {
            onBack()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = BrandColor.CardWhite) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.asr_config_title),
                        fontSize = FontSize.TitleDefaultSp.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(Size.TouchTargetMin),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandColor.CardWhite),
            )

            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.Md),
                verticalArrangement = Arrangement.spacedBy(Spacing.Md),
                contentPadding = PaddingValues(vertical = Spacing.Md),
            ) {
                item { HeaderCard() }
                item {
                    ProviderEntryCard(
                        title = stringResource(R.string.asr_config_card_asr_title),
                        subtitle = stringResource(
                            R.string.asr_config_card_asr_subtitle,
                            state.asrProviderDisplay,
                        ),
                        onClick = onOpenAsrProvider,
                    )
                }
                item {
                    ProviderEntryCard(
                        title = stringResource(R.string.asr_config_card_tts_title),
                        subtitle = stringResource(
                            R.string.asr_config_card_tts_subtitle,
                            state.ttsProviderDisplay,
                        ),
                        onClick = onOpenTtsProvider,
                    )
                }
                item {
                    ProviderEntryCard(
                        title = stringResource(R.string.asr_config_card_llm_title),
                        subtitle = stringResource(
                            R.string.asr_config_card_llm_subtitle,
                            state.llmProviderDisplay,
                            if (state.llmKey().isNotBlank())
                                stringResource(R.string.settings_asr_configured)
                            else
                                stringResource(R.string.settings_asr_not_configured),
                        ),
                        onClick = onOpenLlmProvider,
                    )
                }
                item { LastTestResult(state.lastTestResult) }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.Md, vertical = Spacing.Md),
                verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
            ) {
                // 对应 §3.1.9：未填全 Key 时给老人可读原因，避免按钮 disabled 静默无反馈
                if (!state.allRequiredValid && !state.isSaving) {
                    Text(
                        text = stringResource(R.string.asr_config_save_hint),
                        fontSize = FontSize.caption(),
                        color = BrandColor.TextSecondary,
                    )
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = vm::save,
                        enabled = !state.isSaving && state.allRequiredValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Size.PrimaryButtonHeight),
                        shape = RoundedCornerShape(Corner.Button),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandColor.Brand500,
                            contentColor = BrandColor.CardWhite,
                            disabledContainerColor = BrandColor.BgGray,
                        ),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(color = BrandColor.CardWhite)
                        } else {
                            Text(
                                text = stringResource(R.string.common_save),
                                fontSize = FontSize.button(),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }

    // 对应 §4.8：保存失败 / Key 未填错误统一通过 ElderToast 红条兜底
    if (!state.topError.isNullOrBlank()) {
        ElderToast(message = state.topError, onDismiss = vm::dismissError)
    }
}

@Composable
private fun HeaderCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BrandColor.BgGray,
        shape = RoundedCornerShape(Corner.Card),
    ) {
        Text(
            stringResource(R.string.asr_config_header),
            modifier = Modifier.padding(Spacing.Md),
            fontSize = FontSize.body(),
            color = BrandColor.TextSecondary,
        )
    }
}

@Composable
private fun ProviderEntryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = BrandColor.CardWhite,
        shape = RoundedCornerShape(Corner.Card),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandColor.BgGray),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Size.ListRowMinHeight)
                .padding(horizontal = Spacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = FontSize.body(),
                    fontWeight = FontWeight.Bold,
                    color = BrandColor.TextPrimary,
                )
                Text(
                    subtitle,
                    fontSize = FontSize.caption(),
                    color = BrandColor.TextSecondary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = BrandColor.TextSecondary,
                modifier = Modifier.size(Size.IconMd),
            )
        }
    }
}

@Composable
private fun LastTestResult(text: String?) {
    if (text.isNullOrBlank()) return
    Text(
        stringResource(R.string.asr_config_last_test, text),
        fontSize = FontSize.BodySmallSp.sp,
        color = BrandColor.TextSecondary,
    )
}

