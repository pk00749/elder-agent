// 对应 prd.md §4.7 空态
// 居中文案 + ▶ TTS 按钮 24dp
package com.elder.android.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        Button(
            onClick = onTtsClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandColor.Brand500,
                contentColor = Color.White,
            ),
            modifier = Modifier.padding(top = Spacing.Md),
        ) {
            Text(text = "▶", fontSize = FontSize.TtsButtonSp.sp)
        }
    }
}
