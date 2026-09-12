// §3.1.5 老人端主屏（v3.0 MVP 版）
// 主屏元素恰好 2 个（v3.0 收窄，移除今日提醒卡；设置改为可见按钮 PR #2）
package com.elder.android.screen.elder

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing

@Composable
fun ElderHomeScreen(
    onStartDiary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecent: () -> Unit,
    vm: ElderHomeViewModel = viewModel(),
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BrandColor.CardWhite,
    ) {
        // PR #3：Column 加 windowInsetsPadding（刘海/状态栏/手势区）+ imePadding（键盘预留）
        // 三个区域用 spacedBy(Spacing.Md) + weight(0.4f/0.6f) 自适应布局，移除固定 height
        // 对应 ui-ux-pro-max：Layout & Responsive + Touch & Interaction
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md),
        ) {
            // 区域 A：问候区（weight 0.4，落在屏幕 ~40% 上半部；§3.1.5 MVP）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .semantics { testTag = "home_greeting_area" },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.greeting,
                        fontSize = FontSize.BodyHugeSp.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandColor.TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.Sm))
                    Text(
                        text = uiState.dateLine,
                        fontSize = FontSize.body(),
                        color = BrandColor.TextSecondary,
                    )
                }
                // PR #5：ASR 未配置提示从内联红字升级为独立红卡片
                // 对应 ui-ux-pro-max：Forms & Feedback（一级提示视觉权重）
                // BgGray 底 + Error500 边 + Icons.Default.Warning + 高度 ≥ 72dp
                if (uiState.showAsrHint) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.Md, vertical = Spacing.Sm)
                            .heightIn(min = Size.SecondaryButtonHeight)
                            .clickable(role = Role.Button) { onOpenSettings() }
                            .semantics { testTag = "home_asr_hint_card" },
                        shape = RoundedCornerShape(Corner.Card),
                        color = BrandColor.BgGray,
                        border = BorderStroke(Size.AsrCardBorderWidth, BrandColor.Error500),
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.Md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = BrandColor.Error500,
                                modifier = Modifier.size(Size.IconMd),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.home_asr_unconfigured),
                                    fontSize = FontSize.body(),
                                    color = BrandColor.Error500,
                                )
                                Text(
                                    text = stringResource(R.string.home_asr_open_settings),
                                    fontSize = FontSize.caption(),
                                    color = BrandColor.TextSecondary,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = BrandColor.TextSecondary,
                                modifier = Modifier.size(Size.IconMd),
                            )
                        }
                    }
                }
                // 老人端顶部动作统一为 56dp 高、图标 + 可见文字，避免纯图标不可识别。
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.Md)
                        .semantics { testTag = "home_settings_area" },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                ) {
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .height(Size.TouchTargetMin)
                            .widthIn(min = Size.TopActionMinWidth)
                            .semantics { testTag = "home_settings_button" }
                            .clip(RoundedCornerShape(Corner.Button)),
                        shape = RoundedCornerShape(Corner.Button),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandColor.BgGray,
                            contentColor = BrandColor.TextSecondary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = BrandColor.TextSecondary,
                            modifier = Modifier.size(Size.IconMd),
                        )
                        Spacer(modifier = Modifier.width(Spacing.Sm))
                        Text(
                            text = stringResource(R.string.home_settings_button),
                            fontSize = FontSize.BodySmallSp.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(
                        onClick = onOpenRecent,
                        modifier = Modifier
                            .height(Size.TouchTargetMin)
                            .widthIn(min = Size.TopActionMinWidth)
                            .semantics { testTag = "home_today_records_button" }
                            .clip(RoundedCornerShape(Corner.Button)),
                        shape = RoundedCornerShape(Corner.Button),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.todayRecorded) BrandColor.Brand500 else BrandColor.BgGray,
                            contentColor = if (uiState.todayRecorded) BrandColor.CardWhite else BrandColor.TextSecondary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = if (uiState.todayRecorded) BrandColor.CardWhite else BrandColor.TextSecondary,
                            modifier = Modifier.size(Size.IconMd),
                        )
                        Spacer(modifier = Modifier.width(Spacing.Sm))
                        Text(
                            text = stringResource(R.string.home_today_records_button),
                            fontSize = FontSize.BodySmallSp.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // 区域 C：写日记按钮区（weight 0.6，落在屏幕 ~60% 拇指舒适区）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .padding(Spacing.Md)
                    .semantics { testTag = "home_diary_area" },
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = onStartDiary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Size.HomeDiaryButton),
                    shape = RoundedCornerShape(Corner.Button),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandColor.Brand500,
                        contentColor = BrandColor.CardWhite,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.home_diary_button),
                        fontSize = FontSize.button(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

        }
    }
}
