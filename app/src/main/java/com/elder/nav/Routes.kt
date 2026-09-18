// 路由定义（v3.0 MVP —— 老人端 5 屏：主屏 / 录音 / 时间轴 / 设置 / ASR 配置）
// v0.7.0：新增 ElderAsrProvider / ElderTtsProvider 两个 Provider 选择子页（§A.14）。
// v0.8.0：新增 ElderLlmProvider Provider 选择子页（§A.15）。
package com.elder.android.nav

sealed class Route(val path: String) {
    data object ElderHome : Route("elder/home")
    data object ElderDiaryRecord : Route("elder/diary/record")
    data object ElderInterview : Route("elder/diary/interview")
    data object ElderDiaryRecent : Route("elder/diary/recent")
    data object ElderSettings : Route("elder/settings")
    data object ElderAsrConfig : Route("elder/settings/asr")
    data object ElderAsrProvider : Route("elder/settings/asr/provider")    // v0.7.0
    data object ElderTtsProvider : Route("elder/settings/tts/provider")    // v0.7.0
    data object ElderLlmProvider : Route("elder/settings/llm/provider")    // v0.8.0
}
