// 对应 PRD §4.1–§4.4（色彩 / 字号 / 间距 / 圆角 / 动效）+ §3.1.5（主屏区高度 / 图标尺寸）
// 客户端 Compose / XML 视图**禁止**直接写 #4A7A4A / 24sp / 8.dp —— 必须引用本文件
//（AGENTS.md §A.2 / §18 红线）。
package com.elder.android.design.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable

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
    // §3.1.2 信纸转写区的派生色，不新增品牌色（prd.md §I-1）
    val PaperRule = TextSecondary.copy(alpha = 0.18f)
    val PaperMargin = Brand500.copy(alpha = 0.28f)
}

object FontSize {
    val BodyDefaultSp = 24
    val BodyLargeSp = 28
    val BodyXLargeSp = 32
    val BodySmallSp = 22  // v0.9.0: 18 → 22；废除 18 档（最小可视字号 = 22sp）
    val BodyInputSp = 22
    val BodyHugeSp = 40
    val TitleDefaultSp = 32
    val TitleLargeSp = 40  // v0.9.0: 38 → 40；与 BodyHugeSp 合并
    val TitleXLargeSp = 44
    val ButtonDefaultSp = 32
    val ButtonLargeSp = 38
    val ButtonXLargeSp = 44
    val TtsButtonSp = 24
    val BodyDefaultLineHeightSp = 34
    val BodyInputLineHeightSp = 30
    val LabelLineHeightSp = 30
    val TranscriptLineHeightSp = 56  // v0.9.0: 48 → 56；与 BodyHugeSp=40 行高 1.4 倍

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

    fun caption() = 22.sp  // v0.9.0: 20 → 22；最小可视字号 = 22sp
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
    // 老人端触控基准：Material 最低 48dp，老人端统一提升到 56dp
    val TouchTargetMin = 56.dp
    val TopActionMinWidth = 112.dp
    val SegmentButtonHeight = 56.dp
    val WarningCardMinHeight = 120.dp
    // 主屏区域高度（§3.1.5 v3.0 MVP）
    val HomeGreetingArea = 240.dp
    val HomeDiaryArea = 200.dp
    val HomeDiaryButton = 120.dp
    // PR #5：ASR 提示红卡 BorderStroke 宽度（红边粗细，视觉权重匹配红 Error 提示）
    val AsrCardBorderWidth = 2.dp
    // 图标（§3.1.7 今日记录图标）
    val IconMd = 24.dp
    val TodayRecordIcon = TouchTargetMin
    // ASR 未配置提示红点（§3.1.5）
    val WarningDotSize = 12.dp
    // 进度条（§4.10）
    val ProgressBarHeight = 12.dp  // v0.9.0: 8 → 12；老人看细线吃力
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
    // §3.1.2 信纸转写区
    val TranscriptRuleWidth = 1.dp
    val TranscriptRuleSpacing = 48.dp
    // §3.2.7 / §3.1.8 第 4 项 二维码显示
    val QrDisplayHeight = 160.dp
    // 列表行（§3.1.7 时间轴 / §3.1.8 设置行）
    val ListRowMinHeight = 72.dp
    // 弹窗编辑文本框（§3.1.7 改写弹窗）
    val EditDialogFieldHeight = 160.dp
}


/**
 * v0.10.0 §7 日志详情页 token 对照表(只列改动)。
 *
 * 设计合约:
 * - 间距 / 字号 / 圆角 / 组件树全部不动(沿用 Spacing / FontSize / Corner / Size)
 * - 仅本页(DiaryDetailScreen)使用 DiaryColorScheme;既有页面继续走 BrandColor
 * - 4 处必须翻的(对应浅色版):
 *   1. 次要文字必须**浅**于正文 → secondary 必须比 onSurface 灰度高
 *   2. 图标深色描边 strokeWidth=1.8 → 不准半透明灰
 *   3. 文字截断遮罩终点 → surface 底色(浅色 #FFFFFF;暗色 transparent)
 *   4. 浅色卡片阴影 0.05 透明度封顶;暗色无投影
 */
data class DiaryColorScheme(
    val background: Color,        // 页面背景
    val surface: Color,           // 卡片表面
    val surfaceBorder: Color,     // 卡片 1px 描边(浅色专用)
    val surfaceShadowAlpha: Float,// 卡片投影透明度(浅色 ≤ 0.05;暗色 0f 无投影)
    val divider: Color,           // 分隔线
    val onSurface: Color,         // 标题 / 正文主色
    val onSurfaceVariant: Color,  // 次要文字(必须比 onSurface 浅——对照 1)
    val transcriptText: Color,    // 转写文本(比 secondary 还浅一档)
    val buttonSurface: Color,     // 圆形按钮底
    val playTriangle: Color,      // 播放三角(在 buttonSurface 上)
    val recordDot: Color,         // 录音红点(唯一高饱和,跨主题保持)
    val progressTrack: Color,     // 进度轨道
    val progressPlayed: Color,    // 已播进度
    val progressThumb: Color,     // 滑块
    val textMaskEnd: Color,       // 文字截断遮罩终点色(对照 3:浅色=#FFFFFF;暗色=Transparent)
    val iconStroke: Color,        // 工具栏图标描边(对照 2:深色 stroke;浅色 #1A1A1C)
)

object DiaryColor {
    val Light = DiaryColorScheme(
        background = Color(0xFFF4F4F6),         // §7 别用纯白,卡片才有层次
        surface = Color(0xFFFFFFFF),
        surfaceBorder = Color(0xFFECECEF),       // 1px 描边
        surfaceShadowAlpha = 0.05f,              // 浅色必须有极淡投影;0.05 封顶
        divider = Color(0xFFECECEF),
        onSurface = Color(0xFF1A1A1C),           // 标题 / 正文
        onSurfaceVariant = Color(0xFF8A8A8E),    // 次要(必须**浅**于正文)
        transcriptText = Color(0xFF5A5A5E),      // 转写文本(比 secondary 深一档)
        buttonSurface = Color(0xFFF0F0F2),       // 圆形按钮底
        playTriangle = Color(0xFF1A1A1C),        // 播放三角
        recordDot = Color(0xFFFF3B30),           // 录音红点(跨主题一致)
        progressTrack = Color(0xFFE6E6EA),
        progressPlayed = Color(0xFF1A1A1C),
        progressThumb = Color(0xFF1A1A1C),
        textMaskEnd = Color(0xFFFFFFFF),         // 终点=卡片底色(对照 3)
        iconStroke = Color(0xFF1A1A1C),          // 深色描边(strokeWidth=1.8)
    )

    val Dark = DiaryColorScheme(
        background = Color(0xFF000000),
        surface = Color(0xFF26262A),
        surfaceBorder = Color(0xFF000000),       // 暗色无描边(对比靠亮度)
        surfaceShadowAlpha = 0f,                  // 暗色无投影(对照 4)
        divider = Color(0xFF3A3A3C),
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFF8E8E93),    // 次要
        transcriptText = Color(0xFFC7C7CC),
        buttonSurface = Color(0xFF3A3A3C),
        playTriangle = Color(0xFFFFFFFF),
        recordDot = Color(0xFFFF453A),
        progressTrack = Color(0xFF3A3A3C),
        progressPlayed = Color(0xFFFFFFFF),
        progressThumb = Color(0xFFFFFFFF),
        textMaskEnd = Color(0x00000000),          // transparent(对照 3)
        iconStroke = Color(0xFFFFFFFF),
    )

    /**
     * v0.10.0 §7:DiaryDetailScreen 顶层调用拿当前主题的对照表。
     * 既有页面继续用 BrandColor(不受影响)。
     */
    @Composable
    fun current(): DiaryColorScheme = current(LocalIsDarkMode.current)

    fun current(isDark: Boolean): DiaryColorScheme = if (isDark) Dark else Light
}

/**
 * v0.10.0 §7 主题开关 Composable(ElderTheme 内 provide;DiaryDetail 顶层 LocalIsDarkMode.current 读)。
 * 既有页面不读这个,完全无感。
 */
val LocalIsDarkMode = androidx.compose.runtime.compositionLocalOf<Boolean> { error("LocalIsDarkMode 未提供;请在 ElderTheme 内嵌套") }
