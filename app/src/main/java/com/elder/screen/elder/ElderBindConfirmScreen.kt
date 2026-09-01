// §3.1.8 第 4 项 + §3.2.8 老人端绑定确认卡 —— 显示 bind_code + 轮询 pending + 接受/拒绝
package com.elder.android.screen.elder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.elder.android.design.tokens.Spacing
import com.elder.android.di.ServiceLocator
import com.elder.android.network.dto.ConfirmRequest
import com.elder.android.util.Permissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalContext

@Composable
fun ElderBindConfirmScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val deviceToken = remember { UUID.randomUUID().toString() } // §3.1.8 第 4 项；客户端生成即可
    var bindCode by remember { mutableStateOf<String?() null) }
    var pending by remember { mutableStateOf<List<com.elder.android.network.dto.PendingItem>>(emptyList()) }
    var current by remember { mutableStateOf<com.elder.android.network.dto.PendingItem?() null) }
    var elderName by remember { mutableStateOf("") }
    var showGenerate by remember { mutableStateOf(true) }
    var expiresAt by remember { mutableStateOf(0L) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // §3.1.8：每 60 秒自动重新生成 bind_code；按服务器返回 expires_in 倒计时
    LaunchedEffect(Unit) {
        if (!Permissions.check(context, android.Manifest.permission.RECORD_AUDIO)) {
            // 麦克风权限单独走 util.RequestPermissionEffect
        }
    }

    suspend fun generateCode() {
        runCatching {
            ServiceLocator.apiClient.accountApi.createBindCode(
                com.elder.android.network.dto.BindCodeRequest(deviceToken),
            )
        }.onSuccess {
            bindCode = it.bindCode
            expiresAt = System.currentTimeMillis() + it.expiresIn * 1000
        }
    }

    LaunchedEffect(Unit) { generateCode() }

    // 10s/次轮询 pending（§3.2.7 §F.3）
    LaunchedEffect(deviceToken) {
        while (true) {
            runCatching {
                ServiceLocator.apiClient.accountApi.pendingBinds(deviceToken)
            }.onSuccess { resp ->
                pending = resp.pending
                // 队列模式：取第一条
                current = resp.pending.firstOrNull()
            }
            delay(10_000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BrandColor.CardWhite).padding(Spacing.Lg)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
        Text(stringResource(R.string.bind_qr_my_title), fontSize = FontSize.title(), color = BrandColor.TextPrimary)
        Spacer(Modifier.height(Spacing.Md))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(BrandColor.BgGray, RoundedCornerShape(Corner.Card)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = bindCode ?: "生成中…",
                fontSize = FontSize.title(),
                color = BrandColor.TextPrimary,
            )
        }
        Spacer(Modifier.height(Spacing.Sm))
        Text("60 秒后可重生成", fontSize = FontSize.caption(), color = BrandColor.TextSecondary)
        Button(
            onClick = { scope.launch { generateCode() } },
            colors = ButtonDefaults.buttonColors(containerColor = BrandColor.Brand500, contentColor = BrandColor.CardWhite),
            shape = RoundedCornerShape(Corner.Button),
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) { Text(stringResource(R.string.bind_qr_regenerate)) }
        Spacer(Modifier.height(Spacing.Lg))
        if (current != null) {
            Text(
                text = stringResource(R.string.bind_confirm_title, current!!.familyUserName),
                fontSize = FontSize.title(),
                color = BrandColor.TextPrimary,
            )
            Spacer(Modifier.height(Spacing.Sm))
            Text(
                text = stringResource(R.string.bind_confirm_subtitle, current!!.familyUserName),
                fontSize = FontSize.body(),
                color = BrandColor.TextSecondary,
            )
            Spacer(Modifier.height(Spacing.Md))
            OutlinedTextField(
                value = elderName,
                onValueChange = { v -> elderName = v.take(20) },
                label = { Text(stringResource(R.string.identity_elder)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.Md))
            Row {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                ServiceLocator.apiClient.accountApi.elderConfirmBind(
                                    ConfirmRequest(
                                        bindCode = current!!.bindCode,
                                        elderName = elderName.ifBlank { "爸妈" },
                                        decision = "reject",
                                    ),
                                )
                            }
                            current = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColor.BgGray, contentColor = BrandColor.TextPrimary),
                    shape = RoundedCornerShape(Corner.Button),
                    modifier = Modifier.weight(1f).height(96.dp),
                ) { Text(stringResource(R.string.bind_confirm_reject), fontSize = FontSize.body()) }
                Spacer(Modifier.padding(start = Spacing.Md))
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                ServiceLocator.apiClient.accountApi.elderConfirmBind(
                                    ConfirmRequest(
                                        bindCode = current!!.bindCode,
                                        elderName = elderName.ifBlank { "爸妈" },
                                        decision = "accept",
                                    ),
                                )
                            }.onSuccess { resp ->
                                // accept 后服务端返回 elder JWT
                                ServiceLocator.tokenStore.save(
                                    resp.token, resp.elderId, "elder", resp.elderId,
                                )
                                current = null
                                pending = emptyList()
                                bindCode = null
                                onBack()
                            }
                        }
                    },
                    enabled = elderName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandColor.Brand500, contentColor = BrandColor.CardWhite),
                    shape = RoundedCornerShape(Corner.Button),
                    modifier = Modifier.weight(1f).height(96.dp),
                ) { Text(stringResource(R.string.bind_confirm_accept), fontSize = FontSize.body()) }
            }
        }
    }
}

private val Key_Hack: Nothing = Unit
