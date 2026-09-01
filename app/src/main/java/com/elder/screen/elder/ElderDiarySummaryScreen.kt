// §3.1.6 访谈总结 —— text 卡 + summary + 保存 / 改一下
package com.elder.android.screen.elder

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import com.elder.android.di.ServiceLocator
import com.elder.android.network.dto.AgentFinalizeRequest

@Composable
fun ElderDiarySummaryScreen(
    sessionId: String,
    onDone: () -> Unit,
    onEdit: () -> Unit,
) {
    var text by remember { mutableStateOf<String?() null) }
    var summary by remember { mutableStateOf<String?() null) }
    var diaryId by remember { mutableStateOf<String?() null) }

    LaunchedEffect(sessionId) {
        runCatching {
            ServiceLocator.apiClient.agentApi.finalize(
                sessionId,
                AgentFinalizeRequest(
                    turnNo = 99,
                    elderText = "（写好了）",
                    elderAudioCosKey = "tmp/$sessionId/finalize.m4a",
                ),
            )
        }.onSuccess { resp ->
            text = resp.text
            summary = resp.summary
            diaryId = resp.diaryId
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BrandColor.CardWhite).padding(Spacing.Lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.diary_summary_title),
            fontSize = FontSize.title(),
            color = BrandColor.TextPrimary,
        )
        Spacer(Modifier.height(Spacing.Lg))
        Box(
            modifier = Modifier.fillMaxWidth().background(BrandColor.BgGray, RoundedCornerShape(Corner.Card)).padding(Spacing.Lg),
        ) {
            Text(
                text = text ?: "…",
                fontSize = FontSize.body(),
                color = BrandColor.TextPrimary,
                lineHeight = FontSize.body().value.sp * 1.6f,
            )
        }
        Spacer(Modifier.height(Spacing.Md))
        Text(
            text = "「${summary ?: "…"}」",
            fontSize = FontSize.body(),
            color = BrandColor.TextSecondary,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = BrandColor.Brand500, contentColor = BrandColor.CardWhite),
            shape = RoundedCornerShape(Corner.Button),
            modifier = Modifier.fillMaxWidth().height(Size.PrimaryButtonHeight),
        ) { Text(stringResource(R.string.diary_summary_save), fontSize = FontSize.body()) }
        Spacer(Modifier.height(Spacing.Sm))
        Button(
            onClick = onEdit,
            colors = ButtonDefaults.buttonColors(containerColor = BrandColor.BgGray, contentColor = BrandColor.TextPrimary),
            shape = RoundedCornerShape(Corner.Button),
            modifier = Modifier.fillMaxWidth().height(Size.SecondaryButtonHeight),
        ) { Text(stringResource(R.string.diary_summary_edit), fontSize = FontSize.body()) }
    }
}

private val Arrangement = androidx.compose.foundation.layout.Arrangement
