// §3.1.9 ASR API 配置页（v3.0.1 §A.1.b：只剩百炼 API Key；WorkspaceId/model 卡片只读展示）
package com.elder.android.screen.asr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elder.android.R
import com.elder.android.data.asr.AsrApiClient.Companion.BAILIAN_MODEL
import com.elder.android.data.asr.AsrApiClient.Companion.BAILIAN_WORKSPACE_ID
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontLevel
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import com.elder.android.ui.component.ElderToast

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AsrConfigScreen(
    onBack: () -> Unit,
    vm: AsrConfigViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedOk) {
        if (state.savedOk) {
            // §3.1.9：保存后回主屏
            onBack()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BrandColor.CardWhite,
    ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
            TopAppBar(
                title = { Text(stringResource(R.string.asr_config_title), fontSize = FontSize.TitleDefaultSp.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(Size.TouchTargetMin),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColor.CardWhite,
                ),
            )

            var showAdvanced by remember { mutableStateOf(false) }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.Md),
                verticalArrangement = Arrangement.spacedBy(Spacing.Md),
                contentPadding = PaddingValues(vertical = Spacing.Md),
            ) {
                item { HeaderCard() }
                item {
                    TextButton(onClick = { showAdvanced = !showAdvanced }) {
                        Text(
                            text = stringResource(
                                if (showAdvanced) R.string.asr_config_advanced_hide
                                else R.string.asr_config_advanced_show,
                            ),
                            color = BrandColor.TextSecondary,
                            fontSize = FontSize.BodySmallSp.sp,
                        )
                    }
                }
                if (showAdvanced) {
                    item { BailianConfigCard() }
                }
                item {
                    LabeledField(
                        label = stringResource(R.string.asr_config_api_key),
                        value = state.apiKey,
                        onChange = vm::setApiKey,
                        placeholder = stringResource(R.string.asr_config_api_key_placeholder),
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                    )
                }
                item {
                    TestButton(
                        isLoading = state.isTesting,
                        enabled = state.allRequiredValid && !state.isTesting,
                        onClick = { vm.test() },
                    )
                }
                // v3.0.1 §A.1.b：顶部 ✓ 已升级为真 TextButton 调 vm.save()，正文不再重复"保存"按钮
                state.lastTestResult?.let {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BrandColor.BgGray, RoundedCornerShape(Corner.Card))
                                .padding(Spacing.Md),
                        ) {
                            Text(
                                stringResource(R.string.asr_config_last_test, it),
                                fontSize = FontSize.BodySmallSp.sp,
                                color = BrandColor.TextSecondary,
                            )
                        }
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
                    onClick = vm::save,
                    enabled = state.allRequiredValid,
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
                    Text(
                        text = stringResource(R.string.common_save),
                        fontSize = FontSize.button(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    state.topError?.let { msg ->
        ElderToast(
            message = msg,
            onDismiss = vm::dismissError,
        )
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

/** v3.0.1 §A.1.b：WorkspaceId + model 只读展示，UI 不能再改 */
@Composable
private fun BailianConfigCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BrandColor.BgGray,
        shape = RoundedCornerShape(Corner.Card),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
        ) {
            Text(stringResource(R.string.asr_config_provider), fontSize = FontSize.body(), color = BrandColor.TextPrimary)
            Text(stringResource(R.string.asr_config_model, BAILIAN_MODEL), fontSize = FontSize.BodySmallSp.sp, color = BrandColor.TextSecondary)
            Text(stringResource(R.string.asr_config_workspace_id, BAILIAN_WORKSPACE_ID), fontSize = FontSize.BodySmallSp.sp, color = BrandColor.TextSecondary)
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
        Text(label, fontSize = FontSize.body(), color = BrandColor.TextPrimary)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, fontSize = FontSize.caption(), color = BrandColor.TextSecondary) },
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = FontSize.BodyInputSp.sp),
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = BrandColor.CardWhite,
                unfocusedContainerColor = BrandColor.CardWhite,
                focusedIndicatorColor = BrandColor.Brand500,
            ),
        )
    }
}

@Composable
private fun TestButton(
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(Size.SecondaryButtonHeight),
        shape = RoundedCornerShape(Corner.Button),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = BrandColor.Brand500,
            disabledContentColor = BrandColor.TextSecondary,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = BrandColor.Brand500)
        } else {
            Text(
                stringResource(R.string.asr_config_test),
                fontSize = FontSize.body(FontLevel.LARGE),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
