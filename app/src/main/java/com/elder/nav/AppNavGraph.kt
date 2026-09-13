// 顶层导航图（v3.0 MVP —— 单端 5 屏，no role switch, no family side）
package com.elder.android.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.elder.android.screen.asr.AsrConfigScreen
import com.elder.android.screen.elder.ElderDiaryRecentScreen
import com.elder.android.screen.elder.ElderDiaryRecordScreen
import com.elder.android.screen.elder.ElderHomeScreen
import com.elder.android.screen.elder.ElderSettingsScreen

@Composable
fun AppNavGraph() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Route.ElderHome.path) {
        composable(Route.ElderHome.path) {
            ElderHomeScreen(
                onStartDiary = { nav.navigate(Route.ElderDiaryRecord.path) },
                onOpenSettings = { nav.navigate(Route.ElderSettings.path) },
                onOpenRecent = { nav.navigate(Route.ElderDiaryRecent.path) },
            )
        }
        composable(Route.ElderDiaryRecord.path) {
            ElderDiaryRecordScreen(
                onBack = { nav.popBackStack() },
                onDone = {
                    nav.navigate(Route.ElderHome.path) {
                        popUpTo(Route.ElderHome.path) { inclusive = false }
                    }
                },
            )
        }
        composable(Route.ElderDiaryRecent.path) {
            ElderDiaryRecentScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.ElderSettings.path) {
            ElderSettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenAsr = { nav.navigate(Route.ElderAsrConfig.path) },
                onLoggedOut = {
                    nav.navigate(Route.ElderHome.path) {
                        popUpTo(Route.ElderHome.path) { inclusive = true }
                    }
                },
            )
        }
        composable(Route.ElderAsrConfig.path) {
            AsrConfigScreen(onBack = { nav.popBackStack() })
        }
    }
}
