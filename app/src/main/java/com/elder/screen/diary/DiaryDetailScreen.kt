// 对应 docs/v0.10.0.md §7：DiaryDetailScreen 日记详情页（Android Compose 实现，对齐浅色版 React Prompt）。
//
// 布局结构（间距/字号/圆角/组件树按 §7 设计合约;颜色按 DiaryColorScheme）：
//   ┌──────────────────────────────────────────┐
//   │ TopBar 56dp: ← 返回    share search ⋮  │
//   ├──────────────────────────────────────────┤
//   │ 标题 32sp/700 #1A1A1C                   │
//   │ 元信息 15sp #8A8A8E: 9月19日 13:50 · 共 139 字 · 未分类 ▾ │
//   │                                          │
//   │ ┌─ 语音附件卡 (#FFFFFF, 22dp 圆角) ─┐   │
//   │ │ 录音 1           ▶  🔴  (34dp 圆钮) │   │
//   │ │ 00:00 / 01:00                     │   │
//   │ │ ━━━━━━━━●━━━  (3dp 进度条)        │   │
//   │ │ ─────────────                    │   │
//   │ │ 转写文本 (14sp/1.75, clamp 6 行,  │   │
//   │ │ bottom mask → #FFFFFF)             │   │
//   │ └────────────────────────────────────┘   │
//   │                                          │
//   │ 正文 17sp/1.8 #1A1A1C                    │
//   ├──────────────────────────────────────────┤
//   │ BottomBar 64dp + safe-area:               │
//   │   ✨   ✓   📷   ⊕   🎙  (5 深色描边图标) │
//   └──────────────────────────────────────────┘
//
// 4 处必须翻的（来自浅色版 Prompt）：
//   1. 次要色 onSurfaceVariant=#8A8A8E 必须浅于正文 onSurface=#1A1A1C → DiaryColor 已翻转
//   2. 图标深色描边 strokeWidth=1.8 → 不准半透明灰 → 用 IconStroke
//   3. 遮罩终点 textMaskEnd=#FFFFFF(浅色)→ transparent(暗色)→ DiaryColor 已翻转
//   4. 阴影 surfaceShadowAlpha=0.05f(浅色)→ 0f(暗色)→ DiaryColor 已翻转
//
// 不动：间距 / 字号 / 圆角 / 组件树（沿用 Spacing / FontSize / Corner）。
package com.elder.android.screen.diary

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elder.android.R
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.DiaryColor
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryDetailScreen(
    diaryId: Long,
    onBack: () -> Unit,
    vm: DiaryDetailViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = DiaryColor.current()

    LaunchedEffect(diaryId) { vm.load(diaryId) }

    LaunchedEffect(state.playError) {
        state.playError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = { },  // 顶部无标题;标题在内容区
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(Size.BackButtonHeight),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.diary_detail_back),
                            tint = c.iconStroke,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { Toast.makeText(context, R.string.diary_detail_feature_wip, Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.diary_detail_share), tint = c.iconStroke)
                    }
                    IconButton(onClick = { Toast.makeText(context, R.string.diary_detail_feature_wip, Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.diary_detail_search), tint = c.iconStroke)
                    }
                    IconButton(onClick = { Toast.makeText(context, R.string.diary_detail_feature_wip, Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.diary_detail_overflow), tint = c.iconStroke)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background),
            )
        },
        bottomBar = {
            BottomToolbar(c = c, context = context)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.Lg)  // 内容区左右 20dp
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.Lg),
        ) {
            Spacer(modifier = Modifier.height(Spacing.Sm))

            // 标题 32sp/700
            Text(
                text = state.title,
                fontSize = 32.sp,
                fontWeight = FontWeight(700),
                color = c.onSurface,
            )

            // 元信息 15sp（对照 1：必须浅于正文 → c.onSurfaceVariant）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.dateLine} · 共 ${state.charCount} 字 · 未分类",
                    fontSize = 15.sp,
                    color = c.onSurfaceVariant,  // 浅色 #8A8A8E（比 #1A1A1C 浅 → OK）
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            // 语音附件卡（对照 4：浅色 0.05 阴影 + 1px 描边;暗色无）
            AudioAttachmentCard(state = state, c = c, onPlay = vm::togglePlay)

            // 正文 17sp/1.8
            Text(
                text = state.diary?.text.orEmpty(),
                fontSize = 17.sp,
                lineHeight = 30.6.sp,  // 17 × 1.8
                color = c.onSurface,
            )

            Spacer(modifier = Modifier.height(Spacing.Lg))
        }
    }
}

// (helpers extracted to DiaryDetailComponents.kt)
