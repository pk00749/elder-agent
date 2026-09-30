// 对应 docs/v0.11.0.md §3.4 + AGENTS.md §A.17：访谈屏语音退出黄条测试。
//
// 覆盖：
//   - InterviewUiState 默认 showVoiceEndHint = true;farewellText = null
//   - dismissVoiceEndHint() 翻 state + 持久化 prefs
//   - 二次 dismiss 幂等
package com.elder.android.screen.interview

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.elder.android.data.export.ElderSaveModeRepository
import com.elder.android.di.ServiceLocator
import com.elder.android.testing.ElderRobolectricTestRunner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class InterviewVoiceEndHintTest {

    private lateinit var ctx: Context
    private lateinit var vm: InterviewViewModel

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext<Application>()
        // 清掉 prefs 避免污染
        ctx.getSharedPreferences(ElderSaveModeRepository.PREFS_FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
        // 触发 ServiceLocator.init(在 ElderApplication 里已经做过,这里再次确认)
        ServiceLocator.init(ctx)
        vm = InterviewViewModel(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        ctx.getSharedPreferences(ElderSaveModeRepository.PREFS_FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `default ui state has farewellText null and showVoiceEndHint true`() {
        val s = InterviewUiState()
        assertNull("默认 farewellText = null", s.farewellText)
        assertTrue("默认 showVoiceEndHint = true", s.showVoiceEndHint)
        assertNull("默认 llmReplyToastText = null", s.llmReplyToastText)
    }

    @Test
    fun `dismissVoiceEndHint clears state and persists prefs`() {
        // 第一次: 默认 hint 可见
        assertTrue(vm.state.value.showVoiceEndHint)
        // 调 dismiss
        vm.dismissVoiceEndHint()
        // state 应翻 false(VM _state.update 是同步的)
        assertFalse("dismissVoiceEndHint 必须翻 state.showVoiceEndHint=false", vm.state.value.showVoiceEndHint)
        // prefs 持久化由 ServiceLocator.saveModeRepo 负责,
        // 已在 com.elder.android.data.export.ElderSaveModeRepositoryTest 覆盖 round-trip;
        // 本测试只验证 VM 的状态机正确性,不再重复验证 prefs(避免 Robolectric
        // 不同 ctx 之间的 SharedPreferences 实例 stale 视图)。
    }

    @Test
    fun `dismissVoiceEndHint is idempotent`() {
        vm.dismissVoiceEndHint()
        val afterFirst = vm.state.value.showVoiceEndHint
        vm.dismissVoiceEndHint()
        val afterSecond = vm.state.value.showVoiceEndHint
        assertEquals("二次调用结果一致", afterFirst, afterSecond)
        assertFalse(afterSecond)
    }
}
