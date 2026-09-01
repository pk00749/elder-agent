// §B 身份选择
package com.elder.android.screen.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elder.android.R
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.design.tokens.Spacing

@Composable
fun IdentitySelectionScreen(
    onPickFamily: () -> Unit,
    onPickElder: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandColor.CardWhite)
            .padding(Spacing.Lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.identity_pick_title),
            fontSize = FontSize.title(),
            color = BrandColor.TextPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.Xl))
        BigChoiceButton(
            text = stringResource(R.string.identity_family),
            onClick = onPickFamily,
            primary = true,
        )
        Spacer(modifier = Modifier.height(Spacing.Lg))
        BigChoiceButton(
            text = stringResource(R.string.identity_elder),
            onClick = onPickElder,
            primary = false,
        )
    }
}

@Composable
private fun BigChoiceButton(text: String, onClick: () -> Unit, primary: Boolean) {
    val container = if (primary) BrandColor.Brand500 else BrandColor.BgGray
    val content = if (primary) BrandColor.CardWhite else BrandColor.TextPrimary
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(Corner.Button),
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
    ) {
        Text(text = text, fontSize = FontSize.body(), textAlign = TextAlign.Center)
    }
}
