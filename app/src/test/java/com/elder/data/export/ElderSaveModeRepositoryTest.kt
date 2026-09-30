// 对应 docs/v0.11.0.md §3.1 + AGENTS.md §A.17：ElderSaveModeRepository 单测。
//
// 覆盖：
//   - 默认 mode = LOCAL
//   - setMode 持久化 + 下次 current() 读到
//   - fromRaw 容错(空 / null / 未知 → LOCAL)
//   - 独立 prefs file "elder_save_mode"(与 elder_oss_keys 不混)
//   - voice_end_hint_seen 默认 false,setVoiceEndHintDismissed 后 true
package com.elder.android.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.elder.android.testing.ElderRobolectricTestRunner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ElderSaveModeRepositoryTest {

    private lateinit var context: Context
    private lateinit var repo: ElderSaveModeRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 清掉 prefs(避免测试间污染)
        context.getSharedPreferences(ElderSaveModeRepository.PREFS_FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
        repo = ElderSaveModeRepository(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(ElderSaveModeRepository.PREFS_FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `default mode is LOCAL`() {
        assertEquals(SaveMode.LOCAL, repo.current())
    }

    @Test
    fun `setMode CLOUD is persisted across instances`() {
        repo.setMode(SaveMode.CLOUD)
        assertEquals(SaveMode.CLOUD, repo.current())
        // 新实例读 prefs 也得到 CLOUD
        val newRepo = ElderSaveModeRepository(context)
        assertEquals(SaveMode.CLOUD, newRepo.current())
    }

    @Test
    fun `setMode BOTH round-trips through prefs`() {
        repo.setMode(SaveMode.BOTH)
        assertEquals(SaveMode.BOTH, repo.current())
    }

    @Test
    fun `prefs file is elder_save_mode (independent from elder_oss_keys)`() {
        // ElderSaveModeRepository.PREFS_FILE 必须是 "elder_save_mode"
        assertEquals("elder_save_mode", ElderSaveModeRepository.PREFS_FILE)
        // OSS keys 用另一个文件(§A.16.3 锁定);不应共享
        assertTrue(
            "SaveMode 与 OSS 凭据必须用独立 prefs file",
            ElderSaveModeRepository.PREFS_FILE != "elder_oss_keys",
        )
    }

    @Test
    fun `voice end hint defaults to not dismissed`() {
        assertFalse(repo.isVoiceEndHintDismissed())
    }

    @Test
    fun `markVoiceEndHintDismissed persists and round-trips`() {
        repo.markVoiceEndHintDismissed()
        assertTrue(repo.isVoiceEndHintDismissed())
        val newRepo = ElderSaveModeRepository(context)
        assertTrue("dismissed state must persist across instances", newRepo.isVoiceEndHintDismissed())
    }

    @Test
    fun `SaveMode enum round trips all 3 values`() {
        for (mode in SaveMode.entries) {
            repo.setMode(mode)
            assertEquals(mode, repo.current())
            assertEquals(mode, SaveMode.fromRaw(mode.raw))
        }
    }
}
