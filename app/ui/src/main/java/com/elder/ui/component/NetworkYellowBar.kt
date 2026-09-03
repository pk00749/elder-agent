// 对应 prd.md §4.9 网络异常黄条
// 48dp / #FFF4E6 / "重试"按钮 96×48dp / 恢复后自动隐藏 + 短震（占位）
package com.elder.android.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elder.android.ui.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Spacing

private val BarHeight = 48.dp
private val RetrySize = 96.dp

@Composable
fun NetworkYellowBar(
    visible: Boolean,
    onRetry: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .background(
                    color = BrandColor.NetYellow,
                    shape = RoundedCornerShape(Corner.Toast),
                )
                .padding(horizontal = Spacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.network_error_text),
                color = BrandColor.TextPrimary,
                fontSize = FontSize.BodyDefaultSp.sp,
            )
            Box(modifier = Modifier.size(width = RetrySize, height = BarHeight)) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandColor.Brand500,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(Corner.Toast),
                ) {
                    Text(
                        text = stringResource(R.string.network_retry_button),
                        fontSize = FontSize.BodyDefaultSp.sp,
                    )
                }
            }
        }
    }
}
