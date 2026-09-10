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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

    LaunchedEffect(Unit) {
        vm.onEnter()
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
                    IconButton(onClick = {
                        vm.cancel()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandColor.CardWhite),
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
                    else -> StartState(onStart = vm::startRecording, permLauncher = permLauncher)
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
private fun RecordingActive(state: DiaryRecordUiState, onStop: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.diary_recording_recording, (state.elapsedMs / 60000).toInt(), ((state.elapsedMs / 1000) % 60).toInt()),
            fontSize = FontSize.body(FontLevel.LARGE),
            fontWeight = FontWeight.Bold,
            color = BrandColor.Error500,
        )
        Spacer(modifier = Modifier.height(Spacing.Xl))
        Button(
            onClick = onStop,
            modifier = Modifier
                .size(Size.PressButtonSize)
                .clip(CircleShape),
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColor.Brand500,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = stringResource(R.string.diary_recording_stop),
                fontSize = FontSize.body(),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ProcessingState() = LoadingState()

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
                contentColor = Color.White,
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
                contentColor = Color.White,
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
