// §3.1.1 / §3.1.3 服药 / 就医全屏卡 —— 大字号 + 准备清单 + 知道了
package com.elder.android.screen.en

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import com.elder.android.di.ServiceLocator
import com.elder.android.network.dto.AckRequest
import com.elder.android.network.dto.Reminder
import com.elder.android.network.dto.ReminderListResponse
import kotlinx.coroutines.launch

@Composable
fun ElderReminderFullScreen(reminderId: String, onBack: () -> Unit) {
    var reminder by remember { mutableStateOf<Reminder?() null) }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val tts = ServiceLocator.ttsPlayer

    LaunchedEffect(reminderId) {
        runCatching { ServiceLocator.apiClient.reminderApi.listReminders() }
            .onSuccess { res: ReminderListResponse -> reminder = res.reminders.firstOrNull { it.id == reminderId } }
    }

    val r = reminder
    Box(modifier = Modifier.fillMaxSize().background(BrandColor.CardWhite).padding(Spacing.Lg)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (r?.type == "medication") "💊" else "🏥",
                fontSize = FontSize.title(),
                color = BrandColor.WarmOrange,
            )
            Spacer(Modifier.height(Spacing.Md))
            Text(
                text = if (r?.type == "medication") stringResource(R.string.reminder_med_full_title) else stringResource(R.string.reminder_appt_full_title),
                fontSize = FontSize.title(),
                color = BrandColor.TextPrimary,
            )
            Spacer(Modifier.height(Spacing.Md))
            if (r != null) {
                if (r.type == "medication") {
                    Text(
                        text = "${r.payload["med_name"]}（${r.payload["dosage"]?.let { (it as Map<*, *>)["type"] }}）",
                        fontSize = FontSize.body(),
                        color = BrandColor.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = "${r.payload["hospital"]} · ${r.payload["department"]} · ${r.payload["datetime"]}",
                        fontSize = FontSize.body(),
                        color = BrandColor.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                (r.payload["note"] as? String)?.let {
                    Spacer(Modifier.height(Spacing.Md))
                    Text(
                        text = "准备：$it",
                        fontSize = FontSize.body(),
                        color = BrandColor.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.Lg),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Button(
                onClick = {
                    if (r != null) {
                        scope.launch {
                            runCatching {
                                ServiceLocator.apiClient.reminderApi.ackReminder(
                                    id = r.id,
                                    body = AckRequest(action = "taken"),
                                )
                            }
                            onBack()
                        }
                    } else onBack()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandColor.Brand500, contentColor = BrandColor.CardWhite,
                ),
                shape = RoundedCornerShape(Corner.Button),
                modifier = Modifier.fillMaxWidth().height(Size.PrimaryButtonHeight),
            ) { Text(stringResource(R.string.reminder_full_ack), fontSize = FontSize.body()) }
            Spacer(Modifier.height(Spacing.Sm))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.common_back))
                }
            }
        }
    }
}
