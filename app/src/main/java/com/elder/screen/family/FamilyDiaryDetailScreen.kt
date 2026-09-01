// §3.2.6 日志详情 —— 文字卡 + 时间轴分段播放
package com.elder.android.screen.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Spacing
import com.elder.android.di.ServiceLocator
import com.elder.android.network.dto.DiaryDetailResponse

@Composable
fun FamilyDiaryDetailScreen(diaryId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var detail by remember = { mutableStateOf<DiaryDetailResponse?>(null) }
    var error by remember = { mutableStateOf<String?() null) }
    val tts = ServiceLocator.ttsPlayer

    LaunchedEffect(diaryId) {
        runCatching { ServiceLocator.apiClient.accountApi.getDiary(diaryId) }
            .onSuccess { detail = it }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BrandColor.CardWhite).padding(Spacing.Lg).verticalScroll(rememberScrollState()),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
        detail?.let { d ->
            Text(d.date, fontSize = FontSize.caption(), color = BrandColor.TextSecondary)
            Spacer(Modifier.height(Spacing.Md))
            Text(d.text, fontSize = FontSize.body(), color = BrandColor.TextPrimary, lineHeight = FontSize.body().value.sp * 1.6f)
            Spacer(Modifier.height(Spacing.Lg))
            Text("「${d.summary}」", fontSize = FontSize.body(), color = BrandColor.TextSecondary)
            Spacer(Modifier.height(Spacing.Lg))
            d.audioSegments.forEach { seg ->
                val url = seg["audio_url"] as? String ?: return@forEach
                val asr = seg["asr_text"] as? String ?: ""
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Sm).background(BrandColor.BgGray, RoundedCornerShape(Corner.Card)).padding(Spacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(asr, fontSize = FontSize.caption(), color = BrandColor.TextPrimary)
                    }
                    Button(
                        onClick = {
                            runCatching { tts.play(context, url) }
                                .onFailure { error = "听不了，请稍后再试" }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandColor.Brand500, contentColor = BrandColor.CardWhite),
                        shape = RoundedCornerShape(Corner.Button),
                    ) { Text("▶") }
                }
            }
        }
        error?.let { Text(it, color = BrandColor.Error500) }
    }
}
