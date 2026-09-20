// 对应 prd.md §3.1.9 + AGENTS.md §14：AsrProviderViewModel 单测
// v0.8.1 整改后新增的逻辑分支守护。
package com.elder.android.screen.asr

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.elder.android.data.AsrConfig
import com.elder.android.data.db.AsrProvider
import com.elder.android.data.db.TtsProvider
import com.elder.android.testing.ElderRobolectricTestRunner
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
import kotlinx.coroutines.runBlocking

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AsrProviderViewModelTest : ProviderVmTestBase() {

    private lateinit var vm: AsrProviderViewModel

    @Before fun initVm() {
        vm = AsrProviderViewModel(ApplicationProvider.getApplicationContext<Application>())
        waitForVm()
    }

    @After fun teardownVm() = cleanupVm()

    /** v0.8.1 修：init 必须清空 cfg 里残留的 lastTestResult / topError。 */
    @Test
    fun init_resetsLastTestResultAndTopError_whenStoredConfigHasPriorFailure() = runBlocking {
        repo.save(
            AsrConfig(
                apiKey = "k-bailian",
                asrProvider = AsrProvider.BAILIAN,
                ttsProvider = TtsProvider.MINIMAX,
                lastTestResult = "{\"error\":\"previous upstream 500\"}",
            ),
        )
        val fresh = AsrProviderViewModel(ApplicationProvider.getApplicationContext<Application>())
        waitForVm()

        val state = fresh.state.value
        assertEquals(AsrProvider.BAILIAN, state.provider)
        assertEquals("k-bailian", state.apiKey)
        assertNull("v0.8.1: init must clear lastTestResult", state.lastTestResult)
        assertNull("v0.8.1: init must clear topError", state.topError)
    }

    /** repo 没有 cfg 时，init 必须保留默认 state。 */
    @Test
    fun init_keepsDefaults_whenNoStoredConfig() {
        assertEquals(AsrProvider.MINIMAX_REALTIME, vm.state.value.provider)
        assertEquals("", vm.state.value.apiKey)
        assertNull(vm.state.value.lastTestResult)
        assertNull(vm.state.value.topError)
        assertFalse(vm.state.value.isTesting)
        assertFalse(vm.state.value.savedOk)
    }

    /** setProvider 切到不同 Provider 时 state.provider 必须更新。 */
    @Test
    fun setProvider_updatesProvider_whenDifferent() {
        vm.setProvider(AsrProvider.BAILIAN)
        assertEquals(AsrProvider.BAILIAN, vm.state.value.provider)
        vm.setProvider(AsrProvider.MINIMAX_REALTIME)
        assertEquals(AsrProvider.MINIMAX_REALTIME, vm.state.value.provider)
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

    /** 有 apiKey + 正确 provider 时 saveAndBack 必须真落库。 */
    @Test
    fun saveAndBack_withApiKey_persistsAndSetsSavedOk() = runBlocking {
        vm.setProvider(AsrProvider.BAILIAN)
        vm.setApiKey("k-bailian")

        var doneCalled = false
        vm.saveAndBack(onDone = { doneCalled = true })
        waitForVm()

        assertTrue("with apiKey onDone must be called", doneCalled)
        assertTrue(vm.state.value.savedOk)
        assertNull(vm.state.value.topError)

        val saved = repo.current()
        assertNotNull("saveAndBack must persist", saved)
        assertEquals(AsrProvider.BAILIAN, saved!!.asrProvider)
        assertEquals("k-bailian", saved.apiKey)
    }

    /** v0.8.1 新增：cancelTest() 必须清 isTesting，且幂等。 */
    @Test
    fun cancelTest_setsIsTestingFalse_idempotent() {
        vm.cancelTest()
        assertFalse(vm.state.value.isTesting)
        vm.cancelTest()
        assertFalse(vm.state.value.isTesting)
    }

    /** AsrProviderUiState.allRequiredValid：apiKey 非空时为 true。 */
    @Test
    fun allRequiredValid_reflectsApiKey() {
        assertFalse(vm.state.value.allRequiredValid)
        vm.setApiKey("x")
        assertTrue(vm.state.value.allRequiredValid)
    }
}
