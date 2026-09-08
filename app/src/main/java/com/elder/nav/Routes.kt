// 路由定义（v3.0 MVP —— 老人端 5 屏：主屏 / 录音 / 时间轴 / 设置 / ASR 配置）
package com.elder.android.nav

sealed class Route(val path: String) {
    data object ElderHome : Route("elder/home")
    data object ElderDiaryRecord : Route("elder/diary/record")
    data object ElderDiaryRecent : Route("elder/diary/recent")
    data object ElderSettings : Route("elder/settings")
    data object ElderAsrConfig : Route("elder/settings/asr")
}
