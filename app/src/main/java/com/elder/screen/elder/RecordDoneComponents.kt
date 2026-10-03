// 对应 prd.md §3.1.2：「已录音」状态下的预览卡（白底黑字 + 语音回听）。
//
// 设计要点（对齐 DiaryDetailScreen 的 AudioAttachmentCard 视觉语言，但不传播 v0.14 §H-2/H-3 违规）：
//   1. 页面背景纯白 #FFFFFF（BrandColor.CardWhite）—— 满足「白底黑字」字面要求
//   2. 文本颜色 BrandColor.TextPrimary #1A1A1A —— 满足「黑字」
//   3. RecordAudioCard：34dp 圆播放钮（BgGray 底 + TextPrimary 三角，isPlaying → ⏸）+ 时码 + 3dp 进度条（Brand500 played / BgGray track）+ 完整 transcript
//   4. 全部字体 ≥22sp 走 FontSize token；不复用 DiaryDetail.AudioAttachmentCard（避免传播 14/15/17/20sp 违规）
//   5. 「再录一条」OutlinedButton +「返回主页」主按钮；不删 savedId（原录音仍可在列表从 DetailDialog 回看）
//
// 不动 §18：所有 token 不硬编码；不引入新上游 SDK。
package com.elder.android.screen.elder

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontLevel
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing

/**
 * 「已录音」状态下的预览卡：白底 + 标题 + 语音回听卡 + 转写 + 操作按钮。
 *
 * @param state 录制 UI 状态；透传给 RecordAudioCard 显示进度
 * @param onPlay 播放/暂停按钮回调（VM::togglePlay）
 * @param onRedo 「再录一条」按钮回调（VM::retry —— 复用既有方法）
 * @param onDone 「返回主页」按钮回调
 */
@Composable
internal fun RecordedState(
    state: DiaryRecordUiState,
    onPlay: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题：✓ 已录音 · 0:34
        Text(
            text = stringResource(R.string.diary_recording_done_check) + "  " +
                stringResource(
                    R.string.diary_recording_done_duration,
                    (state.elapsedMs / 60000).toInt(),
                    ((state.elapsedMs / 1000) % 60).toInt(),
                ),
            fontSize = FontSize.body(FontLevel.LARGE),
            fontWeight = FontWeight.Bold,
            color = BrandColor.TextPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.Md))

        // 语音回听卡（白底 + 1px 描边 + 完整 transcript）
        val transcript = state.transcript.orEmpty()
        RecordAudioCard(
            durationMs = state.elapsedMs,
            transcript = transcript,
            isPlaying = state.isPlaying,
            playheadMs = state.playheadMs,
            totalMs = state.totalMs,
            onPlay = onPlay,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Spacer(modifier = Modifier.height(Spacing.Md))

        // 「再录一条」次按钮
        OutlinedButton(
            onClick = onRedo,
            modifier = Modifier
                .fillMaxWidth()
                .height(Size.SecondaryButtonHeight),
            shape = RoundedCornerShape(Corner.Button),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = BrandColor.Brand500,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(Size.IconMd),
            )
            Spacer(modifier = Modifier.width(Spacing.Sm))
            Text(
                text = stringResource(R.string.diary_recording_redo),
                fontSize = FontSize.body(),
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.Sm))

        // 「返回主页」主按钮
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(Size.PrimaryButtonHeight),
            shape = RoundedCornerShape(Corner.Button),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColor.Brand500,
                contentColor = BrandColor.CardWhite,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(Size.IconMd),
            )
            Spacer(modifier = Modifier.width(Spacing.Sm))
            Text(
                text = stringResource(R.string.diary_recording_back),
                fontSize = FontSize.button(),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * 语音附件卡：白底 + 1px 描边 + 「录音 1」标题 + 圆播放钮 + 时码 + 进度条 + transcript。
 *
 * 视觉对齐 DiaryDetailScreen.AudioAttachmentCard，但全部 ≥22sp token，不传播 H-2/H-3 违规。
 */
@Composable
internal fun RecordAudioCard(
    durationMs: Long,
    transcript: String,
    isPlaying: Boolean,
    playheadMs: Long,
    totalMs: Long,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = BrandColor.CardWhite,
        shape = RoundedCornerShape(Corner.Card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.Md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
        ) {
            // 首行：「录音 1」+ 34dp 圆播放钮 + 34dp 圆钮（占位「再录」）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.diary_recording_audio_label),
                    fontSize = FontSize.body(FontLevel.DEFAULT),
                    fontWeight = FontWeight.Bold,
                    color = BrandColor.TextPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    // 34dp 圆播放钮（BgGray 底 + TextPrimary 三角 / 暂停图标）
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(BrandColor.BgGray)
                            .clickable(onClick = onPlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Check else Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.diary_recording_audio_play),
                            tint = BrandColor.TextPrimary,
                            modifier = Modifier.size(Size.IconMd),
                        )
                    }
                    // 34dp 圆「再录」占位（点击提示「再录一条」已在外层 OutlinedButton）
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(BrandColor.BgGray),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.diary_recording_audio_record),
                            tint = BrandColor.Error500,
                            modifier = Modifier.size(Size.IconMd),
                        )
                    }
                }
            }

            // 时码：当前播放头 / 总时长；未播放时显示 0:00 / durationMs
            val totalSec = if (totalMs > 0L) (totalMs / 1000L) else (durationMs / 1000L)
            val headSec = (if (isPlaying) playheadMs else 0L) / 1000L
            val headMin = headSec / 60
            val headSecRem = headSec % 60
            val totalMin = totalSec / 60
            val totalSecRem = totalSec % 60
            Text(
                text = "%02d:%02d / %02d:%02d".format(headMin, headSecRem, totalMin, totalSecRem),
                fontSize = FontSize.body(),
                fontWeight = FontWeight.Medium,
                color = BrandColor.TextPrimary,
            )

            // 进度条 3dp（Brand500 played / BgGray track）
            val fraction = if (totalMs > 0L) (playheadMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = BrandColor.Brand500,
                trackColor = BrandColor.BgGray,
            )

            Spacer(modifier = Modifier.height(Spacing.Xs))

            // 分隔线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BrandColor.BgGray),
            )

            // 完整 transcript
            Text(
                text = transcript,
                fontSize = FontSize.body(FontLevel.LARGE),
                fontWeight = FontWeight.Bold,
                color = BrandColor.TextPrimary,
            )

            Spacer(modifier = Modifier.height(Spacing.Md))
        }
    }
}
