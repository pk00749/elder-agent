// 对应 prd.md §3.1.9 + AGENTS.md §14：TtsProviderViewModel 单测
// v0.8.1 整改后新增的逻辑分支守护。
package com.elder.android.screen.asr

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.elder.android.data.AsrConfig
import com.elder.android.data.db.AsrProvider
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
class TtsProviderViewModelTest : ProviderVmTestBase() {

    private lateinit var vm: TtsProviderViewModel

    @Before fun initVm() {
        vm = TtsProviderViewModel(ApplicationProvider.getApplicationContext<Application>())
        waitForVm()
    }

    @After fun teardownVm() = cleanupVm()

    /**
     * 现状契约：TtsProviderViewModel.init 当前会把 cfg.ttsMinimaxLastTestResult 拷到 state，
     * 不像 AsrProviderViewModel.init 那样强制清空（v0.8.1 漏改）。本测试锁定当前行为，
     * 并提醒：「若 UX 要求跨 VM 复用时也清空残留红条，须同时改 init + AsrProviderViewModel 对齐」。
     */
    @Test
    fun init_copiesLastTestResultFromCfg_currentBehavior() = runBlocking {
        repo.save(
            AsrConfig(
                apiKey = "",
                ttsMinimaxApiKey = "k-minimax-tts",
                asrProvider = AsrProvider.MINIMAX_REALTIME,
                ttsProvider = TtsProvider.MINIMAX,
                ttsMinimaxLastTestResult = "{\"error\":\"previous upstream 500\"}",
            ),
        )
        val fresh = TtsProviderViewModel(ApplicationProvider.getApplicationContext<Application>())
        waitForVm()

        val state = fresh.state.value
        assertEquals(TtsProvider.MINIMAX, state.provider)
        assertEquals("k-minimax-tts", state.apiKey)
        // 现状：lastTestResult 沿用 cfg 的值（v0.8.1 与 AsrProviderViewModel 不一致）
        assertEquals(
            "{\"error\":\"previous upstream 500\"}",
            state.lastTestResult,
        )
    }

    /** repo 没有 cfg 时，init 必须保留默认 state。 */
    @Test
    fun init_keepsDefaults_whenNoStoredConfig() {
        assertEquals(TtsProvider.MINIMAX, vm.state.value.provider)
        assertEquals("", vm.state.value.apiKey)
        assertNull(vm.state.value.lastTestResult)
        assertNull(vm.state.value.topError)
        assertFalse(vm.state.value.isTesting)
        assertFalse(vm.state.value.savedOk)
    }

    /** setProvider 切到不同 Provider 时 state.provider 必须更新。 */
    @Test
    fun setProvider_updatesProvider_whenDifferent() {
        vm.setProvider(TtsProvider.QWEN)
        assertEquals(TtsProvider.QWEN, vm.state.value.provider)
        vm.setProvider(TtsProvider.MINIMAX)
        assertEquals(TtsProvider.MINIMAX, vm.state.value.provider)
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

    /** 有 apiKey + 正确 provider 时 saveAndBack 必须真落库，且切到 QWEN 时落 apiKey（共用）。 */
    @Test
    fun saveAndBack_withApiKey_persistsAndSetsSavedOk() = runBlocking {
        vm.setProvider(TtsProvider.QWEN)
        vm.setApiKey("k-qwen-tts")

        var doneCalled = false
        vm.saveAndBack(onDone = { doneCalled = true })
        waitForVm()

        assertTrue("with apiKey onDone must be called", doneCalled)
        assertTrue(vm.state.value.savedOk)
        assertNull(vm.state.value.topError)

        val saved = repo.current()
        assertNotNull("saveAndBack must persist", saved)
        assertEquals(TtsProvider.QWEN, saved!!.ttsProvider)
        // QWEN 走共用 apiKey 字段，不走 ttsMinimaxApiKey
        assertEquals("k-qwen-tts", saved.apiKey)
    }

    /** v0.8.1 新增：cancelTest() 必须清 isTesting，且幂等。 */
    @Test
    fun cancelTest_setsIsTestingFalse_idempotent() {
        vm.cancelTest()
        assertFalse(vm.state.value.isTesting)
        vm.cancelTest()
        assertFalse(vm.state.value.isTesting)
    }

    /** TtsProviderUiState.allRequiredValid：apiKey 非空时为 true。 */
    @Test
    fun allRequiredValid_reflectsApiKey() {
        assertFalse(vm.state.value.allRequiredValid)
        vm.setApiKey("x")
        assertTrue(vm.state.value.allRequiredValid)
    }
}
