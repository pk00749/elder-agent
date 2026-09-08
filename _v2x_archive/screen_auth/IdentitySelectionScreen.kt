// §B 身份选择 —— v2.1.2 anonymous-device 流程（无登录屏）
package com.elder.android.screen.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import com.elder.android.di.ServiceLocator
import com.elder.android.network.dto.InitDeviceRequest
import kotlinx.coroutines.launch
import retrofit2.HttpException as RetrofitHttpException

@Composable
fun IdentitySelectionScreen(
    onPickedFamily: () -> Unit,
    onPickedElder: () -> Unit,
) {
    var busy by remember { mutableStateOf<String?>(null) }  // null | "family" | "elder"
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val apiClient = ServiceLocator.apiClient
    val tokenStore = ServiceLocator.tokenStore

    fun pickFamily() {
        if (busy != null) return
        busy = "family"
        errorMsg = null
        scope.launch {
            runCatching {
                val deviceToken = tokenStore.ensureDeviceToken()
                apiClient.accountApi.initDevice(InitDeviceRequest(deviceToken = deviceToken))
            }.onSuccess { resp ->
                tokenStore.save(
                    token = resp.token,
                    userId = resp.userId,
                    role = resp.role,
                    elderId = null,
                )
                onPickedFamily()
            }.onFailure {
                busy = null
                errorMsg = if ((it as? RetrofitHttpException)?.code() == 422) {
                    "设备标识无效，请重启 App 重试"
                } else {
                    "网络异常，请稍后重试"
                }
            }
        }
    }

    fun pickElder() {
        if (busy != null) return
        busy = "elder"
        errorMsg = null
        scope.launch {
            runCatching { tokenStore.ensureDeviceToken() }
                .onSuccess {
                    tokenStore.saveRole(role = "elder")
                    onPickedElder()
                }
                .onFailure {
                    busy = null
                    errorMsg = "本地存储异常，请稍后重试"
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandColor.CardWhite)
            .padding(Spacing.Lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.identity_pick_title),
            fontSize = FontSize.title(),
            color = BrandColor.TextPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.Xl))
        BigChoiceButton(
            text = stringResource(R.string.identity_family),
            onClick = ::pickFamily,
            primary = true,
            enabled = busy == null,
            loading = busy == "family",
        )
        Spacer(modifier = Modifier.height(Spacing.Lg))
        BigChoiceButton(
            text = stringResource(R.string.identity_elder),
            onClick = ::pickElder,
            primary = false,
            enabled = busy == null,
            loading = busy == "elder",
        )
        errorMsg?.let {
            Spacer(modifier = Modifier.height(Spacing.Md))
            Text(it, color = BrandColor.Error500, fontSize = FontSize.caption())
        }
    }
}

@Composable
private fun BigChoiceButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
    enabled: Boolean,
    loading: Boolean,
) {
    val container = if (primary) BrandColor.Brand500 else BrandColor.BgGray
    val content = if (primary) BrandColor.CardWhite else BrandColor.TextPrimary
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.5f),
            disabledContentColor = content.copy(alpha = 0.7f),
        ),
        shape = RoundedCornerShape(Corner.Button),
        modifier = Modifier
            .fillMaxWidth()
            .height(Size.PrimaryButtonHeight),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(FontSize.body().value.toInt().dp),
                color = content,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text = text, fontSize = FontSize.body(), textAlign = TextAlign.Center)
        }
    }
}
