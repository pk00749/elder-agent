// 对应 prd.md §3.1.9 + AGENTS.md §14：LlmProviderViewModel 单测
// v0.8.1 整改后新增的逻辑分支守护。
package com.elder.android.screen.asr

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.elder.android.data.AsrConfig
import com.elder.android.data.db.AsrProvider
import com.elder.android.data.db.LlmProvider
import com.elder.android.data.db.TtsProvider
import com.elder.android.testing.ElderRobolectricTestRunner
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class LlmProviderViewModelTest : ProviderVmTestBase() {

    private lateinit var vm: LlmProviderViewModel

    @Before fun initVm() {
        vm = LlmProviderViewModel(ApplicationProvider.getApplicationContext<Application>())
        waitForVm()
    }

    @After fun teardownVm() = cleanupVm()

    /** 现状契约：LlmProviderViewModel.init 当前按 llmProvider 把对应 lastTestResult 拷到 state
     * （与 AsrProviderViewModel 强制清空的修复不一致）。本测试锁定当前行为。 */
    @Test
    fun init_copiesLastTestResultByProvider_currentBehavior() = runBlocking {
        repo.save(
            AsrConfig(
                apiKey = "k-qwen",
                qwenLlmApiKey = "k-qwen",
                asrProvider = AsrProvider.MINIMAX_REALTIME,
                ttsProvider = TtsProvider.MINIMAX,
                llmProvider = LlmProvider.QWEN,
                qwenLlmLastTestResult = "{\"error\":\"qwen previous fail\"}",
            ),
        )
        val fresh = LlmProviderViewModel(ApplicationProvider.getApplicationContext<Application>())
        waitForVm()

        val state = fresh.state.value
        assertEquals(LlmProvider.QWEN, state.provider)
        assertEquals("k-qwen", state.apiKey)
        // 现状：把对应 Provider 的 lastTestResult 拷过来
        assertEquals("{\"error\":\"qwen previous fail\"}", state.lastTestResult)
    }

    /** 切到不同 Provider 时 lastTestResult 必须清空（v0.8.1 整改）。 */
    @Test
    fun setProvider_clearsLastTestResult_onDifferentProvider() = runBlocking {
        // 先构造一个有 lastTestResult 的 cfg（通过 init 拷进去）
        repo.save(
            AsrConfig(
                apiKey = "",
                qwenLlmApiKey = "k-qwen",
                asrProvider = AsrProvider.MINIMAX_REALTIME,
                ttsProvider = TtsProvider.MINIMAX,
                llmProvider = LlmProvider.QWEN,
                qwenLlmLastTestResult = "{\"error\":\"qwen previous fail\"}",
            ),
        )
        // 重新构造 VM 让 init 拷进来
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val fresh = LlmProviderViewModel(ctx)
        waitForVm()
        assertNotNull(fresh.state.value.lastTestResult)

        // 切到不同 Provider：lastTestResult 必须清
        fresh.setProvider(LlmProvider.DEEPSEEK)
        assertNull("v0.8.1: setProvider must clear lastTestResult", fresh.state.value.lastTestResult)
        assertEquals(LlmProvider.DEEPSEEK, fresh.state.value.provider)
    }

    /** setProvider 切到同 Provider 时不修改 state。 */
    @Test
    fun setProvider_isNoOp_whenSameProvider() {
        val before = vm.state.value
        vm.setProvider(before.provider)
        // state 引用改变是正常的，但语义上"什么都没变" —— 这里只验证不抛异常且 provider 没变
        assertEquals(before.provider, vm.state.value.provider)
    }

    /** repo 没有 cfg 时，init 必须保留默认 state。 */
    @Test
    fun init_keepsDefaults_whenNoStoredConfig() {
        assertEquals(LlmProvider.MINIMAX, vm.state.value.provider)
        assertEquals("", vm.state.value.apiKey)
        assertNull(vm.state.value.lastTestResult)
        assertNull(vm.state.value.topError)
        assertFalse(vm.state.value.isTesting)
        assertFalse(vm.state.value.savedOk)
    }

    /** dismissError() 必须把 topError 清成 null。 */
    @Test
    fun dismissError_clearsTopError() {
        vm.test()
        assertNotNull("empty apiKey test() must set topError", vm.state.value.topError)
        vm.dismissError()
        assertNull(vm.state.value.topError)
    }

    /** 空 apiKey 调 test() 必须写 topError 且不翻 isTesting。 */
    @Test
    fun test_blankApiKey_setsTopError_withoutEnteringNetworkCall() {
        vm.test()
        val s = vm.state.value
        assertNotNull("empty apiKey must set topError", s.topError)
        assertFalse("empty apiKey must not enter network", s.isTesting)
    }

    /** saveAndBack() 空 apiKey 必须拦截，不回调 onDone。 */
    @Test
    fun saveAndBack_blankApiKey_blocksAndSetsTopError() {
        var doneCalled = false
        vm.saveAndBack(onDone = { doneCalled = true })
        assertFalse("empty apiKey must not call onDone", doneCalled)
        assertNotNull(vm.state.value.topError)
        assertFalse(vm.state.value.savedOk)
    }

    /** 有 apiKey + QWEN 时 saveAndBack 必须把 key 落到 qwenLlmApiKey（独立字段）。 */
    @Test
    fun saveAndBack_qwen_persistsToQwenLlmApiKey() = runBlocking {
        vm.setProvider(LlmProvider.QWEN)
        vm.setApiKey("k-qwen-llm")

        var doneCalled = false
        vm.saveAndBack(onDone = { doneCalled = true })
        waitForVm()

        assertTrue(doneCalled)
        assertTrue(vm.state.value.savedOk)
        assertNull(vm.state.value.topError)

        val saved = repo.current()
        assertNotNull(saved)
        assertEquals(LlmProvider.QWEN, saved!!.llmProvider)
        assertEquals("k-qwen-llm", saved.qwenLlmApiKey)
    }

    /** 有 apiKey + DEEPSEEK 时 saveAndBack 必须把 key 落到 deepseekLlmApiKey。 */
    @Test
    fun saveAndBack_deepseek_persistsToDeepseekLlmApiKey() = runBlocking {
        vm.setProvider(LlmProvider.DEEPSEEK)
        vm.setApiKey("k-deepseek-llm")

        var doneCalled = false
        vm.saveAndBack(onDone = { doneCalled = true })
        waitForVm()

        assertTrue(doneCalled)
        assertTrue(vm.state.value.savedOk)
        assertNull(vm.state.value.topError)

        val saved = repo.current()
        assertNotNull(saved)
        assertEquals(LlmProvider.DEEPSEEK, saved!!.llmProvider)
        assertEquals("k-deepseek-llm", saved.deepseekLlmApiKey)
    }

    /** v0.8.1 新增：cancelTest() 必须清 isTesting，且幂等。 */
    @Test
    fun cancelTest_setsIsTestingFalse_idempotent() {
        vm.cancelTest()
        assertFalse(vm.state.value.isTesting)
        vm.cancelTest()
        assertFalse(vm.state.value.isTesting)
    }

    /** LlmProviderUiState.allRequiredValid：apiKey 非空时为 true。 */
    @Test
    fun allRequiredValid_reflectsApiKey() {
        assertFalse(vm.state.value.allRequiredValid)
        vm.setApiKey("x")
        assertTrue(vm.state.value.allRequiredValid)
    }
}
