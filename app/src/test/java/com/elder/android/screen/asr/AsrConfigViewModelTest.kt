// 对应 prd.md §3.1.9 + AGENTS.md §14：AsrConfigViewModel 单测
// v0.8.1 关键修复守护：init 订阅 repo.observe()，子页保存后顶层能自动刷新（修"子页保存后顶层不刷新"）。
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AsrConfigViewModelTest : ProviderVmTestBase() {

    private lateinit var vm: AsrConfigViewModel

    @Before fun initVm() {
        vm = AsrConfigViewModel(ApplicationProvider.getApplicationContext<Application>())
        waitForVm()
    }

    @After fun teardownVm() = cleanupVm()

    /** repo 为空时 state 默认值。 */
    @Test
    fun init_emptyRepoState() {
        assertEquals("", vm.state.value.asrProviderRaw)
        assertEquals("", vm.state.value.ttsProviderRaw)
        assertEquals("", vm.state.value.llmProviderRaw)
        assertFalse(vm.state.value.asrConfigured)
        assertFalse(vm.state.value.ttsConfigured)
        assertFalse(vm.state.value.llmConfigured)
    }

    /** v0.8.1 修复：repo 有 cfg 时 init 必须把 Provider raw 同步到 state（订阅 observe 一次）。 */
    @Test
    fun init_subscribesAndReflectsCfg() = runBlocking {
        repo.save(
            AsrConfig(
                apiKey = "k",
                asrProvider = AsrProvider.BAILIAN,
                ttsProvider = TtsProvider.QWEN,
                llmProvider = LlmProvider.QWEN,
            ),
        )
        // 重新构造 VM，触发 init 块订阅 observe + 收到第一帧
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val fresh = AsrConfigViewModel(ctx)
        waitForVm()

        val s = fresh.state.value
        assertEquals("bailian", s.asrProviderRaw)
        assertEquals("qwen", s.ttsProviderRaw)
        assertEquals("qwen", s.llmProviderRaw)
        assertTrue(s.asrConfigured)
        assertTrue(s.ttsConfigured)
        assertTrue(s.llmConfigured)
    }

    /** v0.8.1 关键修复守护：子页 save 后顶层必须刷新（observe 订阅起作用）。 */
    @Test
    fun observe_refreshesAfterRepoUpdate() = runBlocking {
        // 先存一份 BAILIAN
        repo.save(
            AsrConfig(
                apiKey = "k",
                asrProvider = AsrProvider.BAILIAN,
                ttsProvider = TtsProvider.MINIMAX,
                llmProvider = LlmProvider.MINIMAX,
            ),
        )
        // 等 VM 的 collect 拿到第一帧
        waitForVm()
        assertEquals("bailian", vm.state.value.asrProviderRaw)

        // 模拟"子页改了 Provider 并保存" → repo 再 upsert 一条
        repo.save(
            AsrConfig(
                apiKey = "k",
                asrProvider = AsrProvider.MINIMAX_REALTIME,
                ttsProvider = TtsProvider.MINIMAX,
                llmProvider = LlmProvider.DEEPSEEK,
            ),
        )
        // observe 推送新帧，VM 必须自动更新 state
        waitForVm()
        assertEquals(
            "v0.8.1 修复：observe 必须反映 repo 后续 update",
            "minimax_realtime",
            vm.state.value.asrProviderRaw,
        )
        assertEquals("deepseek", vm.state.value.llmProviderRaw)
    }

    /** Provider raw → 老人可读中文标签。 */
    @Test
    fun displayLabels_forKnownProviders() = runBlocking {
        repo.save(
            AsrConfig(
                apiKey = "k",
                asrProvider = AsrProvider.BAILIAN,
                ttsProvider = TtsProvider.QWEN,
                llmProvider = LlmProvider.DEEPSEEK,
            ),
        )
        waitForVm()
        val s = vm.state.value
        assertEquals("百炼", s.asrProviderDisplay)
        assertEquals("千问 Kiki", s.ttsProviderDisplay)
        assertEquals("DeepSeek", s.llmProviderDisplay)
    }

    /** 未识别的 raw 走 fallback：直接显示 raw。 */
    @Test
    fun displayLabels_fallBackToRawForUnknownProvider() = runBlocking {
        repo.save(
            AsrConfig(
                apiKey = "k",
                asrProvider = AsrProvider.MINIMAX_REALTIME,
                ttsProvider = TtsProvider.MINIMAX,
                llmProvider = LlmProvider.MINIMAX,
            ),
        )
        waitForVm()
        // minimax_realtime → "MiniMax"
        assertEquals("MiniMax", vm.state.value.asrProviderDisplay)
        // minimax → "MiniMax"
        assertEquals("MiniMax", vm.state.value.ttsProviderDisplay)
        assertEquals("MiniMax M3", vm.state.value.llmProviderDisplay)
    }

    /** asrConfigured / ttsConfigured / llmConfigured：只有 raw 非空才算。 */
    @Test
    fun configured_reflectsRawIsNotBlank() {
        // 默认都是空 → 全 false
        assertFalse(vm.state.value.asrConfigured)
        assertFalse(vm.state.value.ttsConfigured)
        assertFalse(vm.state.value.llmConfigured)
        // 任意非空 raw → true
        val s = vm.state.value.copy(asrProviderRaw = "x")
        assertTrue(s.asrConfigured)
        assertFalse(s.ttsConfigured)
        assertFalse(s.llmConfigured)
    }

    /** repo.clear() 后 observe 推 null → state 不变（applyConfig 早返回）。 */
    @Test
    fun applyConfig_keepsState_whenCfgBecomesNull() = runBlocking {
        // 先存一份
        repo.save(
            AsrConfig(
                apiKey = "k",
                asrProvider = AsrProvider.BAILIAN,
                ttsProvider = TtsProvider.MINIMAX,
                llmProvider = LlmProvider.MINIMAX,
            ),
        )
        waitForVm()
        assertEquals("bailian", vm.state.value.asrProviderRaw)

        // 清空
        repo.clear()
        waitForVm()
        // applyConfig(c: AsrConfig?) 遇到 null 直接 return，state 保留上一帧
        assertEquals(
            "applyConfig must early-return on null",
            "bailian",
            vm.state.value.asrProviderRaw,
        )
    }
}
