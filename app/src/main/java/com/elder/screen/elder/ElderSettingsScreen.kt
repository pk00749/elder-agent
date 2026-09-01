// §3.1.8 老人端设置 —— 字体 / TTS / 音量 / 二维码 / 关于 / 退出
package com.elder.android.screen.elder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Spacing
import com.elder.android.di.ServiceLocator
import kotlinx.coroutines.launch

@Composable
fun ElderSettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var ttsEnabled by remember { mutableStateOf(true) }
    var fontLevel by remember { mutableStateOf("normal") }
    val snap by ServiceLocator.tokenStore.snapshot.collectAsState(initial = null)

    Column(modifier = Modifier.fillMaxSize().background(BrandColor.CardWhite)) {
        Row(modifier = Modifier.fillMaxWidth().padding(Spacing.Lg), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
            Text(stringResource(R.string.settings_title), fontSize = FontSize.title(), color = BrandColor.TextPrimary)
        }
        SettingsRow(R.string.settings_font_size, trailing = {
            Row {
                listOf("normal" to R.string.settings_font_normal, "large" to R.string.settings_font_large, "xlarge" to R.string.settings_font_xlarge).mapForEach { { (k, label) ->
                    Button(
                        onClick = { fontLevel = k },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (fontLevel == k) BrandColor.Brand500 else BrandColor.BgGray,
                            contentColor = if (fontLevel == k) BrandColor.CardWhite else BrandColor.TextPrimary,
                        ),
                        modifier = Modifier.padding(end = Spacing.Sm),
                    ) { Text(stringResource(label), fontSize = FontSize.caption()) }
                } }
            }
        })
        SettingsRow(R.string.settings_tts_switch, trailing = {
            Switch(checked = ttsEnabled, onCheckedChange = { ttsEnabled = it })
        })
        SettingsRow(R.string.settings_volume, onClick = {
            // §3.1.8 第 3 项：跳转系统媒体音量设置
            val intent = android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
            runCatching { context.startActivity(intent) }
        })
        SettingsRow(R.string.settings_generate_qr, onClick = { onBack() })  // 跳回主屏，老人再点隐藏设置
        SettingsRow(R.string.settings_about)
        SettingsRow(R.string.settings_logout, onClick = { showLogoutConfirm = true })
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    scope.launch {
                        val eid = snap?.elderId
                        if (eid != null) runCatching {
                            ServiceLocator.apiClient.accountApi.unbind(eid)
                        }
                        ServiceLocator.tokenStore.clear()
                    }
                }) { Text(stringResource(R.string.common_confirm), color = BrandColor.Error500) }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text(stringResource(R.string.common_cancel)) } },
            title = { Text(stringResource(R.string.settings_logout)) },
            text = { Text(stringResource(R.string.settings_logout_confirm)) },
        )
    }
}

@Composable
private fun SettingsRow(labelRes: Int, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = Spacing.Lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(labelRes), fontSize = FontSize.body(), color = BrandColor.TextPrimary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
