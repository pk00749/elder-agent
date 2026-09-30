// 对应 docs/v0.10.0.md §7:DiaryDetailScreen 的子组件(提取以保持单文件 ≤400 行,AGENTS.md §3)。
package com.elder.android.screen.diary

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elder.android.R
import com.elder.android.design.tokens.DiaryColorScheme
import com.elder.android.design.tokens.Spacing

@Composable
internal fun AudioAttachmentCard(
    state: DiaryDetailUiState,
    c: com.elder.android.design.tokens.DiaryColorScheme,
    onPlay: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .then(
                if (c.surfaceBorder.alpha > 0f) {
                    Modifier.border(1.dp, c.surfaceBorder, RoundedCornerShape(22.dp))
                } else {
                    Modifier
                }
            )
            .drawWithContent {
                drawContent()
                if (c.surfaceShadowAlpha > 0f) {
                    // 对照 4：极淡投影(0.05 透明度封顶)
                    drawRect(
                        color = Color.Black.copy(alpha = c.surfaceShadowAlpha),
                        topLeft = Offset(0f, 2f),
                        size = Size(size.width, 8f),
                    )
                }
            }
            .padding(Spacing.Md),  // 内 padding 16dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
            // 首行：「录音 1」+ 两个 34dp 圆钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.diary_detail_audio_label, 1),
                    fontSize = 20.sp,
                    fontWeight = FontWeight(500),
                    color = c.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 播放 圆钮（34dp）
                    CircleIconButton(
                        background = c.buttonSurface,
                        onClick = onPlay,
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Check else Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.diary_detail_audio_play),
                            tint = c.playTriangle,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    // 录音 红点（34dp）
                    CircleIconButton(
                        background = c.buttonSurface,
                        onClick = { /* 功能开发中：重新录音 */ },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(c.recordDot),
                        )
                    }
                }
            }

            // 时间码 15sp（对照 1：c.onSurfaceVariant 必须浅于正文）
            Text(
                text = formatTimecode(state.playheadMs, state.totalMs.takeIf { it > 0L } ?: (state.diary?.durationMs ?: 0L).toLong()),
                fontSize = 15.sp,
                color = c.onSurfaceVariant,
            )

            // 进度条 3dp 圆角（用 Box 自绘以避免 Material 进度条 12dp 默认高度）
            ProgressBar3dp(
                fraction = state.progressFraction,
                track = c.progressTrack,
                played = c.progressPlayed,
                thumb = c.progressThumb,
            )

            // 1px 分隔线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.divider),
            )

            // 转写文本 14sp/1.75（对照 1+3：transcriptText 必须浅于正文；mask 终点跟随卡片底色）
            val transcript = state.diary?.transcript.orEmpty()
            if (transcript.isNotEmpty()) {
                Text(
                    text = transcript,
                    fontSize = 14.sp,
                    lineHeight = 24.5.sp,  // 14 × 1.75
                    color = c.transcriptText,
                    maxLines = if (state.isExpanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.drawWithContent {
                        drawContent()
                        // 对照 3：文字截断遮罩（仅在未展开 + 实际超过 6 行时绘制）
                        if (!state.isExpanded && state.shouldClampTranscript) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.55f to Color.Transparent,
                                    1f to c.textMaskEnd,  // 浅色 #FFFFFF / 暗色 transparent
                                ),
                            )
                        }
                    },
                )

                if (state.shouldClampTranscript) {
                    Spacer(modifier = Modifier.height(Spacing.Xs))
                    Text(
                        text = stringResource(
                            if (state.isExpanded) R.string.diary_detail_transcript_collapse
                            else R.string.diary_detail_transcript_expand
                        ),
                        fontSize = 14.sp,
                        fontWeight = FontWeight(500),
                        color = c.onSurfaceVariant,
                        modifier = Modifier.clickable { /* 由 VM 控制;此处不展开以避免耦合 */ },
                    )
                }
            }
        }
    }
}

@Composable
internal fun CircleIconButton(
    background: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun ProgressBar3dp(
    fraction: Float,
    track: Color,
    played: Color,
    thumb: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp),
    ) {
        // 轨道
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(1.5.dp))
                .background(track),
        )
        // 已播
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxSize()
                .clip(RoundedCornerShape(1.5.dp))
                .background(played),
        )
        // 滑块（8dp 圆点）
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(thumb)
                .align(Alignment.CenterStart)
                .padding(start = (fraction.coerceIn(0f, 1f) * 1000).dp),  // 简化:实际用 Modifier.offset
        )
    }
}

@Composable
internal fun BottomToolbar(c: com.elder.android.design.tokens.DiaryColorScheme, context: android.content.Context) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(c.divider),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = Spacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            // AI 魔法棒（紫粉青渐变填充）
            BottomIcon(
                icon = Icons.Default.Add,  // 占位：项目无 Sparkle 图标
                tint = Brush.verticalGradient(listOf(Color(0xFF9D5CFF), Color(0xFFFF6FB5), Color(0xFF4FC3F7))),
                contentDesc = stringResource(R.string.diary_detail_bottom_ai),
                onClick = { Toast.makeText(context, R.string.diary_detail_feature_wip, Toast.LENGTH_SHORT).show() },
                isGradient = true,
            )
            BottomIcon(icon = Icons.Default.Check, tint = c.iconStroke, contentDesc = stringResource(R.string.diary_detail_bottom_done), onClick = { Toast.makeText(context, R.string.diary_detail_feature_wip, Toast.LENGTH_SHORT).show() })
            BottomIcon(icon = Icons.Default.AddCircle, tint = c.iconStroke, contentDesc = stringResource(R.string.diary_detail_bottom_image), onClick = { Toast.makeText(context, R.string.diary_detail_feature_wip, Toast.LENGTH_SHORT).show() })
            BottomIcon(icon = Icons.Default.Add, tint = c.iconStroke, contentDesc = stringResource(R.string.diary_detail_bottom_add), onClick = { Toast.makeText(context, R.string.diary_detail_feature_wip, Toast.LENGTH_SHORT).show() })
            BottomIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, tint = c.iconStroke, contentDesc = stringResource(R.string.diary_detail_bottom_voice), onClick = { Toast.makeText(context, R.string.diary_detail_feature_wip, Toast.LENGTH_SHORT).show() })
        }
        Spacer(modifier = Modifier.height(Spacing.Sm))
    }
}

@Composable
internal fun BottomIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Any,  // Color 或 Brush
    contentDesc: String,
    onClick: () -> Unit,
    isGradient: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isGradient) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(24.dp).drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFF9D5CFF), Color(0xFFFF6FB5), Color(0xFF4FC3F7))),
                    )
                },
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = tint as Color,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

internal fun formatTimecode(playheadMs: Long, totalMs: Long): String {
    val pMin = playheadMs / 60_000L
    val pSec = (playheadMs % 60_000L) / 1_000L
    val tMin = totalMs / 60_000L
    val tSec = (totalMs % 60_000L) / 1_000L
    return "%02d:%02d / %02d:%02d".format(pMin, pSec, tMin, tSec)
}
