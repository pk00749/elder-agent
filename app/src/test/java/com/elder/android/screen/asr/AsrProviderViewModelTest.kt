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
import java.io.File
import java.lang.reflect.Method

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

    /**
     * v0.8.2 防回归：测试按钮 [test] 用的 5s/16kHz/mono sine wave WAV 必须 header 严格 44 字节，
     * fmt chunk 严格 16 字节，并在 offset 22 写 NumChannels。
     * 否则 MiniMax strict wav parser 看到 fmt 与 data chunk 偏移 2 字节 → HTTP 400
     * "bad_request_error: invalid params, Invalid data found when processing input (2013)"。
     * 用 Java 反射访问 private createSineWaveSample，避免给生产代码改可见性。
     */
    @Test
    fun testButton_sineWaveWav_hasStrictRiffHeader() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val m: Method = AsrProviderViewModel::class.java
            .getDeclaredMethod("createSineWaveSample", File::class.java)
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val wav: File = m.invoke(vm, ctx.cacheDir) as File
        try {
            val bytes = wav.readBytes()
            // 文件 = 44 字节头 + 5s * 16000 * 2 字节 PCM = 160044
            assertEquals("WAV file must be 44-byte header + 5s 16kHz mono PCM", 44 + 5 * 16000 * 2, bytes.size)
            // 校验前 44 字节 header
            // RIFF magic
            assertEquals('R'.code.toByte(), bytes[0])
            assertEquals('I'.code.toByte(), bytes[1])
            assertEquals('F'.code.toByte(), bytes[2])
            assertEquals('F'.code.toByte(), bytes[3])
            // WAVE / fmt  / data magic
            assertEquals("WAVE", String(bytes.sliceArray(8..11)))
            assertEquals("fmt ", String(bytes.sliceArray(12..15)))
            assertEquals("data", String(bytes.sliceArray(36..39)))
            // fmt chunk size 必须 = 16（PCM 标准）
            assertEquals(16, readIntLe(bytes, 16))
            // AudioFormat = 1 (PCM)
            assertEquals(1, readShortLe(bytes, 20))
            // NumChannels = 1 (mono)  —— 这是 v0.8.2 修复的关键字段
            assertEquals("v0.8.2 fix: NumChannels must be written at offset 22", 1, readShortLe(bytes, 22))
            // SampleRate = 16000
            assertEquals(16000, readIntLe(bytes, 24))
            // ByteRate = SampleRate * NumChannels * BytesPerSample = 32000
            assertEquals(32000, readIntLe(bytes, 28))
            // BlockAlign = NumChannels * BytesPerSample = 2
            assertEquals(2, readShortLe(bytes, 32))
            // BitsPerSample = 16
            assertEquals(16, readShortLe(bytes, 34))
            // data chunk size = 5s * 16000 * 2 bytes = 160000
            assertEquals(160000, readIntLe(bytes, 40))
            // RIFF chunk size = fileSize - 8 = 160044 - 8 = 160036
            assertEquals(bytes.size - 8, readIntLe(bytes, 4))
        } finally {
            wav.delete()
        }
    }

    private fun readIntLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun readShortLe(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)
}
