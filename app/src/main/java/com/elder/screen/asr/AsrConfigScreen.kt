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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elder.android.data.asr.AsrApiClient.Companion.BAILIAN_MODEL
import com.elder.android.data.asr.AsrApiClient.Companion.BAILIAN_WORKSPACE_ID
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
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
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("AI 语音识别", fontSize = FontSize.TitleDefaultSp.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Text(
                        "✓",
                        modifier = Modifier
                            .padding(end = Spacing.Md)
                            .background(
                                color = if (state.allRequiredValid) BrandColor.Brand500 else BrandColor.BgGray,
                                shape = RoundedCornerShape(Corner.Pill),
                            )
                            .padding(horizontal = Spacing.Md, vertical = Spacing.Xs),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColor.CardWhite,
                ),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.Md),
                verticalArrangement = Arrangement.spacedBy(Spacing.Md),
                contentPadding = PaddingValues(vertical = Spacing.Md),
            ) {
                item { HeaderCard() }
                item { BailianConfigCard() }
                item {
                    LabeledField(
                        label = "API Key",
                        value = state.apiKey,
                        onChange = vm::setApiKey,
                        placeholder = "sk-...",
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
                            Text("最近测试：$it", fontSize = 18.sp, color = BrandColor.TextSecondary)
                        }
                    }
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
            "把百炼 ASR 的 API Key 填在这里，老人端不依赖任何第三方服务器。配置只保存在这台手机。",
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
            Text("服务商：阿里云百炼", fontSize = FontSize.body(), color = BrandColor.TextPrimary)
            Text("模型：$BAILIAN_MODEL", fontSize = 18.sp, color = BrandColor.TextSecondary)
            Text("WorkspaceId：$BAILIAN_WORKSPACE_ID", fontSize = 18.sp, color = BrandColor.TextSecondary)
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
            placeholder = { Text(placeholder, fontSize = 20.sp, color = BrandColor.TextSecondary) },
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp),
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
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        shape = RoundedCornerShape(Corner.Button),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandColor.Brand500,
            contentColor = Color.White,
            disabledContainerColor = BrandColor.BgGray,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Text("测试一下", fontSize = FontSize.button(), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SaveButton(
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        shape = RoundedCornerShape(Corner.Button),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandColor.Brand500,
            contentColor = Color.White,
            disabledContainerColor = BrandColor.BgGray,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Text("保存", fontSize = FontSize.button(), fontWeight = FontWeight.Bold)
        }
    }
}
