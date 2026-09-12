package com.elder.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elder.android.data.db.FontScale
import com.elder.android.design.tokens.BrandColor
import com.elder.android.design.tokens.Corner
import com.elder.android.design.tokens.FontSize
import com.elder.android.di.ServiceLocator

private val ElderColorScheme = lightColorScheme(
    primary = BrandColor.Brand500,
    onPrimary = BrandColor.CardWhite,
    secondary = BrandColor.Aux500,
    onSecondary = BrandColor.CardWhite,
    background = BrandColor.CardWhite,
    onBackground = BrandColor.TextPrimary,
    surface = BrandColor.CardWhite,
    onSurface = BrandColor.TextPrimary,
    surfaceVariant = BrandColor.BgGray,
    onSurfaceVariant = BrandColor.TextSecondary,
    error = BrandColor.Error500,
    onError = BrandColor.CardWhite,
    outline = BrandColor.TextSecondary,
)

private val ElderTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = FontSize.BodyDefaultSp.sp,
        lineHeight = FontSize.BodyDefaultLineHeightSp.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = FontSize.BodyInputSp.sp,
        lineHeight = FontSize.BodyInputLineHeightSp.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = FontSize.BodyDefaultSp.sp,
        lineHeight = FontSize.LabelLineHeightSp.sp,
    ),
)

private val ElderShapes = Shapes(
    small = RoundedCornerShape(Corner.Button),
    medium = RoundedCornerShape(Corner.Card),
    large = RoundedCornerShape(Corner.Card),
)

@Composable
fun ElderTheme(
    fontScale: FontScale? = null,
    content: @Composable () -> Unit,
) {
    val meta by ServiceLocator.deviceMetaRepo.observe()
        .collectAsStateWithLifecycle(initialValue = null)
    val scale = fontScale ?: meta?.fontScale ?: FontScale.DEFAULT
    val currentDensity = LocalDensity.current
    val scaledDensity = remember(currentDensity.density, currentDensity.fontScale, scale) {
        Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * scale.multiplier,
        )
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = ElderColorScheme,
            typography = ElderTypography,
            shapes = ElderShapes,
            content = content,
        )
    }
}

private val FontScale.multiplier: Float
    get() = when (this) {
        FontScale.DEFAULT -> 1f
        FontScale.LARGE -> 1.15f
        FontScale.XLARGE -> 1.3f
    }
