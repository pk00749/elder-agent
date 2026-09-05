// 路由定义（PR 4 导航核心）
package com.elder.android.nav

sealed class Route(val path: String) {
    data object Identity : Route("identity")
    data object FamilyHome : Route("family/home")
    data object FamilyReminderList : Route("family/reminders")
    data object FamilyReminderCreate : Route("family/reminders/new/{type}") {
        fun create(type: String) = "family/reminders/new/$type"
    }
    data object FamilyDiaryList : Route("family/diary")
    data object FamilyDiaryDetail : Route("family/diary/{id}") {
        fun create(id: String) = "family/diary/$id"
    }
    data object FamilyBind : Route("family/bind")
    data object ElderHome : Route("elder/home")
    data object ElderBindConfirm : Route("elder/bind")
    data object ElderDiaryRecord : Route("elder/diary/record")
    data object ElderDiarySummary : Route("elder/diary/summary/{sessionId}") {
        fun create(sessionId: String) = "elder/diary/summary/$sessionId"
    }
    data object ElderDiaryRecent : Route("elder/diary/recent")
    data object ElderReminderFull : Route("elder/reminder/{id}") {
        fun create(id: String) = "elder/reminder/$id"
    }
    data object ElderSettings : Route("elder/settings")
}
