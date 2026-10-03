// §3.1.2 老人端录音屏 ViewModel 测试
// PR #4：DiaryRecordUiState 新增 networkFailed 字段；ElderDiaryRecordViewModel 新增 retryAsr() 方法
// v0.x：DiaryRecordUiState 新增 audioLevel/isPlaying/playheadMs/totalMs/playError 字段
//          ElderDiaryRecordViewModel 新增 togglePlay()/dismissPlayError() 方法
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

    // v0.x 新增：白底黑字 + 语音回听卡 字段契约

    @Test
    fun diaryRecordUiState_exposesAudioLevel() {
        // AudioRecorder.readLoop 通过 levelListener 回调更新此字段；HoldToTalkButton 弧度绑定
        val field = DiaryRecordUiState::class.java.declaredFields
            .firstOrNull { it.name == "audioLevel" && it.type == Float::class.javaPrimitiveType }
        assertNotNull("DiaryRecordUiState 必须暴露 audioLevel: Float 字段（v0.x HoldToTalkButton 弧度）", field)
    }

    @Test
    fun diaryRecordUiState_exposesPlaybackFields() {
        // RecordAudioCard 用 isPlaying / playheadMs / totalMs 渲染圆钮 / 时码 / 进度条
        val fields = DiaryRecordUiState::class.java.declaredFields
        assertNotNull("isPlaying 必须存在", fields.firstOrNull { it.name == "isPlaying" })
        assertNotNull("playheadMs 必须存在", fields.firstOrNull { it.name == "playheadMs" })
        assertNotNull("totalMs 必须存在", fields.firstOrNull { it.name == "totalMs" })
        assertNotNull("playError 必须存在", fields.firstOrNull { it.name == "playError" })
    }

    @Test
    fun elderDiaryRecordViewModel_exposesTogglePlay() {
        // RecordAudioCard 播放圆钮 onClick → vm::togglePlay 引用，签名不对编译就挂
        val method = ElderDiaryRecordViewModel::class.java.declaredMethods
            .firstOrNull { it.name == "togglePlay" && it.parameterCount == 0 }
        assertNotNull("ElderDiaryRecordViewModel 必须暴露 fun togglePlay()（v0.x RecordAudioCard 播放回调）", method)
        assertTrue("togglePlay 必须是 public 方法", Modifier.isPublic(method!!.modifiers))
    }

    @Test
    fun elderDiaryRecordViewModel_exposesDismissPlayError() {
        // ElderToast onDismiss 绑 vm::dismissPlayError（区别于 vm::dismissError 用于 topError）
        val method = ElderDiaryRecordViewModel::class.java.declaredMethods
            .firstOrNull { it.name == "dismissPlayError" && it.parameterCount == 0 }
        assertNotNull(
            "ElderDiaryRecordViewModel 必须暴露 fun dismissPlayError()（v0.x 播放错误 Toast）",
            method,
        )
    }
}
