// 顶层导航图（v3.0 MVP —— 单端 5 屏，no role switch, no family side）
// v0.7.0：新增 ElderAsrProvider / ElderTtsProvider 两个 Provider 子页（§A.14）。
// v0.8.0：新增 ElderLlmProvider Provider 子页（§A.15）。
package com.elder.android.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.elder.android.screen.asr.AsrConfigScreen
import com.elder.android.screen.asr.AsrProviderScreen
import com.elder.android.screen.asr.LlmProviderScreen
import com.elder.android.screen.asr.TtsProviderScreen
import com.elder.android.screen.elder.ElderDiaryRecentScreen
import com.elder.android.screen.elder.ElderDiaryRecordScreen
import com.elder.android.screen.elder.ElderHomeScreen
import com.elder.android.screen.elder.ElderSettingsScreen
import com.elder.android.screen.interview.InterviewScreen
import com.elder.android.screen.oss.OssConfigScreen

@Composable
fun AppNavGraph() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Route.ElderHome.path) {
        composable(Route.ElderHome.path) {
            ElderHomeScreen(
                onStartDiary = { nav.navigate(Route.ElderInterview.path) },
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
        composable(Route.ElderInterview.path) {
            InterviewScreen(
                onBack = { nav.popBackStack() },
                onDone = {
                    nav.navigate(Route.ElderHome.path) {
                        popUpTo(Route.ElderHome.path) { inclusive = false }
                    }
                },
                onOpenSettings = { nav.navigate(Route.ElderSettings.path) },
            )
        }
        composable(Route.ElderDiaryRecent.path) {
            ElderDiaryRecentScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.ElderSettings.path) {
            ElderSettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenAsr = { nav.navigate(Route.ElderAsrConfig.path) },
                onOpenOss = { nav.navigate(Route.ElderOssConfig.path) },  // v0.10.0 §6
                onLoggedOut = {
                    nav.navigate(Route.ElderHome.path) {
                        popUpTo(Route.ElderHome.path) { inclusive = true }
                    }
                },
            )
        }
        composable(Route.ElderAsrConfig.path) {
            AsrConfigScreen(
                onBack = { nav.popBackStack() },
                onOpenAsrProvider = { nav.navigate(Route.ElderAsrProvider.path) },
                onOpenTtsProvider = { nav.navigate(Route.ElderTtsProvider.path) },
                onOpenLlmProvider = { nav.navigate(Route.ElderLlmProvider.path) },
            )
        }
        composable(Route.ElderLlmProvider.path) {
            LlmProviderScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.ElderAsrProvider.path) {
            AsrProviderScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.ElderTtsProvider.path) {
            TtsProviderScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.ElderOssConfig.path) {  // v0.10.0 §6
            OssConfigScreen(onBack = { nav.popBackStack() })
        }
    }
}
