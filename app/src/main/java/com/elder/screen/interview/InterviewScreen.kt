package com.elder.android.screen.interview

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontLevel
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import com.elder.android.ui.component.ElderToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterviewScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: InterviewViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var permissionChecked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionChecked = true
        if (granted) vm.startRecording()
    }

    LaunchedEffect(Unit) {
        vm.onEnter()
        permissionChecked = true
    }

    Surface(modifier = Modifier.fillMaxSize(), color = BrandColor.BgGray) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.interview_title),
                        fontSize = FontSize.TitleDefaultSp.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vm.cancel()
                            onBack()
                        },
                        modifier = Modifier.size(Size.TouchTargetMin),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandColor.CardWhite),
            )

            // v0.11.0 §3.3: 顶栏瞬态 Toast(fadeIn 200ms + 2.5s 显示 + fadeOut 500ms)
            LLMReplyToast(text = state.llmReplyToastText)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(Spacing.Md),
                verticalArrangement = Arrangement.spacedBy(Spacing.Md),
            ) {
                Text(
                    text = stringResource(R.string.interview_turn, state.turnNo, state.maxTurns),
                    fontSize = FontSize.body(),
                    color = BrandColor.TextSecondary,
                )

                if (state.needsConfig) {
                    ConfigCard(onOpenSettings)
                }

                // v0.11.0 §3.3: 取消 AssistantCard 固定卡;LLM 回复改顶栏 Toast
                // v0.11.0 §3.4: 语音退出黄条仅在 READY 阶段可见
                if (state.stage == InterviewStage.READY && state.showVoiceEndHint) {
                    VoiceEndHintBar(onDismiss = vm::dismissVoiceEndHint)
                }

                TranscriptCard(
                    text = state.transcript,
                    modifier = Modifier.weight(1f),
                )

                when (state.stage) {
                    InterviewStage.READY -> Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                vm.startRecording()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        enabled = !state.needsConfig,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Size.PrimaryButtonHeight),
                        shape = RoundedCornerShape(Corner.Button),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandColor.Brand500,
                            contentColor = BrandColor.CardWhite,
                        ),
                    ) {
                        Text(stringResource(R.string.interview_start), fontSize = FontSize.button(), fontWeight = FontWeight.Bold)
                    }

                    InterviewStage.RECORDING -> Button(
                        onClick = { vm.stopAndProcess(onDone) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Size.PrimaryButtonHeight),
                        shape = RoundedCornerShape(Corner.Button),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandColor.Error500,
                            contentColor = BrandColor.CardWhite,
                        ),
                    ) {
                        Text(stringResource(R.string.interview_stop), fontSize = FontSize.button(), fontWeight = FontWeight.Bold)
                    }

                    InterviewStage.THINKING -> StatusRow(stringResource(R.string.interview_thinking))
                    InterviewStage.SPEAKING -> StatusRow(stringResource(R.string.interview_speaking))
                    InterviewStage.REVIEW -> ReviewActions(
                        text = state.draftText.orEmpty(),
                        summary = state.draftSummary.orEmpty(),
                        canRevise = state.canRevise,
                        onSave = { vm.saveDiary(onDone) },
                        onRevise = vm::revise,
                    )
                    InterviewStage.OPENING -> OpeningStatusRow(stringResource(R.string.interview_opening_status))
                    InterviewStage.PREPARING, InterviewStage.SAVED -> SavingStatusRow(stringResource(R.string.interview_saving_status))
                }
            }
        }
    }

    if (state.pendingSaved) {
        ElderToast(
            message = stringResource(R.string.interview_pending_saved),
            onDismiss = vm::dismissError,
        )
    } else {
        state.topError?.let {
            ElderToast(message = it, onDismiss = vm::dismissError)
        }
    }
}

/**
 * v0.11.0 §3.3: 顶栏瞬态 Toast — LLM 回复文字短暂显示后淡出。
 * AnimatedVisibility 控制 fadeIn/fadeOut;text = null 时不渲染;200ms fade-in + 500ms fade-out。
 */
@Composable
private fun LLMReplyToast(text: String?) {
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(500)),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = BrandColor.CardWhite,
            shape = RoundedCornerShape(Corner.Card),
        ) {
            Box(Modifier.padding(Spacing.Md)) {
                Text(
                    text = text.orEmpty(),
                    fontSize = FontSize.body(),
                    fontWeight = FontWeight.Normal,
                    color = BrandColor.TextSecondary,
                )
            }
        }
    }
}

/**
 * v0.11.0 §3.4: 语音退出黄条 — 老人首次进入访谈屏 READY 时顶部黄色提示一次,
 * 老人关闭后写入 prefs,后续不再弹。可关闭、不阻塞交互。
 */
@Composable
private fun VoiceEndHintBar(onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BrandColor.NetYellow,
        shape = RoundedCornerShape(Corner.Card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.interview_voice_end_hint),
                fontSize = FontSize.body(),
                color = BrandColor.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.interview_voice_end_hint_close),
                    color = BrandColor.Brand500,
                    fontSize = FontSize.body(),
                )
            }
        }
    }
}

@Composable
private fun TranscriptCard(text: String?, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BrandColor.CardWhite,
        shape = RoundedCornerShape(Corner.Card),
    ) {
        Box(Modifier.fillMaxSize().padding(Spacing.Md)) {
            Text(
                text = text.orEmpty().ifBlank { stringResource(R.string.interview_transcript_placeholder) },
                modifier = Modifier.verticalScroll(scroll),
                fontSize = FontSize.BodyHugeSp.sp,  // v0.9.0: LARGE 28 → BodyHugeSp=40（老人最大可读档，1.4 倍行高）
                lineHeight = FontSize.TranscriptLineHeightSp.sp,  // v0.9.0: TranscriptLineHeightSp 48 → 56
                fontWeight = FontWeight.Bold,  // v0.9.0: 默认 → Bold（用户自己说出口的话要看清）
                color = if (text.isNullOrBlank()) BrandColor.TextSecondary else BrandColor.TextPrimary,
            )
        }
    }
}

@Composable
private fun ConfigCard(onOpenSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BrandColor.BgGray,
        shape = RoundedCornerShape(Corner.Card),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.interview_need_config),
                modifier = Modifier.weight(1f),
                color = BrandColor.Error500,
                fontSize = FontSize.body(),
            )
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.interview_open_settings), color = BrandColor.Brand500)
            }
        }
    }
}

@Composable
private fun StatusRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Size.PrimaryButtonHeight),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, fontSize = FontSize.body(FontLevel.LARGE), color = BrandColor.Brand500)
    }
}

/**
 * v0.9.0 OPENING 阶段专用：显示"让我先打个招呼…" + 进度条。
 * 与 StatusRow 区别：垂直堆叠（文字 + LinearProgressIndicator），符合 §4.10 加载态规范。
 */
@Composable
private fun OpeningStatusRow(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(Size.PrimaryButtonHeight),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, fontSize = FontSize.body(FontLevel.LARGE), color = BrandColor.Brand500)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Spacing.Sm))
        androidx.compose.material3.LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(Size.ProgressBarHeight),
            color = BrandColor.Brand500,
        )
    }
}


/**
 * v0.11.x bugfix: PREPARING / SAVED 阶段专用 —— 文字 "保存中…" + 进度条,
 * 固定 PrimaryButtonHeight 行高。
 *
 * 修复根因:
 *   原 `LoadingState()` 内部 `.fillMaxSize()`,在父 Column (Arrangement.spacedBy) 末尾会抢占
 *   `TranscriptCard(weight 1f)` 的全部高度,导致老人看到整屏 "加载中…" + 进度条,
 *   看不到 transcript 上下文,主观"卡在加载"。
 *
 *   改成与 `OpeningStatusRow` 同款固定行高的 Column 后,TranscriptCard 仍可 weight(1f)
 *   撑满剩余空间,老人看到 "顶部 transcript + 中间一行保存中 + 进度条",清楚知道系统在保存。
 */
@Composable
private fun SavingStatusRow(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(Size.PrimaryButtonHeight),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, fontSize = FontSize.body(FontLevel.LARGE), color = BrandColor.Brand500)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(Spacing.Sm))
        androidx.compose.material3.LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(Size.ProgressBarHeight),
            color = BrandColor.Brand500,
        )
    }
}

@Composable
private fun ReviewActions(
    text: String,
    summary: String,
    canRevise: Boolean,
    onSave: () -> Unit,
    onRevise: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
        Text(
            text = summary,
            fontSize = FontSize.body(FontLevel.LARGE),
            color = BrandColor.TextSecondary,
        )
        Text(
            text = text,
            fontSize = FontSize.body(),
            color = BrandColor.TextPrimary,
        )
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(Size.PrimaryButtonHeight),
            colors = ButtonDefaults.buttonColors(containerColor = BrandColor.Brand500),
        ) {
            Text(stringResource(R.string.diary_summary_save), fontSize = FontSize.button())
        }
        if (canRevise) {
            TextButton(onClick = onRevise) {
                Text(stringResource(R.string.diary_summary_edit), color = BrandColor.Brand500, fontSize = FontSize.body())
            }
        }
    }
}
