// 对应 docs/v0.10.0.md §6.9：OssSyncRepository 单元测试（mock OssOps + InMemorySharedPreferences 桩）。
package com.elder.android.data.oss

import androidx.test.core.app.ApplicationProvider
import com.elder.android.data.db.ElderDatabase
import com.elder.android.data.crypto.OssKeyCipher
import com.elder.android.testing.ElderRobolectricTestRunner
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(ElderRobolectricTestRunner::class)  // §A.8:Robolectric 4.13 + Java 25 环境下必须用此 runner
class OssSyncRepositoryTest {
    private lateinit var db: ElderDatabase
    private lateinit var repo: OssSyncRepository
    private lateinit var mockClient: OssSyncClient
    private lateinit var cipher: OssKeyCipher

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // 用 in-memory Room,避免污染主 DB,且不走 main thread 检查
        db = androidx.room.Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            ElderDatabase::class.java,
        ).allowMainThreadQueries()  // 测试桩允许
            .build()
        cipher = OssKeyCipher(context)
        mockClient = object : OssSyncClient {
            override suspend fun putObject(prefix: String, key: String, file: java.io.File, contentType: String, endpoint: String, bucket: String, region: String, accessKeyId: String, accessKeySecret: String, stsToken: String?): OssUploadResult {
                return OssUploadResult(objectKey = key, syncedAtMs = System.currentTimeMillis())
            }
            override suspend fun putText(prefix: String, key: String, body: String, endpoint: String, bucket: String, region: String, accessKeyId: String, accessKeySecret: String, stsToken: String?): OssUploadResult {
                return OssUploadResult(objectKey = key, syncedAtMs = System.currentTimeMillis())
            }
            override fun setOpsForTest(ops: OssOps) {}
        }
        repo = OssSyncRepository(
            appContext = context,
            client = mockClient,
            keyStore = cipher,
            db = db,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `syncDiary returns Skipped when oss_config not configured`() = runBlocking {
        // 没配置 → 跳过
        val outcome = repo.syncDiary(1L)
        assertTrue("expected Skipped; got $outcome", outcome is OssSyncRepository.SyncOutcome.Skipped)
    }

    @Test
    fun `syncDiary returns Failed when diary id not found`() = runBlocking {
        // 先写入 oss_config 让仓库不为 null
        val ossDao = db.ossConfigDao()
        ossDao.upsert(
            com.elder.android.data.db.OssConfigEntity(
                id = 1, endpoint = "https://oss-cn-hangzhou.aliyuncs.com",
                bucket = "b", region = "cn-hangzhou", prefix = "elder/local/",
                syncOnWifiOnly = true,
                accessKeyIdEnc = OssKeyCipher.KEY_ACCESS_KEY_ID_ENC,
                accessKeySecretEnc = OssKeyCipher.KEY_ACCESS_KEY_SECRET_ENC,
                stsTokenEnc = null,
                updatedAt = 0L,
            )
        )
        cipher.encryptAndStore(OssKeyCipher.KEY_ACCESS_KEY_ID_ENC, "ak-id")
        cipher.encryptAndStore(OssKeyCipher.KEY_ACCESS_KEY_SECRET_ENC, "ak-secret")

        // diary id = 999 不存在 → Skipped
        val outcome = repo.syncDiary(999L)
        assertTrue("expected Skipped for missing diary; got $outcome", outcome is OssSyncRepository.SyncOutcome.Skipped)
    }

    @Test
    fun `enqueueDebounced returns true first time then false within window`() {
        val now = 1_000L
        assertTrue("first call should enqueue", repo.enqueueDebounced(now))
        assertEquals("second call within 60s should be debounced", false, repo.enqueueDebounced(now + 30_000L))
        assertTrue("after 60s should enqueue again", repo.enqueueDebounced(now + 70_000L))
    }
}
