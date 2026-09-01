// §3.2.7 扫码绑定 —— MVP 简化：手动输入 bind_code（屏幕上显示）；完整相机扫描留 PR 4.x
package com.elder.android.screen.bind

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.elder.android.R
import com.elder.android.data.TokenStore
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import com.elder.android.di.ServiceLocator
import com.elder.android.network.dto.FamilyBindAttemptRequest
import kotlinx.coroutines.launch

@Composable
fun FamilyBindScreen(onBack: () -> Unit) {
    var bindCode by remember = { mutableStateOf("") }
    var status by remember = { mutableStateOf<String?() null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().background(BrandColor.CardWhite).padding(Spacing.Lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
        Text(stringResource(R.string.bind_qr_scan_title), fontSize = FontSize.title(), color = BrandColor.TextPrimary)
        Spacer(Modifier.height(Spacing.Md))
        Text("MVP 简化：输入爸妈屏幕上显示的 8 位码", fontSize = FontSize.caption(), color = BrandColor.TextSecondary)
        Spacer(Modifier.height(Spacing.Lg))
        OutlinedTextField(
            value = bindCode,
            onValueChange = { v -> bindCode = v.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.Lg))
        Button(
            onClick = {
                scope.launch {
                    runCatching {
                        ServiceLocator.apiClient.accountApi.familyBindAttempt(
                            FamilyBindAttemptRequest(bindCode = bindCode),
                        )
                    }.onSuccess {
                        status = "已发起绑定，等爸妈手机上确认"
                        val snap = ServiceLocator.tokenStore.current()
                        if (snap?.elderId == null && snap?.role == "family") {
                            ServiceLocator.tokenStore.updateElderId("pending")
                        }
                    }.onFailure {
                        status = "扫描失败：${it.message ?: "未知错误"}"
                    }
                }
            },
            enabled = bindCode.length == 8,
            colors = ButtonDefaults.buttonColors(containerColor = BrandColor.Brand500, contentColor = BrandColor.CardWhite),
            shape = RoundedCornerShape(Corner.Button),
            modifier = Modifier.fillMaxWidth().height(Size.PrimaryButtonHeight),
        ) { Text(stringResource(R.string.bind_confirm_accept), fontSize = FontSize.body()) }
        status?.let {
            Spacer(Modifier.height(Spacing.Md))
            Text(it, fontSize = FontSize.body(), color = BrandColor.TextPrimary)
        }
    }
}
