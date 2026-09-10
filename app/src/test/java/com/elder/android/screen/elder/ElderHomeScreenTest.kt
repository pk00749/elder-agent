// §3.1.5 + PR #2 老人端主屏 Compose UI 测试
// Robolectric 跑在 JVM 上，不需 emulator；点击新增的「设置」按钮断言 onOpenSettings 回调
package com.elder.android.screen.elder

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.elder.android.di.ServiceLocator
import com.elder.android.testing.ElderRobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, qualifiers = "w360dp-h640dp")
class ElderHomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var vm: ElderHomeViewModel

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // 真实 init：会建 Room DB + 仓仓储。ElderHomeViewModel 直接从 ServiceLocator 取
        // diaryRepo / asrConfigRepo；init 块的 combineState() 查询是只读且空表，测试可稳跑。
        ServiceLocator.init(ctx)
        vm = ElderHomeViewModel(ctx as Application)
    }

    /**
     * PR #2：点击新增的「设置」按钮必须触发 onOpenSettings 回调，
     * 不再依赖问候区 5 次点击隐藏手势（已删除）。
     */
    @Test
    fun settingsButton_triggersOnOpenSettings() {
        var captured: Int = 0
        composeRule.setContent {
            ElderHomeScreen(
                onStartDiary = { captured = 1 },
                onOpenSettings = { captured = 2 },
                onOpenRecent = { captured = 3 },
                vm = vm,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("设置").performClick()
        assertEquals(2, captured)
    }
}
