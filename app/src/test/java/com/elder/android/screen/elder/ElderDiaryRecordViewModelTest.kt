// §3.1.2 老人端录音屏 ViewModel 测试
// PR #4：DiaryRecordUiState 新增 networkFailed 字段；ElderDiaryRecordViewModel 新增 retryAsr() 方法
package com.elder.android.screen.elder

import com.elder.android.testing.ElderRobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.lang.reflect.Modifier

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ElderDiaryRecordViewModelTest {

    /**
     * PR #4：DiaryRecordUiState 必须新增 networkFailed 字段。
     * UI 顶部 NetworkYellowBar 的 visible 直接绑这个字段；
     * 缺字段会导致 Composable 编译失败（state.networkFailed 找不到）。
     */
    @Test
    fun diaryRecordUiState_exposesNetworkFailed() {
        val field = DiaryRecordUiState::class.java.declaredFields
            .firstOrNull { it.name == "networkFailed" && it.type == Boolean::class.javaPrimitiveType }
        assertNotNull(
            "DiaryRecordUiState 必须暴露 networkFailed: Boolean 字段（PR #4 NetworkYellowBar 绑定）",
            field,
        )
    }

    /**
     * PR #4：retryAsr() 必须是 ViewModel 的 public 无参方法，
     * NetworkYellowBar.onRetry 直接回调 vm::retryAsr 引用，签名不对编译就挂。
     */
    @Test
    fun elderDiaryRecordViewModel_exposesRetryAsr() {
        val method = ElderDiaryRecordViewModel::class.java.declaredMethods
            .firstOrNull { it.name == "retryAsr" && it.parameterCount == 0 }
        assertNotNull(
            "ElderDiaryRecordViewModel 必须暴露 fun retryAsr()（PR #4 NetworkYellowBar.onRetry 绑定）",
            method,
        )
        assertTrue(
            "retryAsr 必须是 public 方法",
            Modifier.isPublic(method!!.modifiers),
        )
    }
}
