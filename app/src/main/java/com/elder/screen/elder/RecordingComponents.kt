// 对应 prd.md §3.1.2：录制中页面（按住说话）+ 录音音量可视化。
//
// 设计要点：
//   1. HoldToTalkButton：160dp 大圆环；按住触发 onPress() / 松开触发 onRelease()；
//      圆弧用 Canvas drawArc 按 audioLevel 0..1 画进度（替代原 tap-to-stop "点击停止" 按钮）
//   2. RecordingActive：顶部时长（Error500 红字 XLARGE）+ "转写中…" 提示 +
//      TranscriptPaper 转写中（weight 1f）+ HoldToTalkButton 居底
//   3. TranscriptPaper 移入本文件（RecordingActive 独占，旧 RecordedState 改用 RecordAudioCard）
//
// 不动 §18：所有颜色 / 字号 / 间距 / 圆角走 BrandColor / FontSize / Spacing / Corner / Size token，
// 不引入新上游 SDK（Canvas drawArc + pointerInput 都是 Compose 内置）。
package com.elder.android.screen.elder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontLevel
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import kotlinx.coroutines.flow.collect

/**
 * 按住说话大圆环（160dp）。
 *
 * @param level 0..1 录音音量（RMS 归一化），由 ViewModel audioLevel state 提供
 * @param onPress 按下时触发（启动录音）
 * @param onRelease 松开时触发（停止录音；仅当 released=true，非 cancel）
 * @param label 圆心文字（默认 "按住说话"）
 */
@Composable
internal fun HoldToTalkButton(
    level: Float,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    label: String,
) {
    Box(
        modifier = Modifier
            .size(Size.PressButtonSize)
            // 圆心背景：Brand500；外圆由 Canvas 画进度弧
            .clip(RoundedCornerShape(percent = 50))
            .background(BrandColor.Brand500)
            .drawBehind {
                // 录音音量弧度（环形进度）
                val clamped = level.coerceIn(0f, 1f)
                val stroke = 12.dp.toPx()
                val inset = stroke / 2f
                drawArc(
                    color = BrandColor.CardWhite,
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = GeometrySize(size.width - stroke, size.height - stroke),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        val released = tryAwaitRelease()
                        if (released) onRelease()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = FontSize.body(FontLevel.DEFAULT),
            fontWeight = FontWeight.Bold,
            color = BrandColor.CardWhite,
        )
    }
}

/**
 * 录制中状态：时长 + 转写中提示 + 实时 transcript + 按住说话圆环。
 *
 * 签名变更（原 onStop → onPress + onRelease），原 tap-to-stop 改为 press-and-hold。
 */
@Composable
internal fun RecordingActive(
    state: DiaryRecordUiState,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(
                R.string.diary_recording_recording,
                (state.elapsedMs / 60000).toInt(),
                ((state.elapsedMs / 1000) % 60).toInt(),
            ),
            fontSize = FontSize.body(FontLevel.XLARGE),
            fontWeight = FontWeight.Bold,
            color = BrandColor.Error500,
        )
        Spacer(modifier = Modifier.height(Spacing.Xs))
        Text(
            text = stringResource(R.string.diary_recording_transcribing),
            fontSize = FontSize.body(),
            color = BrandColor.TextSecondary,
        )
        Spacer(modifier = Modifier.height(Spacing.Md))
        TranscriptPaper(
            text = state.transcript,
            placeholder = stringResource(R.string.diary_recording_transcript_placeholder),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Spacer(modifier = Modifier.height(Spacing.Lg))
        // 提示：按住下方按钮
        Text(
            text = stringResource(R.string.diary_recording_hold_hint),
            fontSize = FontSize.body(),
            fontWeight = FontWeight.Medium,
            color = BrandColor.TextSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(Spacing.Md))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            HoldToTalkButton(
                level = state.audioLevel,
                onPress = onPress,
                onRelease = onRelease,
                label = stringResource(R.string.diary_recording_hold),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.Lg))
    }
}

/**
 * 信纸样式的实时 transcript（横线 + 左侧绿竖线）。
 *
 * 沿用旧 ElderDiaryRecordScreen.TranscriptPaper（§3.1.2 信纸转写区），原位置 private，
 * 这里改为 internal 供 RecordingActive 独占（RecordedState 已改用 RecordAudioCard）。
 */
@Composable
internal fun TranscriptPaper(
    text: String?,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val transcript = text?.takeIf { it.isNotBlank() }
    val scrollState = rememberScrollState()

    LaunchedEffect(transcript) {
        snapshotFlow { scrollState.maxValue }.collect { maxValue ->
            scrollState.scrollTo(maxValue)
        }
    }

    androidx.compose.material3.Surface(
        modifier = modifier,
        color = BrandColor.CardWhite,
        shape = RoundedCornerShape(Corner.Card),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val marginX = Spacing.Lg.toPx()
                    val ruleEndX = size.width - Spacing.Md.toPx()
                    val ruleSpacing = Size.TranscriptRuleSpacing.toPx()
                    val ruleWidth = Size.TranscriptRuleWidth.toPx()

                    var ruleY = Spacing.Lg.toPx()
                    while (ruleY < size.height) {
                        drawLine(
                            color = BrandColor.PaperRule,
                            start = Offset(marginX, ruleY),
                            end = Offset(ruleEndX, ruleY),
                            strokeWidth = ruleWidth,
                        )
                        ruleY += ruleSpacing
                    }

                    drawLine(
                        color = BrandColor.PaperMargin,
                        start = Offset(marginX, 0f),
                        end = Offset(marginX, size.height),
                        strokeWidth = Size.AsrCardBorderWidth.toPx(),
                    )
                }
                .padding(
                    start = Spacing.Xl,
                    top = Spacing.Lg,
                    end = Spacing.Md,
                    bottom = Spacing.Md,
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                Text(
                    text = transcript ?: placeholder,
                    fontSize = FontSize.BodyHugeSp.sp,
                    lineHeight = FontSize.TranscriptLineHeightSp.sp,
                    fontWeight = if (transcript == null) FontWeight.Normal else FontWeight.Bold,
                    color = if (transcript == null) BrandColor.TextSecondary else BrandColor.TextPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.Xxl))
            }
        }
    }
}
