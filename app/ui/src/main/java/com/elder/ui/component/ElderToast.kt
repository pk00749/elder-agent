// 对应 prd.md §4.8 错误 Toast
// 4s / 底部 96dp / Error500 底 / 白字 / TTS 同步播报（占位）
package com.elder.android.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import kotlinx.coroutines.delay

private const val SHOW_DURATION_MS = 4000L

@Composable
fun ElderToast(
    message: String?,
    onDismiss: () -> Unit,
) {
    var visible by remember { mutableStateOf(message != null) }
    LaunchedEffect(message) {
        if (message != null) {
            visible = true
            delay(SHOW_DURATION_MS)
            visible = false
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(Spacing.Lg),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Size.ToastBottomMargin)
                    .background(
                        color = BrandColor.Error500,
                        shape = RoundedCornerShape(Corner.Toast),
                    )
                    .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
            ) {
                Text(
                    text = message.orEmpty(),
                    color = BrandColor.CardWhite,
                    fontSize = FontSize.body(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
