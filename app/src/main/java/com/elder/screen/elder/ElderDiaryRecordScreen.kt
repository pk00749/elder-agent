// §3.1.2 AI 访谈写日志 —— 按住说话 + DSH agent 流
// MVP：录音 → 上传（mock）→ ASR → turn → finalize → summary
package com.elder.android.screen.en

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import com.elder.android.di.ServiceLocator
import com.elder.android.network.dto.AgentDiaryStartRequest
import com.elder.android.network.dto.AgentFinalizeRequest
import com.elder.android.network.dto.AgentTurnRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ElderDiaryRecordScreen(
    onBack: () -> Unit,
    onFinalized: (sessionId: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sessionId by remember { mutableStateOf<String?() null) }
    var maxTurns by remember { mutableStateOf(8) }
    var turnNo by remember { mutableStateOf(0) }
    var assistantText by remember { mutableStateOf<String?() null) }
    var assistantAudioUrl by remember { mutableStateOf<String?() null) }
    var turnsLeft by remember { mutableStateOf(maxTurns) }
    var isHolding by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?() null) }
    val recorder = remember { ServiceLocator.audioRecorder }
    val tts = remember { ServiceLocator.ttsPlayer }
    val ttsText = assistantText

    LaunchedEffect(Unit) {
        val snap = ServiceLocator.tokenStore.current()
        val eid = snap?.elderId ?: snap?.userId
        if (eid != null) {
            runCatching {
                ServiceLocator.apiClient.agentApi.startSession(AgentDiaryStartRequest(elderId = eid))
            }.onSuccess { resp ->
                sessionId = resp.sessionId
                maxTurns = resp.maxTurns
                turnsLeft = resp.maxTurns
                assistantText = resp.greetingText
                assistantAudioUrl = resp.greetingAudioUrl
            }.onFailure {
                error = "访谈启动失败：${it.message}"
            }
        } else {
            error = "未绑定老人，写好的日记会丢失"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(BrandColor.CardWhite).padding(Spacing.Lg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
            Text(
                text = stringResource(R.string.diary_recording_progress, turnNo, maxTurns),
                fontSize = FontSize.body(),
                color = BrandColor.TextSecondary,
            )
            Spacer(Modifier.height(Spacing.Lg))
            // Agent 回复区（§3.1.2 高 200（设计值））
            Box(
                modifier = Modifier.fillMaxWidth().height(Size.AgentReplyAreaHeight),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = assistantText ?: "…",
                        fontSize = FontSize.title(),
                        color = BrandColor.TextPrimary,
                    )
                    Spacer(Modifier.height(Spacing.Sm))
                    Text(
                        text = if (ttsText != null) "▶ 播放中" else "",
                        fontSize = FontSize.caption(),
                        color = BrandColor.Brand500,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // 按住说话按钮（§3.1.2 直径 160（设计值））
            val pressModifier = Modifier
                .size(Size.PressButtonSize)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            val started = try {
                                recorder.start()
                                Unit
                            } catch (_: Exception) {
                                null
                            }
                            val released = tryAwaitRelease()
                            isHolding = false
                            recorder.stop()
                            if (released && started != null && sessionId != null) {
                                isSending = true
                                // ASR + turn
                                scope.launch {
                                    try {
                                        val asr = ServiceLocator.apiClient.agentApi.asr(
                                            com.elder.android.network.dto.AsrRequest(
                                                audioUrl = "https://mock.cos/${sessionId}/${turnNo + 1}.m4a",
                                            ),
                                        )
                                        val resp = ServiceLocator.apiClient.agentApi.turn(
                                            sessionId!!,
                                            AgentTurnRequest(
                                                turnNo = turnNo + 1,
                                                elderText = asr.text,
                                                elderAudioCosKey = "tmp/${sessionId}/${turnNo + 1}.m4a",
                                            ),
                                        )
                                        turnNo = resp.turnNo
                                        turnsLeft = resp.turnsLeft
                                        assistantText = resp.assistantText
                                        assistantAudioUrl = resp.assistantAudioUrl
                                        // 立即播放 TTS（§3.1.2）
                                        runCatching { tts.play(context, resp.assistantAudioUrl) }
                                        if (resp.shouldFinalize) {
                                            delay(2_000)
                                            finalize(onFinalized)
                                        }
                                    } catch (e: Exception) {
                                        error = "网络好像断了，正在重试…"
                                    } finally {
                                        isSending = false
                                    }
                                }
                            }
                        },
                    )
                }
            Button(
                onClick = {},
                enabled = false,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHolding) BrandColor.Brand500 else BrandColor.BgGray,
                    contentColor = if (isHolding) BrandColor.CardWhite else BrandColor.TextPrimary,
                    disabledContainerColor = if (isHolding) BrandColor.Brand500 else BrandColor.BgGray,
                    disabledContentColor = if (isHolding) BrandColor.CardWhite else BrandColor.TextPrimary,
                ),
                shape = RoundedCornerShape(Size.PillCornerRadius),
                modifier = pressModifier,
            ) {
                Text(
                    text = if (isHolding) stringResource(R.string.diary_recording_release) else stringResource(R.string.diary_recording_hold),
                    fontSize = FontSize.body(),
                )
            }
            Spacer(Modifier.height(Spacing.Lg))
            Text(
                text = error ?: "",
                fontSize = FontSize.caption(),
                color = BrandColor.Error500,
            )
        }
    }

    suspend fun finalize(cb: (String) -> Unit) {
        val sid = sessionId ?: return
        runCatching {
            ServiceLocator.apiClient.agentApi.finalize(
                sid,
                AgentFinalizeRequest(
                    turnNo = turnNo + 1,
                    elderText = "（写好了）",
                    elderAudioCosKey = "tmp/$sid/finalize.m4a",
                ),
            )
        }.onSuccess { cb(sid) }
    }
}

private suspend fun kotlinx.coroutines.CoroutineScope.launch(
    block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit,
) = kotlinx.coroutines.launch(this, block = block)
