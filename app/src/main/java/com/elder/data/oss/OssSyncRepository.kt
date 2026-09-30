// 对应 docs/v0.10.0.md §6.5：OssSyncRepository。
// 串联 OssSyncClient + DiaryDao + OssConfigDao;负责:
//   1. 加载 oss_config(明文 access key 由 Keystore 解密后传入 client)
//   2. syncDiary(diaryId) → 上传音频 + 文本 → 写回 diary_entry_local OSS 字段
//   3. retryPending(limit) → 扫 pending/failed 行循环同步
//   4. enqueueDebounced() → 1 次/分钟节流防抖动
package com.elder.android.data.oss

import android.content.Context
import android.util.Log
import com.elder.android.data.db.ElderDatabase
import com.elder.android.data.db.OssConfigEntity
import com.elder.android.data.crypto.OssKeyCipher
import com.elder.android.data.oss.OssSyncActions
import java.io.File

private const val TAG = "OssSyncRepository"

class OssSyncRepository(
    private val appContext: Context,
    private val client: OssSyncClient,
    private val keyStore: OssKeyCipher,
    private val db: ElderDatabase = ElderDatabase.get(appContext),
) : OssSyncActions {
    private val dao = db.diaryDao()
    private val ossDao = db.ossConfigDao()
    @Volatile private var lastEnqueueMs: Long = 0L

    /**
     * 同步单条 diary:上传音频 + 文本 → 写回 oss_sync_status='synced'。
     * 失败:写回 oss_last_error + oss_attempts+1;>=3 → status='failed'。
     */
    override suspend fun syncDiary(diaryId: Long): SyncOutcome {
        val cfg = ossDao.get() ?: return SyncOutcome.Skipped("oss_config 未配置")
        val diary = dao.findById(diaryId) ?: return SyncOutcome.Skipped("diary $diaryId 不存在")
        if (diary.ossSyncStatus == "synced") return SyncOutcome.Skipped("已同步")

        val decrypted = decryptCredentials(cfg)
            ?: return SyncOutcome.Failed(OssError.OSS_AUTH_FAILED, "凭据解密失败")
        val akId = decrypted.first ?: return SyncOutcome.Failed(OssError.OSS_AUTH_FAILED, "AccessKeyId 为空")
        val akSecret = decrypted.second ?: return SyncOutcome.Failed(OssError.OSS_AUTH_FAILED, "AccessKeySecret 为空")
        val sts = decrypted.third

        // 上传音频
        val prefix = cfg.prefix.trim('/')
        val audioKey = "${prefix}/audio/${diary.id}_${System.currentTimeMillis()}.m4a"
        val audioFile = File(diary.audioPath)
        if (!audioFile.exists()) {
            markFailed(diaryId, OssError.OSS_BAD_REQUEST, "audio_path 不存在: ${diary.audioPath}")
            return SyncOutcome.Failed(OssError.OSS_BAD_REQUEST, "audio_path 不存在")
        }

        return try {
            markSyncing(diaryId)
            client.putObject(
                prefix = "",
                key = audioKey,
                file = audioFile,
                contentType = "audio/mp4",
                endpoint = cfg.endpoint, bucket = cfg.bucket, region = cfg.region,
                accessKeyId = akId, accessKeySecret = akSecret, stsToken = sts,
            )
            // 上传文本
            val textKey = "${prefix}/text/${diary.id}_${System.currentTimeMillis()}.json"
            val body = """{"id":${diary.id},"date":"${diary.date}","text":${quoteJson(diary.text)},"summary":${quoteJson(diary.summary ?: "")},"durationMs":${diary.durationMs}}"""
            client.putText(
                prefix = "",
                key = textKey,
                body = body,
                endpoint = cfg.endpoint, bucket = cfg.bucket, region = cfg.region,
                accessKeyId = akId, accessKeySecret = akSecret, stsToken = sts,
            )
            markSynced(diaryId, "${audioKey};${textKey}")
            SyncOutcome.Success(audioKey)
        } catch (e: OssSyncException) {
            markFailed(diaryId, e.code, e.message ?: "")
            SyncOutcome.Failed(e.code, e.message ?: "")
        } catch (e: Throwable) {
            Log.w(TAG, "syncDiary($diaryId) unexpected: ${e.message}")
            markFailed(diaryId, OssError.OSS_NETWORK_ERROR, e.message ?: "")
            SyncOutcome.Failed(OssError.OSS_NETWORK_ERROR, e.message ?: "")
        }
    }

    /**
     * 扫 pending/failed 行循环同步,limit 限速(默认 20)。
     */
    suspend fun retryPending(limit: Int = 20): Int {
        val pending = dao.pendingForSync(limit)
        var successCount = 0
        for (entry in pending) {
            when (syncDiary(entry.id)) {
                is SyncOutcome.Success -> successCount++
                else -> {}
            }
        }
        return successCount
    }

    /**
     * 1 次/分钟节流;短时多次 save_diary 触发不重复 enqueue。
     * 首次调用(lastEnqueueMs == 0L)永远返回 true,后续 60s 内返回 false。
     */
    override fun enqueueDebounced(now: Long): Boolean {
        synchronized(this) {
            if (lastEnqueueMs != 0L && now - lastEnqueueMs < DEBOUNCE_MS) return false
            lastEnqueueMs = now
            return true
        }
    }

    private suspend fun decryptCredentials(cfg: OssConfigEntity): Triple<String?, String?, String?>? {
        return runCatching {
            Triple(
                keyStore.decrypt(cfg.accessKeyIdEnc),
                keyStore.decrypt(cfg.accessKeySecretEnc),
                cfg.stsTokenEnc?.let { keyStore.decrypt(it) },
            )
        }.getOrNull()
    }

    private suspend fun markSyncing(diaryId: Long) {
        val now = System.currentTimeMillis()
        val diary = dao.findById(diaryId) ?: return
        dao.updateOssSync(
            id = diaryId,
            status = "syncing",
            objectKey = diary.ossObjectKey,
            syncedAt = diary.ossSyncedAt,
            lastError = null,
            attempts = diary.ossAttempts,
            now = now,
        )
    }

    private suspend fun markSynced(diaryId: Long, objectKey: String) {
        val now = System.currentTimeMillis()
        val diary = dao.findById(diaryId) ?: return
        dao.updateOssSync(
            id = diaryId,
            status = "synced",
            objectKey = objectKey,
            syncedAt = now,
            lastError = null,
            attempts = diary.ossAttempts,
            now = now,
        )
    }

    private suspend fun markFailed(diaryId: Long, code: String, message: String) {
        val now = System.currentTimeMillis()
        val diary = dao.findById(diaryId) ?: return
        val newAttempts = diary.ossAttempts + 1
        val finalStatus = if (newAttempts >= 3) "failed" else "failed"  // 立即标 failed,UI 灰显
        dao.updateOssSync(
            id = diaryId,
            status = finalStatus,
            objectKey = diary.ossObjectKey,
            syncedAt = diary.ossSyncedAt,
            lastError = "$code: ${message.take(200)}",
            attempts = newAttempts,
            now = now,
        )
    }

    private fun quoteJson(s: String): String {
        // 简易 JSON 字符串转义;文本 / summary 包含双引号 / 反斜杠 / 换行的场景概率极低
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    }

    sealed class SyncOutcome {
        data class Success(val objectKey: String) : SyncOutcome()
        data class Failed(val code: String, val message: String) : SyncOutcome()
        data class Skipped(val reason: String) : SyncOutcome()
    }

    companion object {
        const val DEBOUNCE_MS = 60_000L  // 1 分钟
    }
}
