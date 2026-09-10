// §3.1.8 老人端设置（隐藏入口，v3.0 MVP 版）
package com.elder.android.screen.elder

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elder.android.R
import com.elder.android.data.db.FontScale
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElderSettingsScreen(
    onBack: () -> Unit,
    onOpenAsr: () -> Unit,
    onLoggedOut: () -> Unit,
    vm: ElderSettingsViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = BrandColor.CardWhite) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontSize = FontSize.TitleDefaultSp.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandColor.CardWhite),
            )

            Column(modifier = Modifier.fillMaxSize()) {
                SettingRow1Asr(configured = state.asrConfigured, onClick = onOpenAsr)
                Divider()
                SettingRow2FontScale(current = state.fontScale, onPick = vm::setFontScale)
                Divider()
                SettingRow3Tts(enabled = state.ttsEnabled, onChange = vm::setTtsEnabled)
                Divider()
                SettingRow4Volume(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                Divider()
                SettingRow5About(versionName = state.versionName)
                Divider()
                SettingRow6Logout(onClick = vm::requestLogout)
            }
        }
    }

    if (state.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = vm::cancelLogout,
            title = { Text(stringResource(R.string.settings_logout), fontSize = FontSize.body(), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.settings_logout_warning),
                    fontSize = FontSize.body(),
                    color = BrandColor.TextPrimary,
                )
            },
            confirmButton = {
                TextButton(onClick = vm::confirmLogout) {
                    Text(stringResource(R.string.settings_logout_confirm_button), color = BrandColor.Error500, fontSize = FontSize.body())
                }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelLogout) {
                    Text(stringResource(R.string.common_cancel), color = BrandColor.TextSecondary, fontSize = FontSize.body())
                }
            },
        )
    }
}

@Composable
private fun SettingRow1Asr(configured: Boolean, onClick: () -> Unit) {
    SettingRow(
        title = stringResource(R.string.settings_asr),
        subtitle = if (configured) stringResource(R.string.settings_asr_configured) else stringResource(R.string.settings_asr_not_configured),
        onClick = onClick,
        trailing = {
        if (!configured) RedDot()
    })
}

@Composable
private fun SettingRow2FontScale(current: FontScale, onPick: (FontScale) -> Unit) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(Spacing.Md)) {
        Text(stringResource(R.string.settings_font_size), fontSize = FontSize.body(), color = BrandColor.TextPrimary)
        Spacer(modifier = Modifier.height(Spacing.Sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
            listOf(FontScale.DEFAULT, FontScale.LARGE, FontScale.XLARGE).forEach { f ->
                FilterChip(
                    selected = current == f,
                    onClick = { onPick(f) },
                    label = { Text(labelOf(f), fontSize = FontSize.BodySmallSp.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandColor.Brand500,
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }
    }
}

private fun labelOf(f: FontScale) = when (f) {
    FontScale.DEFAULT -> "默认"
    FontScale.LARGE -> "大"
    FontScale.XLARGE -> "特大"
}

@Composable
private fun SettingRow3Tts(enabled: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(
        title = stringResource(R.string.settings_tts_switch),
        subtitle = if (enabled) stringResource(R.string.settings_on) else stringResource(R.string.settings_off),
        onClick = {},
        trailing = {
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BrandColor.Brand500,
                ),
            )
        },
    )
}

@Composable
private fun SettingRow4Volume(onClick: () -> Unit) {
    SettingRow(title = stringResource(R.string.settings_volume), subtitle = stringResource(R.string.settings_volume_hint), onClick = onClick)
}

@Composable
private fun SettingRow5About(versionName: String) {
    SettingRow(title = stringResource(R.string.settings_about), subtitle = stringResource(R.string.settings_version, versionName), onClick = {})
}

@Composable
private fun SettingRow6Logout(onClick: () -> Unit) {
    SettingRow(title = stringResource(R.string.settings_logout), subtitle = stringResource(R.string.settings_logout_subtitle), onClick = onClick)
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Size.ListRowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = FontSize.body(), color = BrandColor.TextPrimary)
            Text(subtitle, fontSize = FontSize.caption(), color = BrandColor.TextSecondary)
        }
        trailing?.invoke()
    }
}

@Composable
private fun Divider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = Spacing.Md),
        color = BrandColor.BgGray,
    )
}

@Composable
private fun RedDot() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(Size.WarningDotSize)
            .clip(CircleShape)
            .background(BrandColor.Error500),
    )
}
