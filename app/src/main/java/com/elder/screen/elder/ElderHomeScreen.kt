// §3.1.5 老人端主屏（PR 4 实装）—— 3 元素：问候 + 今日提醒卡 + 写日记按钮
package com.elder.android.screen.elder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
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
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import com.elder.android.di.ServiceLocator
import com.elder.android.network.dto.Reminder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ElderHomeScreen(
    onOpenRecent: () -> Unit,
    onStartDiary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReminder: (String) -> Unit,
) {
    val snap by ServiceLocator.tokenStore.snapshot.collectAsState(initial = null)
    val elderId = snap?.elderId ?: snap?.userId
    var reminders by remember { mutableStateOf<List<Reminder>>(emptyList()) }
    val df = remember { SimpleDateFormat("M 月 d 日 EEE HH:mm", Locale.CHINA) }
    val todayText = df.format(Date())

    // 隐藏设置入口：5 次连续点击问候（PRD §3.1.8）
    val tapTimes = remember { IntArray(1) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandColor.CardWhite),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Size.HomeGreetingHeight)
                    .background(BrandColor.CardWhite)
                    .padding(horizontal = Spacing.Lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            tapTimes[0]++
                            if (tapTimes[0] >= 5) {
                                tapTimes[0] = 0
                                onOpenSettings()
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = greeting(),
                        fontSize = FontSize.BodyHugeSp.sp,
                        color = BrandColor.TextPrimary,
                    )
                    Spacer(Modifier.size(Spacing.Sm))
                    Text(todayText, fontSize = FontSize.body(), color = BrandColor.TextSecondary)
                }
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.home_today_records_icon_desc),
                    modifier = Modifier
                        .size(Size.TodayRecordIcon)
                        .clickable { onOpenRecent() },
                    tint = BrandColor.TextSecondary,
                )
            }
            ReminderCard(
                reminders = reminders,
                onClick = { id -> onOpenReminder(id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Size.HomeReminderCardHeight)
                    .padding(horizontal = Spacing.Md),
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onStartDiary,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandColor.Brand500, contentColor = BrandColor.CardWhite,
                ),
                shape = RoundedCornerShape(Corner.Button),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Size.HomeDiaryButtonHeight)
                    .padding(horizontal = Spacing.Md),
            ) { Text(stringResource(R.string.home_diary_button), fontSize = FontSize.button()) }
            Spacer(Modifier.height(Spacing.Lg))
        }
    }

    LaunchedEffect(elderId) {
        if (elderId != null) {
            runCatching { ServiceLocator.apiClient.reminderApi.listReminders() }
                .onSuccess { res -> reminders = res.reminders.filter { it.elderId == elderId && it.status == "active" } }
        }
    }
}

@Composable
private fun ReminderCard(reminders: List<Reminder>, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(BrandColor.BgGray, RoundedCornerShape(Corner.Card))
            .padding(Spacing.Md)
            .clickable(enabled = reminders.isNotEmpty()) { reminders.firstOrNull()?.let { onClick(it.id) } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (reminders.isEmpty()) {
                Text(stringResource(R.string.home_no_reminders_today), fontSize = FontSize.body(), color = BrandColor.TextSecondary)
            } else {
                val first = reminders.first()
                Text(
                    text = if (first.type == "medication") "💊 该吃药啦" else "🏥 该去看医生啦",
                    fontSize = FontSize.body(),
                    color = BrandColor.TextPrimary,
                )
            }
        }
    }
}

private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 11 -> "早上好"
        hour < 14 -> "中午好"
        hour < 18 -> "下午好"
        else -> "晚上好"
    }
}
