// §3.2.1 家属主屏（PR 4 实装 —— 服务端接 ElderId 走家庭绑定）
package com.elder.android.screen.family

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun FamilyHomeScreen(
    onOpenReminders: () -> Unit,
    onOpenDiary: () -> Unit,
    onOpenBind: () -> Unit,
    onLogout: () -> Unit,
) {
    val snap by ServiceLocator.tokenStore.snapshot.collectAsState(initial = null)
    val role = snap?.role
    val reminders = remember { MutableStateFlow<List<Reminder>>(emptyList()) }
    var boundElderId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snap?.userId) {
        if (role == "family") {
            // 列出 family 创建的提醒（family 视角）
            val list = runCatching { ServiceLocator.apiClient.reminderApi.listReminders() }
                .getOrNull()
            if (list != null) reminders.value = list.reminders
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandColor.CardWhite)
            .padding(Spacing.Lg),
    ) {
        Text(
            text = if (reminders.value.isEmpty()) stringResource(R.string.family_home_unbound_title) else stringResource(R.string.family_home_bound_title),
            fontSize = FontSize.title(),
            color = BrandColor.TextPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.Md))
        Text(
            text = "${reminders.value.size} 个提醒",
            fontSize = FontSize.body(),
            color = BrandColor.TextSecondary,
        )
        Spacer(modifier = Modifier.height(Spacing.Lg))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onOpenReminders,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandColor.Brand500, contentColor = BrandColor.CardWhite,
                ),
                shape = RoundedCornerShape(Corner.Button),
                modifier = Modifier
                    .weight(1f)
                    .height(Size.PrimaryButtonHeight),
            ) { Text(stringResource(R.string.family_home_tab_reminders), fontSize = FontSize.body()) }
            Spacer(modifier = Modifier.padding(start = Spacing.Md))
            Button(
                onClick = onOpenDiary,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandColor.Aux500, contentColor = BrandColor.CardWhite,
                ),
                shape = RoundedCornerShape(Corner.Button),
                modifier = Modifier
                    .weight(1f)
                    .height(Size.PrimaryButtonHeight),
            ) { Text(stringResource(R.string.family_home_tab_diary), fontSize = FontSize.body()) }
        }
        Spacer(modifier = Modifier.height(Spacing.Lg))
        Button(
            onClick = onOpenBind,
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColor.BgGray, contentColor = BrandColor.TextPrimary,
            ),
            shape = RoundedCornerShape(Corner.Button),
            modifier = Modifier
                .fillMaxWidth()
                .height(Size.SecondaryButtonHeight),
        ) { Text(stringResource(R.string.family_home_open_camera), fontSize = FontSize.body()) }
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onLogout) {
                Text(stringResource(R.string.family_home_logout), color = BrandColor.TextSecondary)
            }
        }
    }
}
