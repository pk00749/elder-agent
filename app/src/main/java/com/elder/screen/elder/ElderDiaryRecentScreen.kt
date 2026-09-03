// §3.1.7 今日记录屏 —— 今天 / 近7天 双选 + 时间轴列表
package com.elder.android.screen.elder

import androidx.compose.foundation.background
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
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Spacing
import com.elder.android.di.ServiceLocator
import com.elder.android.network.dto.Diary

@Composable
fun ElderDiaryRecentScreen(onBack: () -> Unit) {
    var range by remember { mutableStateOf("today") }
    var diaries by remember { mutableStateOf<List<Diary>>(emptyList()) }
    val snap by ServiceLocator.tokenStore.snapshot.collectAsState(initial = null)
    val elderId = snap?.elderId ?: snap?.userId

    LaunchedEffect(range, elderId) {
        if (elderId != null) {
            runCatching {
                ServiceLocator.apiClient.accountApi.listDiaries(elderId = elderId)
            }.onSuccess { diaries = it.diaries }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BrandColor.CardWhite)) {
        Row(modifier = Modifier.fillMaxWidth().padding(Spacing.Lg), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
            Text(stringResource(R.string.diary_recent_title), fontSize = FontSize.title(), color = BrandColor.TextPrimary)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Lg)) {
            listOf("today" to R.string.diary_recent_today_tab, "7d" to R.string.diary_recent_7d_tab).forEach { (key, label) ->
                Button(
                    onClick = { range = key },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (range == key) BrandColor.Brand500 else BrandColor.BgGray,
                        contentColor = if (range == key) BrandColor.CardWhite else BrandColor.TextPrimary,
                    ),
                    shape = RoundedCornerShape(Corner.Button),
                    modifier = Modifier.padding(end = Spacing.Sm),
                ) { Text(stringResource(label)) }
            }
        }
        Spacer(Modifier.height(Spacing.Md))
        if (diaries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (range == "today") stringResource(R.string.diary_list_empty_today) else stringResource(R.string.diary_list_empty_7d),
                    fontSize = FontSize.body(),
                    color = BrandColor.TextSecondary,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.Lg)) {
                items(diaries) { d ->
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Sm).background(BrandColor.BgGray, RoundedCornerShape(Corner.Card)).padding(Spacing.Md),
                    ) {
                        Column {
                            Text(d.date, fontSize = FontSize.caption(), color = BrandColor.TextSecondary)
                            Spacer(Modifier.height(Spacing.Xs))
                            Text(d.summary, fontSize = FontSize.body(), color = BrandColor.TextPrimary)
                        }
                    }
                }
            }
        }
    }
}
