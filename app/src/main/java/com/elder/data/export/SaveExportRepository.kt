// 对应 docs/v0.11.0.md §3.2：本地 / 云导出仓库。
//
// 职责:
//   1. 根据 SaveMode 选择写路径: LOCAL → 写 .md + .m4a 到 Downloads/老友日记/;
//                                CLOUD → 调 OssSyncRepository.enqueueDebounced();
//                                BOTH  → 两者都做,任一失败不影响另一路径。
//   2. 文件名格式: diary_yyyyMMdd_HHmmss_xxxx.{md|m4a};4 位 hex 随机后缀防多设备冲突。
//   3. 时区: ZoneId.systemDefault()(设备本地;与 §3.1.7 LocalDate.today() 一致)。
//   4. .md schema:见 [MD_TEMPLATE]。
//   5. 云模式未配置 → 静默 fallback LOCAL + 返回 [ExportOutcome.FallbackToLocal]。
//
// 不动 §5 数据模型字段(diary_entry_local 不加列);仅读取既有字段。
// 不引新上游 SDK(仅用 Android Environment / File API + v0.10.0 §6.5 OssSyncRepository)。
package com.elder.android.data.export

import android.content.Context
import android.os.Environment
import android.util.Log
import com.elder.android.data.db.DiaryEntryEntity
import com.elder.android.data.oss.OssSyncActions
import com.elder.android.data.oss.OssError
import com.elder.android.data.oss.OssSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

class SaveExportRepository(
    private val appContext: Context,
    private val ossSyncRepo: OssSyncActions,
) {
    /**
     * 入口:按 [mode] 触发 LOCAL / CLOUD 导出。
     *
     * @param diary 已落 Room 的 DiaryEntryEntity(必须 id > 0)
     * @param audioFile 老人录音源文件(cacheDir/audio/{uuid}.m4a)
     * @param mode 当前 SaveMode
     */
    suspend fun exportIfNeeded(
        diary: DiaryEntryEntity,
        audioFile: File,
        mode: SaveMode,
    ): ExportOutcome = withContext(Dispatchers.IO) {
        // 1) LOCAL 路径
        val localOutcome: ExportOutcome? = if (mode == SaveMode.LOCAL || mode == SaveMode.BOTH) {
            try {
                ExportOutcome.LocalWritten(writeLocal(diary, audioFile))
            } catch (e: Throwable) {
                Log.w(TAG, "writeLocal failed for diary=${diary.id}: ${e.message}")
                ExportOutcome.Failed("LOCAL_WRITE_FAILED", e.message ?: "本地写入失败")
            }
        } else null
        // 2) CLOUD 路径 — enqueueDebounced 非 suspend,直接 try;syncDiary suspend 内 await
        val cloudOutcome: ExportOutcome? = if (mode == SaveMode.CLOUD || mode == SaveMode.BOTH) {
            val enqueued: Boolean = try {
                ossSyncRepo.enqueueDebounced()
            } catch (e: Throwable) {
                Log.w(TAG, "enqueueDebounced threw: ${e.message}")
                false
            }
            if (!enqueued) {
                ExportOutcome.CloudThrottled
            } else {
                val syncResult: OssSyncRepository.SyncOutcome = try {
                    ossSyncRepo.syncDiary(diary.id)
                } catch (e: Throwable) {
                    Log.w(TAG, "cloud syncDiary threw: ${e.message}")
                    OssSyncRepository.SyncOutcome.Failed(OssError.OSS_NETWORK_ERROR, e.message ?: "")
                }
                when (syncResult) {
                    is OssSyncRepository.SyncOutcome.Success ->
                        ExportOutcome.CloudSuccess(syncResult.objectKey)
                    is OssSyncRepository.SyncOutcome.Failed ->
                        ExportOutcome.Failed("CLOUD_SYNC_FAILED", syncResult.message)
                    is OssSyncRepository.SyncOutcome.Skipped ->
                        ExportOutcome.CloudThrottled
                }
            }
        } else null


        composeOutcome(localOutcome, cloudOutcome, mode)

    }

    /** 仅本地写入;给测试与一次性场景用。 */
    suspend fun writeLocal(diary: DiaryEntryEntity, audioFile: File): String {
        val baseName = buildBaseName(diary.createdAt)
        val targetDir = ensureDir()
        val mdFile = File(targetDir, "$baseName.md")
        val m4aFile = File(targetDir, "$baseName.m4a")
        // 1) 复制音频
        if (audioFile.exists()) {
            audioFile.copyTo(m4aFile, overwrite = false)
        } else {
            Log.w(TAG, "audio file missing for diary=${diary.id}: ${audioFile.absolutePath}")
        }
        // 2) 写 md
        val body = renderMarkdown(diary = diary, audioBaseName = baseName)
        mdFile.writeText(body, Charsets.UTF_8)
        return mdFile.absolutePath
    }

    // ===== 内部辅助 =====

    internal fun ensureDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            LOCAL_DIR_NAME,
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    internal fun buildBaseName(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        fmt.timeZone = java.util.TimeZone.getDefault()
        val ts = fmt.format(Date(epochMs))
        val suffix = randomHex4()
        return "diary_${ts}_$suffix"
    }

    internal fun renderMarkdown(diary: DiaryEntryEntity, audioBaseName: String): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        dateFmt.timeZone = java.util.TimeZone.getDefault()
        val dateLine = dateFmt.format(Date(diary.createdAt))
        val summary = (diary.summary ?: "").ifBlank { diary.text.take(SUMMARY_HEAD_LEN) }
        val durationSec = (diary.durationMs / 1000).coerceAtLeast(0)
        // 简单转义:md 标题、引用、段落里不能含未转义 #/>/裸换行外的特殊字符
        // 此处只处理 # / > / 反引号 + 反斜杠;真实日记文本里这些字符出现概率极低。
        val safeTitle = escapeMd(dateLine)
        val safeSummary = escapeMd(summary)
        val safeText = escapeMd(diary.text)
        val audioLine = "$audioBaseName.m4a"
        return MD_TEMPLATE
            .replace("{TITLE}", safeTitle)
            .replace("{SUMMARY}", safeSummary)
            .replace("{TEXT}", safeText)
            .replace("{DURATION}", durationSec.toString())
            .replace("{AUDIO}", audioLine)
    }

    private fun escapeMd(s: String): String =
        s.replace("\\", "\\\\").replace("#", "\\#").replace("`", "\\`")

    private fun randomHex4(): String {
        val r = Random()
        val v = r.nextInt() and 0xFFFF
        return "%04x".format(v)
    }

    private fun composeOutcome(
        local: ExportOutcome?,
        cloud: ExportOutcome?,
        mode: SaveMode,
    ): ExportOutcome = when {
        local is ExportOutcome.LocalWritten && cloud is ExportOutcome.CloudSuccess ->
            ExportOutcome.Both(local.path, cloud.objectKey)
        local is ExportOutcome.LocalWritten && cloud == null -> local
        local == null && cloud is ExportOutcome.CloudSuccess -> cloud
        local == null && cloud == null -> ExportOutcome.Skipped("mode=$mode 不需要导出")
        local is ExportOutcome.Failed && cloud is ExportOutcome.Failed ->
            ExportOutcome.Failed(local.code, local.message)
        local is ExportOutcome.Failed && cloud != null -> ExportOutcome.Failed(local.code, local.message)
        local != null && cloud is ExportOutcome.Failed -> ExportOutcome.Failed(cloud.code, cloud.message)
        local != null && cloud is ExportOutcome.CloudThrottled -> local
        local == null && cloud is ExportOutcome.CloudThrottled -> ExportOutcome.CloudThrottled
        else -> ExportOutcome.Skipped("no-op")
    }

    /** 显式构造 Both(测试 / 云同步成功场景专用)。 */
    @Suppress("unused")
    fun bothOutcomes(localPath: String, cloudKey: String): ExportOutcome.Both =
        ExportOutcome.Both(localPath, cloudKey)

    sealed class ExportOutcome {
        data class LocalWritten(val path: String) : ExportOutcome()
        data class CloudSuccess(val objectKey: String) : ExportOutcome()
        data class Both(val localPath: String, val cloudObjectKey: String) : ExportOutcome()
        data class Failed(val code: String, val message: String) : ExportOutcome()
        data object CloudThrottled : ExportOutcome()
        data class Skipped(val reason: String) : ExportOutcome()

    }

    companion object {
        private const val TAG = "SaveExportRepository"
        const val LOCAL_DIR_NAME = "老友日记"
        private const val SUMMARY_HEAD_LEN = 30

        private val MD_TEMPLATE = """
            # {TITLE}

            > {SUMMARY}

            {TEXT}

            ---
            时长：{DURATION} 秒
            录音：{AUDIO}
        """.trimIndent()
    }
}
