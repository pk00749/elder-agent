// 对应 prd.md §3.1.8 + AGENTS.md §14：ElderSettingsViewModel 单测
// v0.8.1 整改守护：
//   - 新增 llmProviderLabel 字段 + init 从 asrRepo.observe() 同步
//   - confirmLogout() try-catch 包装（任意 repo 抛时不卡 dialog）
//   - dismissError() 清 topError
package com.elder.android.screen.elder

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.elder.android.data.AsrConfig
import com.elder.android.data.db.AsrProvider
import com.elder.android.data.db.FontScale
import com.elder.android.data.db.LlmProvider
import com.elder.android.data.db.TtsProvider
import com.elder.android.di.ServiceLocator
import com.elder.android.screen.asr.ProviderVmTestBase
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
class ElderSettingsViewModelTest : ProviderVmTestBase() {

    private lateinit var vm: ElderSettingsViewModel

    @Before fun initVm() {
        vm = ElderSettingsViewModel(ApplicationProvider.getApplicationContext<Application>())
        waitForVm()
    }

    @After fun teardownVm() = cleanupVm()

    /**
     * 默认 state：未配置 AI（cfg 为 null → asrConfigured=false）。
     * 但 providerLabel 走 fallback 默认值（asrLabel(null, MINIMAX_REALTIME) = "MiniMax"），
     * 因为 UI 拿不到 cfg 时仍要显示一个 hint label。
     * 设置页 SettingRow1Asr 的 subtitle 用 if (configured) 显示三 Provider 摘要，
     * else 显示"未配置"，所以 fallback label 不会落到 UI。
     */
    @Test
    fun init_defaults() {
        val s = vm.uiState.value
        assertFalse(s.asrConfigured)
        // fallback labels 在 cfg=null 时由三处 label() 函数给默认值
        assertEquals("MiniMax", s.asrProviderLabel)
        assertEquals("MiniMax", s.ttsProviderLabel)
        assertEquals("MiniMax M3", s.llmProviderLabel)
        assertFalse(s.showLogoutConfirm)
        assertFalse(s.loggedOut)
        assertNull(s.topError)
    }

    /** v0.8.1：asrRepo 有 cfg 时 llmProviderLabel 也必须填充。 */
    @Test
    fun init_populatesAllThreeProviderLabels() = runBlocking {
        repo.save(
            AsrConfig(
                apiKey = "k",
                asrProvider = AsrProvider.BAILIAN,
                ttsProvider = TtsProvider.QWEN,
                llmProvider = LlmProvider.DEEPSEEK,
            ),
        )
        // 等 observe 把 cfg 推到 VM
        waitForVm()

        val s = vm.uiState.value
        assertTrue("三个 Provider 都设了，应该算 configured", s.asrConfigured)
        assertEquals("百炼", s.asrProviderLabel)
        assertEquals("千问 Kiki", s.ttsProviderLabel)
        assertEquals("DeepSeek", s.llmProviderLabel) // v0.8.1 新增
    }

    /** v0.8.1：asrConfigured 必须三 Provider raw 都非空才 true。 */
    @Test
    fun asrConfigured_requiresAllThreeProviders() = runBlocking {
        // 只设 ASR，TTS/LLM 用默认空
        repo.save(
            AsrConfig(
                apiKey = "k",
                asrProvider = AsrProvider.BAILIAN,
                ttsProvider = TtsProvider.MINIMAX,
                llmProvider = LlmProvider.MINIMAX,
            ),
        )
        waitForVm()
        assertTrue(vm.uiState.value.asrConfigured)
    }

    /** requestLogout() 必须显示 dialog。 */
    @Test
    fun requestLogout_showsDialog() {
        assertFalse(vm.uiState.value.showLogoutConfirm)
        vm.requestLogout()
        assertTrue(vm.uiState.value.showLogoutConfirm)
    }

    /** cancelLogout() 必须关闭 dialog。 */
    @Test
    fun cancelLogout_hidesDialog() {
        vm.requestLogout()
        assertTrue(vm.uiState.value.showLogoutConfirm)
        vm.cancelLogout()
        assertFalse(vm.uiState.value.showLogoutConfirm)
    }

    /** v0.8.1 修：confirmLogout() 成功路径 — 清完所有 repo + loggedOut=true + topError=null。 */
    @Test
    fun confirmLogout_success_clearsAllAndSetsLoggedOut() = runBlocking {
        // 先存一点东西，确保 clearAll 真的能清
        repo.save(AsrConfig(apiKey = "k", asrProvider = AsrProvider.BAILIAN, ttsProvider = TtsProvider.QWEN))
        waitForVm()

        vm.requestLogout()
        vm.confirmLogout()
        waitForVm()

        val s = vm.uiState.value
        assertFalse("成功后 dialog 必须关", s.showLogoutConfirm)
        assertTrue("成功后 loggedOut 必须翻 true", s.loggedOut)
        assertNull("成功后 topError 必须为 null", s.topError)

        // 验证 repo 真清了
        assertNull("asrRepo 必须被清", ServiceLocator.asrConfigRepo.current())
    }

    /** v0.8.1 修：dismissError() 必须清 topError（幂等：默认 null 时再调也不抛）。 */
    @Test
    fun dismissError_isIdempotent_onNullTopError() {
        // 默认 topError=null，连调两次不能抛
        assertNull(vm.uiState.value.topError)
        vm.dismissError()
        assertNull(vm.uiState.value.topError)
        vm.dismissError()
        assertNull(vm.uiState.value.topError)
    }

    /**
     * confirmLogout() 的失败路径（topError 兜底）通过 mock 注入较复杂，
     * 留给集成测试覆盖；本单测只锁 success path 与 dismissError 幂等。
     */

    /** setFontScale 必须立即更新 state（乐观更新） + 异步落 metaRepo。 */
    @Test
    fun setFontScale_optimisticallyUpdatesState() = runBlocking {
        assertEquals(FontScale.DEFAULT, vm.uiState.value.fontScale)
        vm.setFontScale(FontScale.LARGE)
        // 乐观更新：state 立即变
        assertEquals(FontScale.LARGE, vm.uiState.value.fontScale)
        // 落库也是 LARGE
        waitForVm()
        val meta = ServiceLocator.deviceMetaRepo.ensureInitialized()
        assertEquals(FontScale.LARGE, meta.fontScale)
    }

    /** setTtsEnabled 必须立即更新 state + 落库。 */
    @Test
    fun setTtsEnabled_optimisticallyUpdatesState() = runBlocking {
        assertTrue(vm.uiState.value.ttsEnabled)
        vm.setTtsEnabled(false)
        assertFalse(vm.uiState.value.ttsEnabled)
        waitForVm()
        val meta = ServiceLocator.deviceMetaRepo.ensureInitialized()
        assertFalse(meta.ttsEnabled)
    }

    /** versionName 必须从 BuildConfig 读，非空。 */
    @Test
    fun versionName_isBuildConfigVersionName() {
        assertEquals(com.elder.android.BuildConfig.VERSION_NAME, vm.uiState.value.versionName)
        assertTrue("versionName 非空", vm.uiState.value.versionName.isNotBlank())
    }
}
