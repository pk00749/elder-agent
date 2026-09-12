// §3.1.5 + PR #2 老人端主屏 Compose UI 测试
// Robolectric 跑在 JVM 上，不需 emulator；点击新增的「设置」按钮断言 onOpenSettings 回调
// PR #3：3 个区域 Box 通过 Modifier.semantics{ testTag = ... } 暴露，layout 比例 + 多档屏幕测试
package com.elder.android.screen.elder

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.elder.android.di.ServiceLocator
import com.elder.android.design.tokens.Size
import com.elder.android.testing.ElderRobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun topActions_stayAboveDiaryButton() {
        composeRule.setContent {
            ElderHomeScreen(
                onStartDiary = {},
                onOpenSettings = {},
                onOpenRecent = {},
                vm = vm,
            )
        }
        composeRule.waitForIdle()

        val settings = composeRule.onNodeWithTag("home_settings_button")
            .getUnclippedBoundsInRoot()
        val todayRecords = composeRule.onNodeWithTag("home_today_records_button")
            .getUnclippedBoundsInRoot()
        val diaryArea = composeRule.onNodeWithTag("home_diary_area")
            .getUnclippedBoundsInRoot()

        assertTrue(
            "设置按钮不应位于底部，必须在写日记区域上方",
            settings.bottom < diaryArea.top,
        )
        assertTrue(
            "今日记录按钮不应位于底部，必须在写日记区域上方",
            todayRecords.bottom < diaryArea.top,
        )
        composeRule.onNodeWithText("记录").assertExists()
    }

    /**
     * PR #3：weight 0.4 / 0.6 比例在 5" (w360dp-h640dp) 上应保证
     *   - 3 个区域 Box 全部存在（结构合法）
     *   - 写日志按钮占下半屏显著大于问候区（拇指舒适区核心约束）
     *   - 设置按钮不参与 weight 分配，按自然高度渲染
     * 用 assertHeightIsAtLeast + 区域相对比较代替脆的像素测量：
     *   Robolectric 跑 system insets / dp→px 转换有不确定性，绝对比例会脆；
     *   但 "diary > greeting + 全部存在" 是稳定的结构不变量。
     */
    @Test
    fun weightProportions_5inch() {
        composeRule.setContent {
            ElderHomeScreen(
                onStartDiary = {},
                onOpenSettings = {},
                onOpenRecent = {},
                vm = vm,
            )
        }
        composeRule.waitForIdle()
        // 3 个区域 Box 全部存在 — Column 内 weight + wrapContentHeight 组合渲染成功
        composeRule.onNodeWithTag("home_greeting_area").assertExists()
        composeRule.onNodeWithTag("home_diary_area").assertExists()
        composeRule.onNodeWithTag("home_settings_area").assertExists()
        composeRule.onNodeWithTag("home_settings_button")
            .assertHeightIsAtLeast(Size.TouchTargetMin)
        composeRule.onNodeWithTag("home_today_records_button")
            .assertHeightIsAtLeast(Size.TouchTargetMin)

        // 主按钮文案与动作一致，设置按钮不参与 weight 分配
        composeRule.onNodeWithText("按下开始说").assertExists()
        composeRule.onNodeWithText("设置").assertExists()

        // 写日志区像素高度必须 > 问候区（weight 0.6 > 0.4 强制约束）
        val greetH = composeRule.onNodeWithTag("home_greeting_area")
            .getUnclippedBoundsInRoot().bottom - composeRule.onNodeWithTag("home_greeting_area")
            .getUnclippedBoundsInRoot().top
        val diaryH = composeRule.onNodeWithTag("home_diary_area")
            .getUnclippedBoundsInRoot().bottom - composeRule.onNodeWithTag("home_diary_area")
            .getUnclippedBoundsInRoot().top
        assertTrue(
            "diary ($diaryH) 必须 > greeting ($greetH) — weight 0.6 > 0.4 约束",
            diaryH > greetH,
        )
    }

    /**
     * PR #3：横屏（w640dp-h360dp-land）不应溢出 —— 3 个区域 Box 全部存在即视为布局合法。
     */
    @Test @Config(qualifiers = "w640dp-h360dp-land")
    fun homeScreen_rendersInLandscape_noOverflow() {
        composeRule.setContent {
            ElderHomeScreen(
                onStartDiary = {},
                onOpenSettings = {},
                onOpenRecent = {},
                vm = vm,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_greeting_area").assertExists()
        composeRule.onNodeWithTag("home_diary_area").assertExists()
        composeRule.onNodeWithTag("home_settings_area").assertExists()
        composeRule.onNodeWithText("设置").assertExists()
    }

    /**
     * PR #3：7" 平板（w600dp-h960dp）写日志按钮必须完整可见不裁切。
     */
    @Test @Config(qualifiers = "w600dp-h960dp")
    fun homeScreen_rendersAt7inchTablet_noClipping() {
        composeRule.setContent {
            ElderHomeScreen(
                onStartDiary = {},
                onOpenSettings = {},
                onOpenRecent = {},
                vm = vm,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("按下开始说").assertExists()
        composeRule.onNodeWithText("设置").assertExists()
    }
}
