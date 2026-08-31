// 对应 prd.md §3.1.5 老人端主屏
// 3 元素（问候 + 今日提醒卡 + 写日记按钮），PR 1 全部空态/未绑定文案
// 不接后端、不发请求、不调任何 SDK。
package com.elder.android.screen

import androidx.compose.foundation.Image
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandColor.CardWhite),
    ) {
        HomeGreetingArea(modifier = Modifier.height(Size.HomeGreetingHeight))
        HomeReminderCard(
            text = stringResource(R.string.home_no_reminders_today),
            modifier = Modifier
                .fillMaxWidth()
                .height(Size.HomeReminderCardHeight),
        )
        HomeDiaryButton(
            text = stringResource(R.string.home_diary_button),
            modifier = Modifier
                .fillMaxWidth()
                .height(Size.HomeDiaryButtonHeight),
        )
    }
}

@Composable
private fun HomeGreetingArea(modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BrandColor.CardWhite)
            .padding(horizontal = Spacing.Lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.clickable { HomeHiddenSettingTrigger.onTap() },
            ) {
                Text(
                    text = stringResource(R.string.home_greeting_morning),
                    color = BrandColor.TextPrimary,
                    fontSize = FontSize.BodyHugeSp.sp,
                )
            }
            Spacer(modifier = Modifier.size(Spacing.Sm))
            Text(
                text = "8 月 27 日 周三 09:12",
                color = BrandColor.TextSecondary,
                fontSize = FontSize.body(),
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_bell_outline),
            contentDescription = stringResource(R.string.home_today_records_icon_desc),
            modifier = Modifier.size(Size.TodayRecordIcon),
        )
    }
}

@Composable
private fun HomeReminderCard(text: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .background(
                color = BrandColor.BgGray,
                shape = RoundedCornerShape(Corner.Card),
            )
            .padding(Spacing.Md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = BrandColor.TextSecondary,
            fontSize = FontSize.body(),
        )
    }
}

@Composable
private fun HomeDiaryButton(text: String, modifier: Modifier) {
    Button(
        onClick = {},
        enabled = false,
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandColor.Brand500,
            contentColor = Color.White,
            disabledContainerColor = BrandColor.Brand500.copy(alpha = 0.6f),
            disabledContentColor = Color.White,
        ),
        shape = RoundedCornerShape(Corner.Button),
        modifier = modifier.padding(horizontal = Spacing.Md),
    ) {
        Text(
            text = text,
            fontSize = FontSize.button(),
        )
    }
}
