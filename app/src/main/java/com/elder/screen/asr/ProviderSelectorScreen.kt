// §3.1.9 / §A.14 Provider 子页共用 UI（v0.7.0 + v0.8.1 整改）
// AsrProviderScreen / TtsProviderScreen / LlmProviderScreen 都基于此 Composable；只传选项与 VM。
// v0.8.1 整改：
//   - Key 输入框：label（不再 placeholder）+ KeyboardType.Text + 👁 切换可见 + ImeAction.Done
//   - TopAppBar actions 加"测试"快捷入口（老人不必滚到底部）
//   - HapticFeedback 兜底：保存 disabled 时点击有震动
//   - Provider 切换时若跨不同 Key 别名 → 弹"将清空 Key"提示
//   - 测试中按钮显示"取消"按钮，老人中途可中止
//   - 未保存改动时按返回拦截（弹 dialog）
package com.elder.android.screen.asr

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import com.elder.android.ui.component.ElderToast

data class ProviderOption(
    val raw: String,
    val title: String,
    val subtitle: String,
    /** v0.8.1：Provider 控制台链接，用于"如何获取 Key？"按钮。 */
    val keyGuideUrl: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectorScreen(
    title: String,
    options: List<ProviderOption>,
    selectedRaw: String,
    apiKey: String,
    isTesting: Boolean,
    lastTestResult: String?,
    topError: String?,
    onSelect: (String) -> Unit,
    onChangeKey: (String) -> Unit,
    onTest: () -> Unit,
    onCancelTest: () -> Unit,   // v0.8.1 新增
    onSave: () -> Unit,
    onBack: () -> Unit,
    onDismissError: () -> Unit,
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    var pendingSwitchRaw by remember { mutableStateOf<String?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }
    // v0.11.x UI agent M-3:打开 key 指南链接失败用本地 ElderToast
    var keyGuideError by remember { mutableStateOf<String?>(null) }
    val initialKey = rememberSaveable { apiKey }
    val isDirty = apiKey != initialKey

    val selectedOption = options.firstOrNull { it.raw == selectedRaw } ?: options.firstOrNull()

    Surface(modifier = Modifier.fillMaxSize(), color = BrandColor.CardWhite) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            TopAppBar(
                title = {
                    Text(
                        title,
                        fontSize = FontSize.TitleDefaultSp.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isDirty) showExitConfirm = true else onBack()
                        },
                        modifier = Modifier.size(Size.TouchTargetMin),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (apiKey.isNotBlank() && !isTesting) {
                        IconButton(
                            onClick = onTest,
                            modifier = Modifier.size(Size.TouchTargetMin),
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.asr_config_test),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandColor.CardWhite),
            )

            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.Md),
                verticalArrangement = Arrangement.spacedBy(Spacing.Md),
                contentPadding = PaddingValues(vertical = Spacing.Md),
            ) {
                items(options) { option ->
                    ProviderOptionCard(
                        option = option,
                        selected = option.raw == selectedRaw,
                        onClick = {
                            if (option.raw != selectedRaw) {
                                pendingSwitchRaw = option.raw
                            }
                        },
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.asr_config_api_key),
                        fontSize = FontSize.body(),
                        fontWeight = FontWeight.Bold,
                        color = BrandColor.TextPrimary,
                    )
                }
                item {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onChangeKey,
                        label = { Text(stringResource(R.string.asr_config_api_key_label)) },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.asr_config_api_key_placeholder),
                                fontSize = FontSize.body(),
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { /* 让保存按钮主动调 */ }),
                        trailingIcon = {
                            TextButton(onClick = { keyVisible = !keyVisible }) {
                                Text(
                                    text = stringResource(R.string.asr_config_key_toggle),
                                    fontSize = FontSize.caption(),
                                    color = BrandColor.Brand500,
                                )
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = FontSize.BodyInputSp.sp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = BrandColor.CardWhite,
                            unfocusedContainerColor = BrandColor.CardWhite,
                            focusedIndicatorColor = BrandColor.Brand500,
                        ),
                    )
                }
                if (selectedOption?.keyGuideUrl != null) {
                    item {
                        TextButton(
                            onClick = {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(selectedOption.keyGuideUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }.onFailure {
                                    // v0.11.x UI agent M-3:失败用本地 ElderToast(§4.8),不再用 android.widget.Toast
                                    keyGuideError = ctx.getString(R.string.asr_config_open_link_failed)
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.asr_config_key_guide),
                                fontSize = FontSize.body(),
                                color = BrandColor.Brand500,
                            )
                        }
                    }
                }
                item {
                    if (isTesting) {
                        // v0.8.1：测试中显示取消按钮
                        OutlinedButton(
                            onClick = onCancelTest,
                            modifier = Modifier.fillMaxWidth().height(Size.SecondaryButtonHeight),
                            shape = RoundedCornerShape(Corner.Button),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = BrandColor.Error500,
                            ),
                        ) {
                            CircularProgressIndicator(
                                color = BrandColor.Error500,
                                modifier = Modifier.size(Size.IconMd),
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(Spacing.Sm))
                            Text(
                                stringResource(R.string.asr_config_cancel_test),
                                fontSize = FontSize.body(),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onTest,
                            enabled = apiKey.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(Size.SecondaryButtonHeight),
                            shape = RoundedCornerShape(Corner.Button),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = BrandColor.Brand500,
                                disabledContentColor = BrandColor.TextSecondary,
                            ),
                        ) {
                            Text(
                                stringResource(R.string.asr_config_test),
                                fontSize = FontSize.body(),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                if (!lastTestResult.isNullOrBlank()) {
                    item {
                        Text(
                            stringResource(R.string.asr_config_last_test, lastTestResult),
                            fontSize = FontSize.BodySmallSp.sp,
                            color = BrandColor.TextSecondary,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.Md, vertical = Spacing.Md),
            ) {
                Button(
                    onClick = {
                        if (apiKey.isBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            return@Button
                        }
                        onSave()
                    },
                    enabled = apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(Size.PrimaryButtonHeight),
                    shape = RoundedCornerShape(Corner.Button),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandColor.Brand500,
                        contentColor = BrandColor.CardWhite,
                        disabledContainerColor = BrandColor.BgGray,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.common_save),
                        fontSize = FontSize.button(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    if (!topError.isNullOrBlank()) {
        ElderToast(message = topError, onDismiss = onDismissError)
    }

    // v0.11.x UI agent M-3:key 指南链接打开失败的本地 ElderToast
    if (!keyGuideError.isNullOrBlank()) {
        ElderToast(message = keyGuideError, onDismiss = { keyGuideError = null })
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringResource(R.string.asr_config_unsaved_title), fontSize = FontSize.body(), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.asr_config_unsaved_text), fontSize = FontSize.body()) },
            confirmButton = {
                TextButton(onClick = { showExitConfirm = false; onSave() }) {
                    Text(stringResource(R.string.common_save), color = BrandColor.Brand500)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false; onBack() }) {
                    Text(stringResource(R.string.common_cancel), color = BrandColor.TextSecondary)
                }
            },
        )
    }

    pendingSwitchRaw?.let { newRaw ->
        AlertDialog(
            onDismissRequest = { pendingSwitchRaw = null },
            title = { Text(stringResource(R.string.asr_config_switch_title), fontSize = FontSize.body(), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.asr_config_switch_text), fontSize = FontSize.body()) },
            confirmButton = {
                TextButton(onClick = { onSelect(newRaw); pendingSwitchRaw = null; onChangeKey("") }) {
                    Text(stringResource(R.string.common_confirm), color = BrandColor.Brand500)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSwitchRaw = null }) {
                    Text(stringResource(R.string.common_cancel), color = BrandColor.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun ProviderOptionCard(
    option: ProviderOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        interactionSource = interactionSource,
        color = if (isPressed) BrandColor.BgGray else BrandColor.CardWhite,
        shape = RoundedCornerShape(Corner.Card),
        border = BorderStroke(1.dp, if (selected) BrandColor.Brand500 else BrandColor.BgGray),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = BrandColor.Brand500,
                    unselectedColor = BrandColor.TextSecondary,
                ),
            )
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.Sm)) {
                Text(
                    option.title,
                    fontSize = FontSize.body(),
                    fontWeight = FontWeight.Bold,
                    color = BrandColor.TextPrimary,
                )
                Text(
                    option.subtitle,
                    fontSize = FontSize.caption(),
                    color = BrandColor.TextSecondary,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = BrandColor.Brand500,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(
    items: List<ProviderOption>,
    itemContent: @Composable (ProviderOption) -> Unit,
) {
    items(items.size) { idx -> itemContent(items[idx]) }
}
