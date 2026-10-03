package com.elder.android.screen.elder

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.elder.android.di.ServiceLocator
import com.elder.android.testing.ElderRobolectricTestRunner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h640dp")
class ElderDiaryRecordScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        ServiceLocator.init(app)
    }

    @Test
    fun recordingActive_showsHoldToTalkHint() {
        // v0.x：录音中改按住说话，原 "点击停止" 已删除（strings.xml）
        composeRule.setContent {
            RecordingActive(
                state = DiaryRecordUiState(isRecording = true, elapsedMs = 5_000),
                onPress = {},
                onRelease = {},
            )
        }

        composeRule.onNodeWithText("按住说话").assertIsDisplayed()
        // 旧 tap-to-stop 文案已删除，应不可见
        composeRule.onAllNodesWithText("点击停止").assertCountEquals(0)
        // 提示行：按住下方按钮开始说话
        composeRule.onNodeWithText("按住下方按钮开始说话").assertIsDisplayed()
    }

    @Test
    fun recordingActive_showsPartialTranscriptWhileRecording() {
        composeRule.setContent {
            RecordingActive(
                state = DiaryRecordUiState(
                    isRecording = true,
                    elapsedMs = 2_000,
                    transcript = "今天天气很好",
                ),
                onPress = {},
                onRelease = {},
            )
        }

        composeRule.onNodeWithText("转写中…").assertIsDisplayed()
        composeRule.onNodeWithText("今天天气很好").assertIsDisplayed()
    }

    @Test
    fun recordingActive_withoutDelta_showsTranscriptPlaceholder() {
        composeRule.setContent {
            RecordingActive(
                state = DiaryRecordUiState(isRecording = true, elapsedMs = 1_000),
                onPress = {},
                onRelease = {},
            )
        }

        composeRule.onNodeWithText("正在听，您说的话会显示在这里…").assertIsDisplayed()
    }

    @Test
    fun grantedMicrophone_doesNotShowPermissionWarning() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)

        composeRule.setContent {
            ElderDiaryRecordScreen(
                onBack = {},
                onDone = {},
                vm = ElderDiaryRecordViewModel(app),
            )
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("需要麦克风权限才能写日记").assertCountEquals(0)
    }

    // v0.x 新增：「已录音」状态 = RecordedState（白底黑字 + 语音回听卡 + 转写）

    @Test
    fun recordedState_rendersPlayButtonAndTranscript() {
        composeRule.setContent {
            RecordedState(
                state = DiaryRecordUiState(
                    transcript = "今天天气很好",
                    savedId = 1L,
                    elapsedMs = 34_000,
                ),
                onPlay = {},
                onRedo = {},
                onDone = {},
            )
        }

        // 顶部「✓ 已录音」+ 时长（substring 避免 Unicode「✓」与断言器互动）
        composeRule.onNodeWithText("已录音", substring = true).assertIsDisplayed()
        // 「录音 1」音频卡标题
        composeRule.onNodeWithText("录音 1", substring = true).assertIsDisplayed()
        // 完整转写正文
        composeRule.onNodeWithText("今天天气很好", substring = true).assertIsDisplayed()
        // 「再录一条」+「返回主页」按钮
        composeRule.onNodeWithText("再录一条", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("返回主页", substring = true).assertIsDisplayed()
    }

    @Test
    fun recordedState_redoClearsSavedState() {
        var redoCount = 0
        composeRule.setContent {
            RecordedState(
                state = DiaryRecordUiState(
                    transcript = "今天天气很好",
                    savedId = 1L,
                    elapsedMs = 34_000,
                ),
                onPlay = {},
                onRedo = { redoCount++ },
                onDone = {},
            )
        }

        // 点「再录一条」 → onRedo 触发（VM.retry 后续会清空 savedId/transcript）
        composeRule.onNodeWithText("再录一条").performClick()
        org.junit.Assert.assertEquals(1, redoCount)
    }

    @Test
    fun recordedState_backCallbackInvoked() {
        var doneCount = 0
        composeRule.setContent {
            RecordedState(
                state = DiaryRecordUiState(
                    transcript = "今天天气很好",
                    savedId = 1L,
                    elapsedMs = 34_000,
                ),
                onPlay = {},
                onRedo = {},
                onDone = { doneCount++ },
            )
        }

        composeRule.onNodeWithText("返回主页").performClick()
        org.junit.Assert.assertEquals(1, doneCount)
    }
}
