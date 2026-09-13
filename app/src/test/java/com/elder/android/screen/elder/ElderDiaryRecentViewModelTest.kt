// §3.1.7 老人端「今日记录」时间轴屏 ViewModel 测试
// PR #4：ttsPlay(text) 必须存在（空态 ElderEmptyState 的 ▶ 回调）
package com.elder.android.screen.elder

import com.elder.android.testing.ElderRobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.lang.reflect.Modifier

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ElderDiaryRecentViewModelTest {

    /**
     * PR #4：ElderEmptyState 组件回调 onTtsClick，ViewModel 必须暴露 `fun ttsPlay(text: String)`。
     * 当前 MVP 不接千问 TTS SDK（§A.1），但保留签名作为后续接入点，
     * 且让 UI ↔ ViewModel 耦合点固定，避免组件改 schema 时动到屏幕。
     */
    @Test
    fun elderDiaryRecentViewModel_exposesTtsPlay() {
        val method = ElderDiaryRecentViewModel::class.java.declaredMethods
            .firstOrNull { it.name == "ttsPlay" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
        assertNotNull(
            "ElderDiaryRecentViewModel 必须暴露 fun ttsPlay(text: String)（PR #4 空态 ElderEmptyState 接入）",
            method,
        )
        // 签名必须是 public（让 UI 直接调用）
        assertEquals(
            "ttsPlay 必须是 public 方法",
            false,
            Modifier.isPrivate(method!!.modifiers),
        )
    }
}
