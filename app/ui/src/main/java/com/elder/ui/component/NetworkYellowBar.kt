// 对应 prd.md §4.9 网络异常黄条
// 说明文案与 56dp 重试按钮上下排列，适配老人端大字体和窄屏
package com.elder.android.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.elder.android.ui.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Size.WarningCardMinHeight)
                .background(
                    color = BrandColor.NetYellow,
                    shape = RoundedCornerShape(Corner.Toast),
                )
                .padding(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
        ) {
            Text(
                text = stringResource(R.string.network_error_text),
                color = BrandColor.TextPrimary,
                fontSize = FontSize.BodyDefaultSp.sp,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Size.TouchTargetMin),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandColor.Brand500,
                    contentColor = BrandColor.CardWhite,
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
