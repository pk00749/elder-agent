// 对应 prd.md §4.10 加载态
// 居中"加载中…" 24sp + 进度条（无菊花），不出现 modal 遮罩
package com.elder.android.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Size
import com.elder.android.design.tokens.Spacing
import com.elder.android.ui.R
import androidx.compose.ui.res.stringResource

@Composable
fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.Lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.loading_text),
            color = BrandColor.TextPrimary,
            fontSize = FontSize.body(),
        )
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(Size.ProgressBarHeight)
                .padding(top = Spacing.Md),
            color = BrandColor.Brand500,
        )
    }
}
