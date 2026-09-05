// 对应 PRD §4.1–§4.4（色彩 / 字号 / 间距 / 圆角 / 动效）+ §3.1.5（主屏区高度 / 图标尺寸）
// 客户端 Compose / XML 视图**禁止**直接写 #4A7A4A / 24sp / 8.dp —— 必须引用本文件
//（AGENTS.md §A.2 / §18 红线）。
package com.elder.android.design.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Easing

object BrandColor {
    val Brand500 = Color(0xFF4A7A4A)
    val Aux500 = Color(0xFF6B8E6B)
    val Error500 = Color(0xFFC44545)
    val NetYellow = Color(0xFFFFF4E6)
    val TextPrimary = Color(0xFF1A1A1A)
    val TextSecondary = Color(0xFF666666)
    val BgGray = Color(0xFFF0F0F0)
    val CardWhite = Color(0xFFFFFFFF)
    val WarmOrange = Color(0xFFE07A3C)
}

object FontSize {
    val BodyDefaultSp = 24
    val BodyLargeSp = 28
    val BodyXLargeSp = 32
    val BodyHugeSp = 40
    val TitleDefaultSp = 32
    val TitleLargeSp = 38
    val TitleXLargeSp = 44
    val ButtonDefaultSp = 32
    val ButtonLargeSp = 38
    val ButtonXLargeSp = 44
    val TtsButtonSp = 24

    fun body(level: FontLevel = FontLevel.DEFAULT) = when (level) {
        FontLevel.DEFAULT -> BodyDefaultSp.sp
        FontLevel.LARGE -> BodyLargeSp.sp
        FontLevel.XLARGE -> BodyXLargeSp.sp
    }
    fun title(level: FontLevel = FontLevel.DEFAULT) = when (level) {
        FontLevel.DEFAULT -> TitleDefaultSp.sp
        FontLevel.LARGE -> TitleLargeSp.sp
        FontLevel.XLARGE -> TitleXLargeSp.sp
    }
    fun button(level: FontLevel = FontLevel.DEFAULT) = when (level) {
        FontLevel.DEFAULT -> ButtonDefaultSp.sp
        FontLevel.LARGE -> ButtonLargeSp.sp
        FontLevel.XLARGE -> ButtonXLargeSp.sp
    }

    fun caption() = 20.sp
}

enum class FontLevel { DEFAULT, LARGE, XLARGE }

object Spacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 16.dp
    val Lg = 24.dp
    val Xl = 32.dp
    val Xxl = 48.dp
}

object Corner {
    val Card = 8.dp
    val Button = 8.dp
    val Input = 8.dp
    val Toast = 8.dp
    val Pill = 9999.dp
}

object Motion {
    const val DurationMs = 200
    val Easing: Easing = FastOutSlowInEasing
}

object Size {
    // 主屏区域高度（§3.1.5）
    val HomeGreetingHeight = 200.dp
    val HomeReminderCardHeight = 120.dp
    val HomeDiaryButtonHeight = 120.dp
    // 图标（§3.1.7 今日记录图标）
    val TodayRecordIcon = 32.dp
    // 进度条（§4.10）
    val ProgressBarHeight = 8.dp
    // Toast 距底（§4.8）
    val ToastBottomMargin = 96.dp
    // 主操作按钮（§3.2.3「保存」96dp / §3.1.8 第 6 项设置行 72dp）
    val PrimaryButtonHeight = 96.dp
    val SecondaryButtonHeight = 72.dp
    // §3.1.2 录音/Agent 回复区
    val AgentReplyAreaHeight = 200.dp
    val PressButtonSize = 160.dp
    val PillCornerRadius = 80.dp
    val BackButtonHeight = 56.dp
    // §3.2.7 / §3.1.8 第 4 项 二维码显示
    val QrDisplayHeight = 160.dp
}
