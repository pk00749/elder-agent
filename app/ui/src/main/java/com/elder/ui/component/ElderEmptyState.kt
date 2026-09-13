// 对应 prd.md §4.7 空态
// 居中文案 + 可选「朗读」按钮
package com.elder.android.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import com.elder.android.ui.R

@Composable
fun ElderEmptyState(
    text: String,
    onTtsClick: (() -> Unit)? = null,
) {
    val ttsLabel = stringResource(R.string.empty_tts_button)
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
        if (onTtsClick != null) {
            Button(
                onClick = onTtsClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandColor.Brand500,
                    contentColor = BrandColor.CardWhite,
                ),
                modifier = Modifier
                    .padding(top = Spacing.Md)
                    .height(Size.SecondaryButtonHeight)
                    .semantics { contentDescription = ttsLabel },
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(Size.IconMd),
                )
                Spacer(modifier = Modifier.width(Spacing.Sm))
                Text(
                    text = ttsLabel,
                    fontSize = FontSize.body(),
                )
            }
        }
    }
}
