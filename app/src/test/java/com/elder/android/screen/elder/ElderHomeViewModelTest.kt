// §3.1.5 + PR #2 老人端主屏 ViewModel 测试
// 反射断言 ElderHomeUiState / ElderHomeViewModel 不再暴露 5-tap 隐藏入口相关 API
package com.elder.android.screen.elder

import com.elder.android.testing.ElderRobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ElderHomeViewModelTest {

    /**
     * PR #2：ElderHomeUiState 删字段后只剩 4 个。
     * 旧字段 tapCounter / settingsTrigger / todayRecordJustRecorded 全部下线，
     * 5-tap 隐藏入口已替换为屏幕上的「设置」可见按钮（UI 直接回调 onOpenSettings）。
     */
    @Test
    fun elderHomeUiState_exposesOnlyFourFields() {
        // Kotlin data class 编译器会合成 $stable / $serializer 等字段；过滤掉非业务字段
        val fields = ElderHomeUiState::class.java.declaredFields
            .map { it.name }
            .filter { !it.startsWith("$") && !it.startsWith("<") }
            .toSet()
        assertEquals(
            "ElderHomeUiState 必须只剩 greeting / dateLine / todayRecorded / showAsrHint 四个字段",
            setOf("greeting", "dateLine", "todayRecorded", "showAsrHint"),
            fields,
        )
    }

    /**
     * PR #2：5-tap 触发链路（onGreetingTap + consumeSettingsTrigger + consumeTodayRecordFlag）
     * 全部下线；UI 直接 onClick 回调，不需要 ViewModel 兜底。
     */
    @Test
    fun elderHomeViewModel_doesNotExposeFiveTapApi() {
        // 编译器合成的 access$ 方法（data class copy / componentN 等）一并过滤
        val methods = ElderHomeViewModel::class.java.declaredMethods
            .map { it.name }
            .filter { !it.startsWith("$") && !it.startsWith("access$") }
            .toSet()
        assertNull(
            "onGreetingTap 必须删除（5-tap 隐藏入口已替换为可见按钮）",
            methods.firstOrNull { it == "onGreetingTap" },
        )
        assertNull(
            "consumeSettingsTrigger 必须删除",
            methods.firstOrNull { it == "consumeSettingsTrigger" },
        )
        assertNull(
            "consumeTodayRecordFlag 必须删除（与 todayRecordJustRecorded 字段一起下线）",
            methods.firstOrNull { it == "consumeTodayRecordFlag" },
        )
    }
}
