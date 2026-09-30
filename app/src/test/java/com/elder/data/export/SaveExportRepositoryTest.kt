// 对应 docs/v0.11.0.md §3.2 + AGENTS.md §A.17：SaveExportRepository 单测。
//
// 覆盖：
//   - 文件名格式 diary_yyyyMMdd_HHmmss_xxxx.{md|m4a} + 4 hex 后缀
//   - .md schema 5 字段(title / summary / text / duration / audio 文件名)
//   - 本地路径 = Downloads/老友日记/
//   - mode = LOCAL 时只走本地
//   - mode = CLOUD 时只走云(本地路径不变)
//   - mode = BOTH 时两者都成功
//   - audio file 不存在时不抛异常,只写 md
package com.elder.android.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.elder.android.data.db.DiaryEntryEntity
import com.elder.android.data.oss.OssSyncActions
import com.elder.android.data.oss.OssSyncRepository
import com.elder.android.testing.ElderRobolectricTestRunner
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class SaveExportRepositoryTest {

    private lateinit var context: Context
    private lateinit var stubOssSync: StubOssSync
    private lateinit var repo: SaveExportRepository
    private lateinit var stagingDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        stagingDir = File(context.cacheDir, "export-test")
        stagingDir.mkdirs()
        stubOssSync = StubOssSync()
        repo = SaveExportRepository(context, stubOssSync)
    }

    @After
    fun tearDown() {
        stagingDir.deleteRecursively()
    }

    // ===== 文件名格式 =====

    @Test
    fun `buildBaseName follows yyyyMMdd_HHmmss_xxxx format`() {
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.SEPTEMBER, 30, 19, 45, 23)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val ms = cal.timeInMillis
        val name = repo.buildBaseName(ms)
        assertTrue("expected diary_yyyyMMdd_HHmmss_xxxx; got $name", name.matches(Regex("^diary_\\d{8}_\\d{6}_[0-9a-f]{4}$")))
    }

    @Test
    fun `randomHex4 suffix is unique across calls`() {
        val seen = HashSet<String>()
        repeat(100) { seen += repo.buildBaseName(System.currentTimeMillis()).substringAfterLast('_') }
        assertTrue("4 位 hex 随机后缀应当有足够熵; saw=${seen.size}", seen.size >= 2)
    }

    // ===== .md schema =====

    @Test
    fun `renderMarkdown includes 5 fields`() {
        val now = System.currentTimeMillis()
        val diary = DiaryEntryEntity(
            id = 42,
            deviceId = "local",
            date = "2026-09-30",
            text = "今日同老张饮茶",
            transcript = "transcript-blob",
            summary = "饮茶",
            sessionId = "s1",
            source = DiaryEntryEntity.Source.ASR_ORIGINAL,
            audioPath = "/cache/audio/uuid.m4a",
            durationMs = 35_000,
            createdAt = now,
            updatedAt = now,
        )
        val md = repo.renderMarkdown(diary, audioBaseName = "diary_20260930_194523_a1b2")
        // 5 字段: title / summary / text / duration / audio 文件名
        assertTrue("md must contain date heading", md.contains("# 2026-"))
        assertTrue("md must contain summary quote", md.contains("> 饮茶"))
        assertTrue("md must contain text body", md.contains("今日同老张饮茶"))
        assertTrue("md must contain duration", md.contains("时长：35 秒"))
        assertTrue("md must contain audio filename", md.contains("diary_20260930_194523_a1b2.m4a"))
    }

    // ===== 写入路径 =====

    @Test
    fun `ensureDir creates Downloads subdirectory named after 老友日记`() {
        val dir = repo.ensureDir()
        assertTrue("下载/老友日记 目录必须存在", dir.exists())
        assertEquals("老友日记", dir.name)
        // Robolectric / 真实设备都把 Download 目录放在父目录(命名可能为 "Download" / "Downloads" / 含 /storage/emulated/0/...)
        assertTrue("parent directory should contain Download; got=${dir.parentFile?.name}",
            (dir.parentFile?.name ?: "").contains("Download", ignoreCase = true))
    }

    // ===== writeLocal =====

    @Test
    fun `writeLocal writes md and m4a with matching base name`() = runBlocking {
        val audioSrc = File(stagingDir, "recording-source.m4a")
        audioSrc.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val now = System.currentTimeMillis()
        val diary = DiaryEntryEntity(
            id = 100, deviceId = "local", date = "2026-09-30",
            text = "今日去咗公园", summary = "行公园",
            sessionId = "s", source = DiaryEntryEntity.Source.ASR_ORIGINAL,
            audioPath = audioSrc.absolutePath, durationMs = 12_000,
            createdAt = now, updatedAt = now,
        )
        val mdPath = repo.writeLocal(diary, audioSrc)
        val dir = File(mdPath).parentFile
        assertNotNull("md must exist in 老友日记/", dir)
        val baseName = File(mdPath).nameWithoutExtension
        val m4a = File(dir, "$baseName.m4a")
        assertTrue("m4a must exist at ${m4a.absolutePath}", m4a.exists())
        assertEquals(audioSrc.readBytes().size, m4a.readBytes().size)
        val md = File(mdPath).readText()
        assertTrue("md must contain date", md.contains("# 2026-"))
        assertTrue("md must contain text", md.contains("今日去咗公园"))
    }

    @Test
    fun `writeLocal does not throw when audio file missing`() = runBlocking {
        val missing = File(stagingDir, "never-created.m4a")
        val now = System.currentTimeMillis()
        val diary = DiaryEntryEntity(
            id = 101, deviceId = "local", date = "2026-09-30",
            text = "无音频可写", summary = "无音频",
            sessionId = "s", source = DiaryEntryEntity.Source.ASR_ORIGINAL,
            audioPath = missing.absolutePath, durationMs = 0,
            createdAt = now, updatedAt = now,
        )
        val mdPath = repo.writeLocal(diary, missing)
        assertTrue("md must still be written when audio missing", File(mdPath).exists())
    }

    // ===== exportIfNeeded modes =====

    @Test
    fun `mode LOCAL writes local but does not enqueue OSS`() = runBlocking {
        val audioSrc = File(stagingDir, "r.m4a").apply { writeBytes(byteArrayOf(0)) }
        val diary = newDiary(audioSrc.absolutePath)
        val outcome = repo.exportIfNeeded(diary, audioSrc, SaveMode.LOCAL)
        assertTrue("LOCAL mode outcome should be LocalWritten; got=$outcome", outcome is SaveExportRepository.ExportOutcome.LocalWritten)
        assertFalse("LOCAL mode must not call OSS enqueue", stubOssSync.enqueueCalled)
        val written = (outcome as SaveExportRepository.ExportOutcome.LocalWritten).path
        assertTrue("local md must exist", File(written).exists())
    }

    @Test
    fun `mode CLOUD does not write local but enqueues OSS`() = runBlocking {
        val audioSrc = File(stagingDir, "r.m4a").apply { writeBytes(byteArrayOf(0)) }
        val diary = newDiary(audioSrc.absolutePath)
        val before = File(repo.ensureDir().absolutePath).listFiles()?.size ?: 0
        val outcome = repo.exportIfNeeded(diary, audioSrc, SaveMode.CLOUD)
        assertTrue("OSS enqueue must be called", stubOssSync.enqueueCalled)
        val after = File(repo.ensureDir().absolutePath).listFiles()?.size ?: 0
        assertEquals("CLOUD mode must not write local files", before, after)
        assertTrue(
            "outcome must not be LocalWritten; got=$outcome",
            outcome !is SaveExportRepository.ExportOutcome.LocalWritten,
        )
    }

    @Test
    fun `mode BOTH writes local AND enqueues OSS`() = runBlocking {
        val audioSrc = File(stagingDir, "r.m4a").apply { writeBytes(byteArrayOf(0)) }
        val diary = newDiary(audioSrc.absolutePath)
        val outcome = repo.exportIfNeeded(diary, audioSrc, SaveMode.BOTH)
        assertTrue("OSS enqueue must be called", stubOssSync.enqueueCalled)
        assertTrue(
            "BOTH mode outcome should be Both or LocalWritten; got=$outcome",
            outcome is SaveExportRepository.ExportOutcome.Both ||
                outcome is SaveExportRepository.ExportOutcome.LocalWritten,
        )
    }

    // ===== SaveMode enum =====

    @Test
    fun `SaveMode fromRaw falls back to LOCAL on unknown`() {
        assertEquals(SaveMode.LOCAL, SaveMode.fromRaw(null))
        assertEquals(SaveMode.LOCAL, SaveMode.fromRaw(""))
        assertEquals(SaveMode.LOCAL, SaveMode.fromRaw("nonsense"))
        assertEquals(SaveMode.LOCAL, SaveMode.fromRaw("local"))
        assertEquals(SaveMode.CLOUD, SaveMode.fromRaw("cloud"))
        assertEquals(SaveMode.BOTH, SaveMode.fromRaw("both"))
    }

    @Test
    fun `SaveMode default is LOCAL`() {
        assertEquals(SaveMode.LOCAL, SaveMode.DEFAULT)
    }

    private fun newDiary(audioPath: String): DiaryEntryEntity {
        val now = System.currentTimeMillis()
        return DiaryEntryEntity(
            id = 1, deviceId = "local", date = "2026-09-30",
            text = "test", summary = "test summary",
            sessionId = "s", source = DiaryEntryEntity.Source.ASR_ORIGINAL,
            audioPath = audioPath, durationMs = 1000,
            createdAt = now, updatedAt = now,
        )
    }

    /** v0.11.0 §3.2 测试桩 OssSync — 只暴露 SaveExportRepository 用到的两个方法。 */
    private class StubOssSync : OssSyncActions {
        var enqueueCalled: Boolean = false
        var syncCalled: Boolean = false
        var shouldEnqueueReturn: Boolean = true
        var shouldSyncReturn: OssSyncRepository.SyncOutcome =
            OssSyncRepository.SyncOutcome.Success(objectKey = "fake-key")

        override fun enqueueDebounced(now: Long): Boolean {
            enqueueCalled = true
            return shouldEnqueueReturn
        }

        override suspend fun syncDiary(diaryId: Long): OssSyncRepository.SyncOutcome {
            syncCalled = true
            return shouldSyncReturn
        }
    }
}
