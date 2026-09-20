// §3.1.9 AI 服务配置页（v0.7.0 §A.14 + v0.8.0 §A.15 + v0.8.1 整改）
// v0.8.1 整改：
//   - 顶层改只读状态总览（§A.15.3 设计意图）：三张入口卡 + 状态；无 OutlinedTextField，无保存按钮
//   - 删 LastTestResult（测试发生在子页，顶层显示会误导）
//   - 顶部 hint 改为按缺口 Provider 动态列出
//   - 每张入口卡右侧根据是否已配置显示红点
package com.elder.android.screen.asr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
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
                // v0.8.1 整改：按缺口 Provider 动态列出（替代"请补全..."静态文案）
                if (!state.asrConfigured || !state.ttsConfigured || !state.llmConfigured) {
                    item {
                        MissingProvidersHint(
                            missingAsr = !state.asrConfigured,
                            missingTts = !state.ttsConfigured,
                            missingLlm = !state.llmConfigured,
                        )
                    }
                }
                item {
                    ProviderEntryCard(
                        title = stringResource(R.string.asr_config_card_asr_title),
                        subtitle = stringResource(
                            R.string.asr_config_card_asr_subtitle,
                            state.asrProviderDisplay,
                        ),
                        onClick = onOpenAsrProvider,
                        showRedDot = !state.asrConfigured,
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
                        showRedDot = !state.ttsConfigured,
                    )
                }
                item {
                    ProviderEntryCard(
                        title = stringResource(R.string.asr_config_card_llm_title),
                        subtitle = stringResource(
                            R.string.asr_config_card_llm_subtitle,
                            state.llmProviderDisplay,
                            if (state.llmConfigured)
                                stringResource(R.string.settings_asr_configured)
                            else
                                stringResource(R.string.settings_asr_not_configured),
                        ),
                        onClick = onOpenLlmProvider,
                        showRedDot = !state.llmConfigured,
                    )
                }
            }
        }
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

/**
 * v0.8.1 整改：按缺口 Provider 动态列出（取代"请补全各 Provider 所需 API Key 后再保存"）。
 * 老人一眼看到还差哪几个，比静态文案更明确。
 */
@Composable
private fun MissingProvidersHint(missingAsr: Boolean, missingTts: Boolean, missingLlm: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BrandColor.NetYellow,
        shape = RoundedCornerShape(Corner.Card),
    ) {
        Column(modifier = Modifier.padding(Spacing.Md)) {
            Text(
                text = stringResource(R.string.asr_config_missing_title),
                fontSize = FontSize.body(),
                fontWeight = FontWeight.Bold,
                color = BrandColor.TextPrimary,
            )
            if (missingAsr) Text("• ${stringResource(R.string.asr_config_card_asr_title)}", fontSize = FontSize.body())
            if (missingTts) Text("• ${stringResource(R.string.asr_config_card_tts_title)}", fontSize = FontSize.body())
            if (missingLlm) Text("• ${stringResource(R.string.asr_config_card_llm_title)}", fontSize = FontSize.body())
        }
    }
}

@Composable
private fun ProviderEntryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showRedDot: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = BrandColor.CardWhite,
        shape = RoundedCornerShape(Corner.Card),
        border = BorderStroke(1.dp, BrandColor.BgGray),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Size.ListRowMinHeight)
                .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
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
            if (showRedDot) {
                RedDot()
                Spacer8()
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
private fun RedDot() {
    Box(
        modifier = Modifier
            .size(Size.WarningDotSize)
            .clip(CircleShape)
            .background(BrandColor.Error500),
    )
}

@Composable
private fun Spacer8() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(Spacing.Sm))
}
