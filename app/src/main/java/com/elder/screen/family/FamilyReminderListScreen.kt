// §3.2.2 提醒 Tab —— 列表 + 加号
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.elder.android.design.tokens.Spacing
import com.elder.android.di.ServiceLocator
import com.elder.android.network.dto.Reminder

@Composable
fun FamilyReminderListScreen(
    onAdd: () -> Unit,
    onAddAppt: () -> Unit,
    onBack: () -> Unit,
) {
    var items by remember { mutableStateOf<List<Reminder>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching { ServiceLocator.apiClient.reminderApi.listReminders() }
            .onSuccess { items = it.reminders }
    }
    Column(modifier = Modifier.fillMaxSize().background(BrandColor.CardWhite)) {
        Row(modifier = Modifier.fillMaxWidth().padding(Spacing.Lg), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.reminder_create_medication), tint = BrandColor.Brand500)
            }
            IconButton(onClick = onAddAppt) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.reminder_create_appointment), tint = BrandColor.WarmOrange)
            }
        }
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.reminder_list_empty), fontSize = FontSize.body(), color = BrandColor.TextSecondary)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.Lg)) {
                items(items) { r -> ReminderRow(r) }
            }
        }
    }
}

@Composable
private fun ReminderRow(r: Reminder) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Sm)
            .background(BrandColor.BgGray, RoundedCornerShape(Corner.Card))
            .padding(Spacing.Md),
    ) {
        Column {
            val title = if (r.type == "medication") {
                (r.payload["med_name"] as? String) ?: "服药提醒"
            } else {
                "${r.payload["hospital"]} ${r.payload["department"]}"
            }
            Text(title, fontSize = FontSize.body(), color = BrandColor.TextPrimary)
            Text(
                text = if (r.type == "medication") {
                    val sched = r.payload["schedule"] as? List<*> ?: emptyList<Any>()
                    val times = sched.join.mapNotNull { (it as? Map<*, *>)?.get("time") }.joinToString("、")
                    "每天 $times"
                } else {
                    val dt = r.payload["datetime"] as? String ?: ""
                    "$dt · 提前 ${r.payload["advance_remind_min"]} 分钟"
                },
                fontSize = FontSize.caption(),
                color = BrandColor.TextSecondary,
            )
        }
    }
}
