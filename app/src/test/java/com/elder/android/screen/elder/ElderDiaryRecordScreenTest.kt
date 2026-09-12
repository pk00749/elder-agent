package com.elder.android.screen.elder

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
    fun recordingActive_hasOnlyOneStopAction() {
        composeRule.setContent {
            RecordingActive(
                state = DiaryRecordUiState(isRecording = true, elapsedMs = 5_000),
                onStop = {},
            )
        }

        composeRule.onNodeWithText("点击停止").assertExists()
        composeRule.onAllNodesWithText("停止录音").assertCountEquals(0)
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
                onStop = {},
            )
        }

        composeRule.onNodeWithText("转写中…").assertExists()
        composeRule.onNodeWithText("今天天气很好").assertExists()
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
}
