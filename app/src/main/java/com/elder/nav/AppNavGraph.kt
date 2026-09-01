// 顶层导航图（PR 4 —— MVP 启动后按 token 角色路由首屏）
package com.elder.android.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.elder.android.di.ServiceLocator
import com.elder.android.screen.auth.IdentitySelectionScreen
import com.elder.android.screen.auth.LoginScreen
import com.elder.android.screen.bind.FamilyBindScreen
import com.elder.android.screen.elder.ElderBindConfirmScreen
import com.elder.android.screen.elder.ElderDiaryRecentScreen
import com.elder.android.screen.elder.ElderDiaryRecordScreen
import com.elder.android.screen.elder.ElderDiarySummaryScreen
import com.elder.android.screen.elder.ElderHomeScreen
import com.elder.android.screen.elder.ElderReminderFullScreen
import com.elder.android.screen.elder.ElderSettingsScreen
import com.elder.android.screen.family.FamilyDiaryDetailScreen
import com.elder.android.screen.family.FamilyDiaryListScreen
import com.elder.android.screen.family.FamilyHomeScreen
import com.elder.android.screen.family.FamilyReminderCreateScreen
import com.elder.android.screen.family.FamilyReminderListScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph() {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val tokenState = ServiceLocator.tokenStore.snapshot.collectAsState(initial = null)
    var bootstrapDone by remember { mutableStateOf(false) }
    var startRoute by remember { mutableStateOf(Route.Identity.path) }

    LaunchedEffect(Unit) {
        val snap = ServiceLocator.tokenStore.current()
        startRoute = when {
            snap == null -> Route.Identity.path
            snap.role == "family" -> Route.FamilyHome.path
            else -> Route.ElderHome.path  // elder —— 已绑定走 home，未绑定也可走 home 用日记按钮（§3.2.9 提示）
        }
        bootstrapDone = true
    }

    if (!bootstrapDone) return

    NavHost(navController = nav, startDestination = startRoute) {
        composable(Route.Identity.path) {
            IdentitySelectionScreen(
                onPickFamily = { nav.navigate(Route.Login.create("family")) },
                onPickElder = { nav.navigate(Route.Login.create("elder")) },
            )
        }
        composable(
            Route.Login.path,
            arguments = listOf(navArgument("role") { type = NavType.StringType }),
        ) { backStack ->
            val role = backStack.arguments?.getString("role") ?: "family"
            LoginScreen(
                role = role,
                onLoggedIn = {
                    if (role == "family") nav.navigate(Route.FamilyHome.path) {
                        popUpTo(Route.Identity.path) { inclusive = true }
                    } else nav.navigate(Route.ElderHome.path) {
                        popUpTo(Route.Identity.path) { inclusive = true }
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Route.FamilyHome.path) {
            FamilyHomeScreen(
                onOpenReminders = { nav.navigate(Route.FamilyReminderList.path) },
                onOpenDiary = { nav.navigate(Route.FamilyDiaryList.path) },
                onOpenBind = { nav.navigate(Route.FamilyBind.path) },
                onLogout = {
                    scope.launch {
                        ServiceLocator.tokenStore.clear()
                        nav.navigate(Route.Identity.path) { popUpTo(0) }
                    }
                },
            )
        }
        composable(Route.FamilyReminderList.path) {
            FamilyReminderListScreen(
                onAdd = { nav.navigate(Route.FamilyReminderCreate.create("medication")) },
                onAddAppt = { nav.navigate(Route.FamilyReminderCreate.create("appointment")) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            Route.FamilyReminderCreate.path,
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) { backStack ->
            val type = backStack.arguments?.getString("type") ?: "medication"
            FamilyReminderCreateScreen(
                initialType = type,
                elderId = ServiceLocator.tokenStore.current()?.elderId ?: "",
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
        composable(Route.FamilyDiaryList.path) {
            FamilyDiaryListScreen(onBack = { nav.popBackStack() })
        }
        composable(
            Route.FamilyDiaryDetail.path,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStack ->
            val id = backStack.arguments?.getString("id") ?: ""
            FamilyDiaryDetailScreen(diaryId = id, onBack = { nav.popBackStack() })
        }
        composable(Route.FamilyBind.path) {
            FamilyBindScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.ElderHome.path) {
            ElderHomeScreen(
                onOpenRecent = { nav.navigate(Route.ElderDiaryRecent.path) },
                onStartDiary = { nav.navigate(Route.ElderDiaryRecord.path) },
                onOpenSettings = { nav.navigate(Route.ElderSettings.path) },
                onOpenBind = { nav.navigate(Route.ElderBindConfirm.path) },
                onOpenReminder = { id -> nav.navigate(Route.ElderReminderFull.create(id)) },
            )
        }
        composable(Route.ElderBindConfirm.path) {
            ElderBindConfirmScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.ElderDiaryRecord.path) {
            ElderDiaryRecordScreen(
                onBack = { nav.popBackStack() },
                onFinalized = { sessionId ->
                    nav.navigate(Route.ElderDiarySummary.create(sessionId)) {
                        popUpTo(Route.ElderHome.path)
                    }
                },
            )
        }
        composable(
            Route.ElderDiarySummary.path,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStack ->
            val sessionId = backStack.arguments?.getString("sessionId") ?: ""
            ElderDiarySummaryScreen(
                sessionId = sessionId,
                onDone = {
                    nav.navigate(Route.ElderHome.path) {
                        popUpTo(Route.ElderHome.path) { inclusive = true }
                    }
                },
                onEdit = { nav.popBackStack() },
            )
        }
        composable(Route.ElderDiaryRecent.path) {
            ElderDiaryRecentScreen(onBack = { nav.popBackStack() })
        }
        composable(
            Route.ElderReminderFull.path,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStack ->
            val id = backStack.arguments?.getString("id") ?: ""
            ElderReminderFullScreen(reminderId = id, onBack = { nav.popBackStack() })
        }
        composable(Route.ElderSettings.path) {
            ElderSettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
