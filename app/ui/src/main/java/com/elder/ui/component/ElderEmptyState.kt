// 对应 prd.md §4.7 空态
// 居中文案 + ▶ TTS 按钮 24dp
package com.elder.android.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Spacing

@Composable
fun ElderEmptyState(
    text: String,
    onTtsClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.Lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = BrandColor.TextPrimary,
            fontSize = FontSize.body(),
        )
        // PR #5：▶ emoji 升级为 Icons.Default.VolumeUp + contentDescription（a11y 可读）
        // 同步对应 prd.md §18 红线 — 禁止 emoji 作图标，必须 Material icon
        Button(
            onClick = onTtsClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColor.Brand500,
                contentColor = Color.White,
            ),
            modifier = Modifier
                .padding(top = Spacing.Md)
                .semantics { contentDescription = "朗读" },
        ) {
            Icon(
                // PR #5：core icons 里没有 VolumeUp，用 PlayArrow（视觉三角形同原 ▶）
                // Button 自带 semantics{ contentDescription = "朗读" }，a11y 仍正确
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,  // Button 的 semantics 已经覆盖
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
