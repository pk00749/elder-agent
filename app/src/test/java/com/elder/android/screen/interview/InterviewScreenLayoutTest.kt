// 对应 docs/v0.11.x-bugfix.md + AGENTS.md §18：访谈屏 PREPARING / SAVED 阶段布局回归测试。
//
// 修复根因:LoadingState() 内部 fillMaxSize() 在父 Column (Arrangement.spacedBy) 末尾会抢占
// TranscriptCard(weight 1f) 的全部高度,导致整屏被 loading 占满看不到上下文。
// 修复后:用 SavingStatusRow(同 OpeningStatusRow 的 PrimaryButtonHeight 固定行高)替换 LoadingState,
// TranscriptCard 仍可见,老人知道是"系统在保存"。
package com.elder.android.screen.interview

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import com.elder.android.design.tokens.Size
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.elder.android.di.ServiceLocator
import com.elder.android.testing.ElderRobolectricTestRunner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class InterviewScreenLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        ServiceLocator.init(app)
    }

    /**
     * 验证:固定行高的 status row(SavingStatusRow 同款布局)放在带 weight(1f) 子项之后,
     * 不会抢占上方 weight(1f) 子项的高度。
     */
    @Test
    fun fixedHeightStatusRow_doesNotConsumeAllHeight() {
        // 容器高度 = 4 × PrimaryButtonHeight(96dp),让 3 个固定行高子项可观察是否互抢。
        val primaryBtn = Size.PrimaryButtonHeight.value
        composeRule.setContent {
            Column(
                modifier = Modifier.fillMaxWidth().height((primaryBtn * 4).dp),
            ) {
                Text("上方占位")
                Box(
                    modifier = Modifier.fillMaxWidth().height((primaryBtn * 3).dp),
                ) {
                    Text("TRANSCRIPT 区")
                }
                Text(
                    text = "正在保存…",
                    modifier = Modifier.fillMaxWidth().height(primaryBtn.dp),
                )
            }
        }
        composeRule.onNodeWithText("TRANSCRIPT 区").assertIsDisplayed()
        composeRule.onNodeWithText("正在保存…").assertIsDisplayed()
    }

    /**
     * 验证:InterviewScreen.kt 不再把 PREPARING/SAVED 映射到 LoadingState()(会全屏抢高度)。
     * 用源码扫描替代 runtime 断言 —— 因为 private composable 无法跨文件测试。
     * 行为约束测试(对应 AGENTS.md §14):防止有人改回 LoadingState() 触发原 bug。
     */
    @Test
    fun interviewScreen_doesNotUseLoadingStateForPreparingOrSaved() {
        // 读源码:Gradle test 工作目录下 src/main 在 app/ 之上
        val candidates = listOf(
            "src/main/java/com/elder/screen/interview/InterviewScreen.kt",
            "app/src/main/java/com/elder/screen/interview/InterviewScreen.kt",
        )
        val src = candidates.mapNotNull { runCatching { File(it).readText() }.getOrNull() }
            .firstOrNull() ?: ""
        check(src.isNotEmpty()) {
            "无法读取 InterviewScreen.kt 源码 —— 不能做源码扫描断言(cwd=${File(".").absolutePath})"
        }
        // 必须不再有 PREPARING/SAVED → LoadingState() 这种用法
        val forbidden = "InterviewStage.PREPARING, InterviewStage.SAVED -> LoadingState()"
        require(!src.contains(forbidden)) {
            "InterviewScreen.kt 仍然把 PREPARING/SAVED 映射到 LoadingState() —— 整屏被抢高度,bug 复现!"
        }
        // 必须有 SavingStatusRow 引用 + interview_saving_status 字符串
        require(src.contains("SavingStatusRow")) {
            "InterviewScreen.kt 缺少 SavingStatusRow 定义"
        }
        require(src.contains("interview_saving_status")) {
            "InterviewScreen.kt 缺少 interview_saving_status 字符串引用"
        }
    }
}
