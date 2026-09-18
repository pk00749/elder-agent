// §3.1.9 / §A.14 Provider 子页共用 UI（v0.7.0）
// AsrProviderScreen / TtsProviderScreen 都基于此 Composable；只传选项与 VM。
package com.elder.android.screen.asr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onSave: () -> Unit,
    onBack: () -> Unit,
    onDismissError: () -> Unit,
) {
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
                items(options) { option ->
                    ProviderOptionCard(
                        option = option,
                        selected = option.raw == selectedRaw,
                        onClick = { onSelect(option.raw) },
                    )
                }
                item {
                    Text(
                        stringResource(R.string.asr_config_api_key),
                        fontSize = FontSize.body(),
                        color = BrandColor.TextPrimary,
                    )
                }
                item {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onChangeKey,
                        placeholder = {
                            Text(
                                stringResource(R.string.asr_config_api_key_placeholder),
                                fontSize = FontSize.caption(),
                                color = BrandColor.TextSecondary,
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = FontSize.BodyInputSp.sp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = BrandColor.CardWhite,
                            unfocusedContainerColor = BrandColor.CardWhite,
                            focusedIndicatorColor = BrandColor.Brand500,
                        ),
                    )
                }
                item {
                    OutlinedButton(
                        onClick = onTest,
                        enabled = apiKey.isNotBlank() && !isTesting,
                        modifier = Modifier.fillMaxWidth().height(Size.SecondaryButtonHeight),
                        shape = RoundedCornerShape(Corner.Button),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = BrandColor.Brand500,
                            disabledContentColor = BrandColor.TextSecondary,
                        ),
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(color = BrandColor.Brand500)
                        } else {
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
                    onClick = onSave,
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
                        stringResource(R.string.common_save),
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
}

@Composable
private fun ProviderOptionCard(
    option: ProviderOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = BrandColor.CardWhite,
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

/** LazyColumn.items 助手 — for contentType list。 */
private fun androidx.compose.foundation.lazy.LazyListScope.items(
    items: List<ProviderOption>,
    itemContent: @Composable (ProviderOption) -> Unit,
) {
    items(items.size) { idx -> itemContent(items[idx]) }
}
