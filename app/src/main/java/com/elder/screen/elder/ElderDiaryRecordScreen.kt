// §3.1.2 单次录音 → ASR → 本地日记（v3.0 MVP 录音屏）
package com.elder.android.screen.elder

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.elder.android.ui.component.LoadingState
import com.elder.android.ui.component.NetworkYellowBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElderDiaryRecordScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    vm: ElderDiaryRecordViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) vm.startRecording()
        else vm.dismissError()
    }

    // §4.6 §G.1 不二次引导：仅在权限检查真正跑完之后才把 StartState（"权限被拒绝"文案 + 授权按钮）
    // 渲染出来；刚进屏那一帧权限还没查完，先用空 Box 占位，避免已授权用户也闪一下"权限被拒绝"。
    var permissionChecked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.onEnter()
        permissionChecked = true
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startRecording()
        } else {
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BrandColor.CardWhite,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.diary_recording_title), fontSize = FontSize.TitleDefaultSp.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vm.cancel()
                            onBack()
                        },
                        modifier = Modifier.size(Size.TouchTargetMin),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
               colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandColor.CardWhite),
            )

            // PR #4：ASR 上游失败（非 ASR_AUTH_FAILED）时顶部展示黄条 + 重试按钮
            // 对应 prd.md §4.9 网络异常黄条规范 — 独立组件，不绑其他上游
            NetworkYellowBar(
                visible = state.networkFailed,
                onRetry = vm::retryAsr,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.Md),
                contentAlignment = Alignment.Center,
            ) {
                val savedTranscript = state.transcript
                val savedId = state.savedId
                when {
                    state.isRecording -> RecordingActive(state = state, onStop = vm::stopAndProcess)
                    state.isProcessing -> LoadingState()
                    savedId != null && savedTranscript != null -> RecordedState(
                        durationMs = state.elapsedMs,
                        transcript = savedTranscript,
                        onDone = onDone,
                    )
                    // 已检查且还没授权 -> StartState（"权限被拒绝" + 授权按钮）；刚进屏权限还没查完 -> 空 Box
                    permissionChecked && ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> StartState(
                        onStart = vm::startRecording,
                        permLauncher = permLauncher,
                    )
                    else -> Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    state.topError?.let { msg ->
        ElderToast(
            message = msg,
            onDismiss = vm::dismissError,
        )
    }

    LaunchedEffect(state.asrNotConfigured) {
        if (state.asrNotConfigured) onBack()
    }
}

@Composable
internal fun RecordingActive(state: DiaryRecordUiState, onStop: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.diary_recording_recording, (state.elapsedMs / 60000).toInt(), ((state.elapsedMs / 1000) % 60).toInt()),
            fontSize = FontSize.body(FontLevel.LARGE),
            fontWeight = FontWeight.Bold,
            color = BrandColor.Error500,
        )
        state.transcript?.takeIf { it.isNotBlank() }?.let { partial ->
            Spacer(modifier = Modifier.height(Spacing.Md))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BrandColor.BgGray,
                shape = RoundedCornerShape(Corner.Card),
            ) {
                Column(modifier = Modifier.padding(Spacing.Md)) {
                    Text(
                        text = stringResource(R.string.diary_recording_transcribing),
                        fontSize = FontSize.body(),
                        color = BrandColor.TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.Sm))
                    Text(
                        text = partial,
                        fontSize = FontSize.body(),
                        color = BrandColor.TextPrimary,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.Xl))
        Button(
            onClick = onStop,
            modifier = Modifier
                .size(Size.PressButtonSize)
                .clip(CircleShape),
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColor.Brand500,
                contentColor = BrandColor.CardWhite,
            ),
        ) {
            Text(
                text = stringResource(R.string.diary_recording_stop),
                fontSize = FontSize.body(),
                fontWeight = FontWeight.Bold,
            )
        }
        // v3.0.2：移除 72dp 全宽次级"停止录音"按钮（PR #5 加的冗余双按钮），只留 160dp 主圆按钮。
        // 反馈"重复按钮"问题：单按钮更清晰，避免老人误触。
        Spacer(modifier = Modifier.height(Spacing.Lg))
    }
}

@Composable
private fun RecordedState(
    durationMs: Long,
    transcript: String,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.diary_recording_done_check) + " " + stringResource(R.string.diary_recording_done_duration, (durationMs / 60000).toInt(), ((durationMs / 1000) % 60).toInt()),
            fontSize = FontSize.body(FontLevel.LARGE),
            fontWeight = FontWeight.Bold,
            color = BrandColor.Brand500,
        )
        Spacer(modifier = Modifier.height(Spacing.Lg))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = BrandColor.BgGray,
            shape = RoundedCornerShape(Corner.Card),
        ) {
            Text(
                text = transcript,
                modifier = Modifier.padding(Spacing.Md),
                fontSize = FontSize.body(),
                color = BrandColor.TextPrimary,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.Lg))
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
            Text(stringResource(R.string.diary_recording_back), fontSize = FontSize.button(), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StartState(
    onStart: () -> Unit,
    permLauncher: androidx.activity.result.ActivityResultLauncher<String>,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.diary_recording_mic_denied),
            fontSize = FontSize.body(),
            color = BrandColor.Error500,
        )
        Spacer(modifier = Modifier.height(Spacing.Lg))
        Button(
            onClick = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            modifier = Modifier
                .fillMaxWidth()
                .height(Size.PrimaryButtonHeight),
            shape = RoundedCornerShape(Corner.Button),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColor.Brand500,
                contentColor = BrandColor.CardWhite,
            ),
        ) {
            Text(stringResource(R.string.diary_recording_auth_and_start), fontSize = FontSize.button(), fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatSec(ms: Long): String {
    val sec = ms / 1000
    return "%d:%02d".format(sec / 60, sec % 60)
}
