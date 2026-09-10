// §3.1.5 老人端主屏（v3.0 MVP 版）
// 主屏元素恰好 2 个（v3.0 收窄，移除今日提醒卡；设置改为可见按钮 PR #2）
package com.elder.android.screen.elder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
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
                    if (uiState.showAsrHint) {
                        Spacer(modifier = Modifier.height(Spacing.Sm))
                        Text(
                            text = stringResource(R.string.home_asr_unconfigured),
                            fontSize = FontSize.BodySmallSp.sp,
                            color = BrandColor.Error500,
                            modifier = Modifier
                                .clickable { onOpenSettings() }
                                .padding(Spacing.Xs),
                        )
                    }
                }
                // 今日记录图标（右上 32dp；§3.1.7）— 点击进时间轴屏
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.Md)
                        .size(Size.TodayRecordIcon)
                        .clip(CircleShape)
                        .background(if (uiState.todayRecorded) BrandColor.Brand500 else Color.Transparent)
                        .clickable { onOpenRecent() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.home_today_records_icon_desc),
                        tint = if (uiState.todayRecorded) Color.White else BrandColor.TextSecondary,
                        modifier = Modifier.size(Size.IconMd),
                    )
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
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.home_diary_button),
                        fontSize = FontSize.button(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // 区域 D：设置入口（PR #2 §3.1.8：可见按钮替代 5 次点击隐藏手势）
            // PR #3：wrapContentHeight 依赖自然高度，不参与 weight 比例分配
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = Spacing.Md, vertical = Spacing.Xs)
                    .semantics { testTag = "home_settings_area" },
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Size.SecondaryButtonHeight),
                    shape = RoundedCornerShape(Corner.Button),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandColor.BgGray,
                        contentColor = BrandColor.TextSecondary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(Size.IconMd),
                    )
                    Spacer(modifier = Modifier.width(Spacing.Sm))
                    Text(
                        text = stringResource(R.string.home_settings_button),
                        fontSize = FontSize.body(),
                    )
                }
            }
        }
    }
}
