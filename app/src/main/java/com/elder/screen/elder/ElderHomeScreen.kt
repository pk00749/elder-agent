// §3.1.5 老人端主屏（v3.0 MVP 版）
// 主屏元素恰好 2 个（v3.0 收窄，移除今日提醒卡；隐藏设置入口不计）
package com.elder.android.screen.elder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
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
        Column(modifier = Modifier.fillMaxSize()) {
            // 区域 A：问候（高 240dp，v3.0 收窄后单元素独占；§3.1.5 MVP）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { vm.onGreetingTap() })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = uiState.greeting,
                        fontSize = 40.sp,
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
                            text = "请在设置 → AI 语音识别 配置 API",
                            fontSize = FontSize.body(),
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
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (uiState.todayRecorded) BrandColor.Brand500 else Color.Transparent)
                        .clickable { onOpenRecent() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "今日记录",
                        tint = if (uiState.todayRecorded) Color.White else BrandColor.TextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // 区域 C：写日记按钮（高 200dp，v3.0 占满）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(Spacing.Md),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = onStartDiary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(Corner.Button),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandColor.Brand500,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = "✎  点击开始写日志",
                        fontSize = FontSize.button(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    // 隐藏入口触发后跳设置
    LaunchedEffect(uiState.settingsTrigger) {
        if (uiState.settingsTrigger) {
            vm.consumeSettingsTrigger()
            onOpenSettings()
        }
    }
}
