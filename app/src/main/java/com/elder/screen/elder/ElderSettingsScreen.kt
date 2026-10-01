// §3.1.8 老人端设置（主屏可见入口，v3.0 MVP 版 + v0.8.1 整改）
// v0.8.1 整改：
//   - ASR 入口卡副标题加 LLM 信息（之前只有 ASR + TTS）
//   - 跳系统音量设置加 try-catch（部分定制 ROM 会抛 ActivityNotFoundException）
//   - 退出登录 dialog 改用老人版文案（老人端没有"接收提醒"概念）
package com.elder.android.screen.elder

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import com.elder.android.ui.component.ElderToast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elder.android.R
import com.elder.android.data.db.FontScale
import com.elder.android.data.export.SaveMode
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.FontLevel
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElderSettingsScreen(
    onBack: () -> Unit,
    onOpenAsr: () -> Unit,
    onOpenOss: () -> Unit = {},  // v0.10.0 §6 入口卡回调;默认空(便于 preview / 旧测试桩)
    onLoggedOut: () -> Unit,
    vm: ElderSettingsViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()

    // v0.11.x UI agent M-1:音量跳转失败用 ElderToast 替代 android.widget.Toast
    // 用本地 remember state(而非 VM state.topError),保持本 commit 只动 Screen
    var volumeError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = BrandColor.CardWhite) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontSize = FontSize.TitleDefaultSp.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(Size.TouchTargetMin),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandColor.CardWhite),
            )

            // v0.11.x bugfix: 10 settings rows + dividers 超出视口,无 verticalScroll 让 退出登录 按钮被截掉
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                SettingRow1Asr(
                    configured = state.asrConfigured,
                    asrProviderLabel = state.asrProviderLabel,
                    ttsProviderLabel = state.ttsProviderLabel,
                    llmProviderLabel = state.llmProviderLabel,   // v0.8.1 新增
                    onClick = onOpenAsr,
                )
                Divider()
                // v0.10.0 §6: 同步到云入口卡
                SettingRowOss(
                    configured = state.ossConfigured,
                    bucket = state.ossBucket,
                    onClick = onOpenOss,
                )
                Divider()
                // v0.11.0 §3.1: 保存方式选择卡(点击弹 dialog;默认 LOCAL)
                SettingRowSaveMode(
                    current = state.saveMode,
                    onClick = vm::showSaveModeDialog,
                )
                Divider()
                SettingRow2FontScale(current = state.fontScale, onPick = vm::setFontScale)
                Divider()
                SettingRow3Tts(enabled = state.ttsEnabled, onChange = vm::setTtsEnabled)
                Divider()
                SettingRow4Volume(onClick = {
                    // v0.8.1 整改：try-catch 包装（MIUI / ColorOS 部分版本无 ACTION_SOUND_SETTINGS）
                    // v0.11.x UI agent M-1:失败提示走 ElderToast(prd §4.8),不再用 android.widget.Toast
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_SOUND_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure {
                        volumeError = ctx.getString(R.string.settings_volume_no_app)
                    }
                })
                Divider()
                SettingRow5About(versionName = state.versionName)
                Spacer(modifier = Modifier.height(Spacing.Lg))
                Divider()
                SettingRow6Logout(onClick = vm::requestLogout)
            }
        }
    }

    // v0.8.1 整改：退出登录失败等异常通过 ElderToast 兜底（之前会被吞掉）
    if (!state.topError.isNullOrBlank()) {
        ElderToast(message = state.topError, onDismiss = vm::dismissError)
    }

    // v0.11.x UI agent M-1:音量跳转失败的本地 ElderToast
    if (!volumeError.isNullOrBlank()) {
        ElderToast(message = volumeError, onDismiss = { volumeError = null })
    }

    if (state.showSaveModeDialog) {
        SaveModeDialog(
            current = state.saveMode,
            onPick = vm::setSaveMode,
            onDismiss = vm::dismissSaveModeDialog,
        )
    }

    if (state.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = vm::cancelLogout,
            title = { Text(stringResource(R.string.settings_logout), fontSize = FontSize.body(), fontWeight = FontWeight.Bold) },
            // v0.8.1 整改：老人端用专属文案（"接收提醒"是家属端概念，老人听不懂）
            text = {
                Text(
                    stringResource(R.string.settings_logout_warning_elder),
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
private fun SettingRow1Asr(
    configured: Boolean,
    asrProviderLabel: String,
    ttsProviderLabel: String,
    llmProviderLabel: String,   // v0.8.1 新增
    onClick: () -> Unit,
) {
    SettingRow(
        title = stringResource(R.string.settings_asr),
        // v0.8.1 整改：副标题加 LLM 信息
        subtitle = if (configured) stringResource(
            R.string.settings_asr_summary_v2,
            asrProviderLabel,
            ttsProviderLabel,
            llmProviderLabel,
        ) else stringResource(R.string.settings_asr_not_configured),
        onClick = onClick,
        trailing = {
            if (!configured) RedDot()
        },
    )
}

/**
 * v0.11.0 §3.1: 保存方式入口卡 — 显示当前模式 + 一行说明;点击弹 dialog。
 */
@Composable
private fun SettingRowSaveMode(current: SaveMode, onClick: () -> Unit) {
    val (titleRes, summaryRes) = when (current) {
        SaveMode.LOCAL -> R.string.settings_save_mode to R.string.settings_save_mode_summary_local
        SaveMode.CLOUD -> R.string.settings_save_mode to R.string.settings_save_mode_summary_cloud
        SaveMode.BOTH -> R.string.settings_save_mode to R.string.settings_save_mode_summary_both
    }
    SettingRow(
        title = stringResource(titleRes),
        subtitle = stringResource(summaryRes),
        onClick = onClick,
        trailing = {
            // 当前模式文字标签(local / cloud / both)
            val tagRes = when (current) {
                SaveMode.LOCAL -> R.string.settings_save_mode_local
                SaveMode.CLOUD -> R.string.settings_save_mode_cloud
                SaveMode.BOTH -> R.string.settings_save_mode_both
            }
            Text(
                text = stringResource(tagRes),
                fontSize = FontSize.body(),
                color = BrandColor.Brand500,
            )
        },
    )
}

/**
 * v0.11.0 §3.1: 保存方式 dialog — 三选一单选。
 */
@Composable
private fun SaveModeDialog(
    current: SaveMode,
    onPick: (SaveMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.settings_save_mode_dialog_title),
                fontSize = FontSize.body(FontLevel.LARGE),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.settings_save_mode_dialog_intro),
                    fontSize = FontSize.body(),
                    color = BrandColor.TextPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.Md))
                SaveMode.entries.forEach { mode ->
                    SaveModeRadio(
                        mode = mode,
                        selected = mode == current,
                        onClick = { onPick(mode) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = BrandColor.TextSecondary, fontSize = FontSize.body())
            }
        },
    )
}

@Composable
private fun SaveModeRadio(mode: SaveMode, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onClick,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                selectedColor = BrandColor.Brand500,
                unselectedColor = BrandColor.TextSecondary,
            ),
        )
        Spacer(modifier = Modifier.width(Spacing.Sm))
        Column {
            Text(
                text = when (mode) {
                    SaveMode.LOCAL -> stringResource(R.string.settings_save_mode_local)
                    SaveMode.CLOUD -> stringResource(R.string.settings_save_mode_cloud)
                    SaveMode.BOTH -> stringResource(R.string.settings_save_mode_both)
                },
                fontSize = FontSize.body(),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = BrandColor.TextPrimary,
            )
            Text(
                text = when (mode) {
                    SaveMode.LOCAL -> stringResource(R.string.settings_save_mode_summary_local)
                    SaveMode.CLOUD -> stringResource(R.string.settings_save_mode_summary_cloud)
                    SaveMode.BOTH -> stringResource(R.string.settings_save_mode_summary_both)
                },
                fontSize = FontSize.BodySmallSp.sp,
                color = BrandColor.TextSecondary,
            )
        }
    }
}

@Composable
private fun SettingRow2FontScale(current: FontScale, onPick: (FontScale) -> Unit) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(Spacing.Md)) {
        Text(stringResource(R.string.settings_font_size), fontSize = FontSize.body(), color = BrandColor.TextPrimary)
        Spacer(modifier = Modifier.height(Spacing.Sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        ) {
            listOf(FontScale.DEFAULT, FontScale.LARGE, FontScale.XLARGE).forEach { f ->
                FilterChip(
                    selected = current == f,
                    onClick = { onPick(f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(Size.SegmentButtonHeight),
                    label = { Text(labelOf(f), fontSize = FontSize.BodySmallSp.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandColor.Brand500,
                        selectedLabelColor = BrandColor.CardWhite,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.Sm))
        Text(
            text = stringResource(R.string.settings_font_preview),
            fontSize = FontSize.body(),
            color = BrandColor.TextSecondary,
        )
    }
}

@Composable
private fun labelOf(f: FontScale) = when (f) {
    FontScale.DEFAULT -> stringResource(R.string.settings_font_normal)
    FontScale.LARGE -> stringResource(R.string.settings_font_large)
    FontScale.XLARGE -> stringResource(R.string.settings_font_xlarge)
}

@Composable
private fun SettingRow3Tts(enabled: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(
        title = stringResource(R.string.settings_tts_switch),
        subtitle = stringResource(R.string.settings_tts_hint),
        trailing = {
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                enabled = true,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BrandColor.CardWhite,
                    checkedTrackColor = BrandColor.Brand500,
                ),
            )
        },
        onClick = null,
    )
}

@Composable
private fun SettingRow4Volume(onClick: () -> Unit) {
    SettingRow(title = stringResource(R.string.settings_volume), subtitle = stringResource(R.string.settings_volume_hint), onClick = onClick)
}

@Composable
private fun SettingRow5About(versionName: String) {
    SettingRow(title = stringResource(R.string.settings_about), subtitle = stringResource(R.string.settings_version, versionName))
}

@Composable
private fun SettingRow6Logout(onClick: () -> Unit) {
    SettingRow(
        title = stringResource(R.string.settings_logout),
        subtitle = stringResource(R.string.settings_logout_subtitle),
        onClick = onClick,
        danger = true,
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Size.ListRowMinHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = FontSize.body(),
                color = if (danger) BrandColor.Error500 else BrandColor.TextPrimary,
            )
            Text(subtitle, fontSize = FontSize.caption(), color = BrandColor.TextSecondary)
        }

        trailing?.invoke()
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (danger) BrandColor.Error500 else BrandColor.TextSecondary,
                modifier = Modifier.size(Size.IconMd),
            )
        }

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

// v0.10.0 §6: 同步到云入口卡;未配置显示红点 + 副文案「未配置」;已配置显示 Bucket 名
@Composable
private fun SettingRowOss(
    configured: Boolean,
    bucket: String,
    onClick: () -> Unit,
) {
    SettingRow(
        title = stringResource(R.string.oss_sync_card),
        subtitle = if (configured) {
            stringResource(R.string.oss_sync_subtitle_configured, bucket)
        } else {
            stringResource(R.string.oss_sync_subtitle_unconfigured)
        },
        trailing = if (!configured) { { RedDot() } } else null,
        onClick = onClick,
    )
}
