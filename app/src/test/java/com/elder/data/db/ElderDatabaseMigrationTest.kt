package com.elder.android.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.elder.android.testing.ElderRobolectricTestRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ElderDatabaseMigrationTest {
    private lateinit var context: Context
    private lateinit var db: ElderDatabase
    private val dbName = "migration-2-3.db"

    @Before
    fun createVersion2Database() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE diary_entry_local (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            device_id TEXT NOT NULL,
                            date TEXT NOT NULL,
                            text TEXT NOT NULL,
                            transcript TEXT,
                            source TEXT NOT NULL,
                            audio_path TEXT NOT NULL,
                            duration_ms INTEGER NOT NULL,
                            asr_provider TEXT,
                            asr_model TEXT,
                            asr_confidence REAL,
                            asr_latency_ms INTEGER,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL,
                            deleted_at INTEGER
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX index_diary_entry_local_date_created_at ON diary_entry_local(date, created_at)")
                    db.execSQL("CREATE INDEX index_diary_entry_local_device_id ON diary_entry_local(device_id)")
                    db.execSQL(
                        """
                        CREATE TABLE asr_config (
                            id INTEGER NOT NULL PRIMARY KEY,
                            api_key_enc TEXT NOT NULL,
                            updated_at INTEGER NOT NULL,
                            last_test_result TEXT
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE device_meta (
                            id INTEGER NOT NULL PRIMARY KEY,
                            device_id TEXT NOT NULL,
                            device_token TEXT,
                            font_scale TEXT NOT NULL,
                            tts_enabled INTEGER NOT NULL,
                            timezone TEXT NOT NULL,
                            app_version TEXT NOT NULL,
                            first_launch_at INTEGER NOT NULL,
                            last_active_at INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "INSERT INTO diary_entry_local " +
                            "(device_id,date,text,transcript,source,audio_path,duration_ms,created_at,updated_at) " +
                            "VALUES ('local','2026-09-14','旧日记',NULL,'ASR_ORIGINAL','/tmp/old.wav',1000,1,1)"
                    )
                    db.execSQL(
                        "INSERT INTO asr_config (id,api_key_enc,updated_at,last_test_result) " +
                            "VALUES (1,'api_key',1,NULL)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase.close()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `migration preserves old diary and key then creates v3 tables`() = runTest {
        db = Room.databaseBuilder(context, ElderDatabase::class.java, dbName)
            .addMigrations(
                ElderDatabase.MIGRATION_2_3,
                ElderDatabase.MIGRATION_3_4,
                ElderDatabase.MIGRATION_4_5,  // v0.7.0: asr_config DROP+CREATE，强制重输 4 份 Key
                ElderDatabase.MIGRATION_5_6,  // v0.8.0: asr_config ALTER TABLE +7 列
            )
            .allowMainThreadQueries()
            .build()

        val diary = db.diaryDao().observeAll().first()
        assertEquals(1, diary.size)
        assertEquals("旧日记", diary[0].text)

        // v0.7.0 MIGRATION_4_5 DROP+CREATE asr_config; 旧 api_key 数据丢失
        val config = db.asrConfigDao().get()
        assertEquals(null, config?.apiKeyEnc)
        // 新 schema 列存在但全部空
        assertTrue(config?.minimaxApiKeyEnc == null)
        assertTrue(config?.ttsMinimaxApiKeyEnc == null)

        db.interviewSessionDao().upsert(
            InterviewSessionEntity(
                id = "session",
                status = "ACTIVE",
                turnsJson = "[]",
                createdAt = 1,
                updatedAt = 1,
            )
        )
        assertEquals("session", db.interviewSessionDao().active()?.id)
        assertTrue(db.pendingDiaryDao().all().isEmpty())
    }

    @Test
    fun `migration 4 to 5 drops old asr_config and creates 16-column schema`() = runTest {
        val v4DbName = "migration-4-5.db"
        context.deleteDatabase(v4DbName)
        // Build a v4 database manually
        val v4Config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(v4DbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE diary_entry_local (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            device_id TEXT NOT NULL,
                            date TEXT NOT NULL,
                            text TEXT NOT NULL,
                            transcript TEXT,
                            source TEXT NOT NULL,
                            audio_path TEXT NOT NULL,
                            duration_ms INTEGER NOT NULL,
                            asr_provider TEXT,
                            asr_model TEXT,
                            asr_confidence REAL,
                            asr_latency_ms INTEGER,
                            summary TEXT,
                            session_id TEXT,
                            pending_id TEXT,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL,
                            deleted_at INTEGER
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX index_diary_entry_local_date_created_at ON diary_entry_local(date, created_at)")
                    db.execSQL("CREATE INDEX index_diary_entry_local_device_id ON diary_entry_local(device_id)")
                    db.execSQL("CREATE UNIQUE INDEX index_diary_entry_local_pending_id ON diary_entry_local(pending_id)")
                    db.execSQL(
                        """
                        CREATE TABLE asr_config (
                            id INTEGER NOT NULL PRIMARY KEY,
                            api_key_enc TEXT NOT NULL,
                            updated_at INTEGER NOT NULL,
                            last_test_result TEXT,
                            minimax_api_key_enc TEXT,
                            minimax_last_test_result TEXT
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE elder_facts (
                            id TEXT NOT NULL PRIMARY KEY,
                            type TEXT NOT NULL,
                            content TEXT NOT NULL,
                            confidence TEXT NOT NULL,
                            last_used_at INTEGER NOT NULL,
                            mention_count INTEGER NOT NULL DEFAULT 0,
                            source_session_id TEXT,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("INSERT INTO asr_config (id,api_key_enc,updated_at) VALUES (1, 'old_api_key', 1)")

                    // v0.6.0 MIGRATION_3_4: elder_facts indexes
                    db.execSQL("CREATE INDEX index_elder_facts_type_last_used_at ON elder_facts(type, last_used_at)")
                    db.execSQL(
                        """
                        CREATE TABLE device_meta (
                            id INTEGER NOT NULL PRIMARY KEY,
                            device_id TEXT NOT NULL,
                            device_token TEXT,
                            font_scale TEXT NOT NULL,
                            tts_enabled INTEGER NOT NULL,
                            timezone TEXT NOT NULL,
                            app_version TEXT NOT NULL,
                            first_launch_at INTEGER NOT NULL,
                            last_active_at INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE interview_session (
                            id TEXT NOT NULL PRIMARY KEY,
                            status TEXT NOT NULL,
                            turns_json TEXT NOT NULL,
                            draft_text TEXT,
                            draft_summary TEXT,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE pending_diary (
                            id TEXT NOT NULL PRIMARY KEY,
                            date TEXT NOT NULL,
                            audio_path TEXT NOT NULL,
                            duration_ms INTEGER NOT NULL,
                            attempts INTEGER NOT NULL,
                            last_error TEXT,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX index_elder_facts_source_session_id ON elder_facts(source_session_id)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(v4Config).writableDatabase.close()

        // Migrate to v5 (current ElderDatabase)
        val db5 = Room.databaseBuilder(context, ElderDatabase::class.java, v4DbName)
            .addMigrations(ElderDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            // v0.7.0: asr_config is dropped and recreated; old data lost.
            // We only assert the new schema has the expected columns and one row can be inserted.
            val dao = db5.asrConfigDao()
            // Read returns null because the table was rebuilt empty.
            val initial = dao.get()
            assertEquals(null, initial)
            // Insert with all 16 columns succeeds.
            dao.upsert(
                AsrConfigEntity(
                    id = 1,
                    apiKeyEnc = "k",
                    asrProvider = "minimax_realtime",
                    asrEndpoint = "wss://api.minimax.cn/ws/v1/stt",
                    asrModel = "<test>",
                    ttsProvider = "minimax",
                    ttsEndpoint = "wss://api.minimax.cn/ws/v1/t2a",
                    ttsModel = "<test>",
                    ttsVoiceId = "Cantonese_KindWoman",
                    minimaxApiKeyEnc = "minimax_k",
                    ttsMinimaxApiKeyEnc = "tts_minimax_k",
                    updatedAt = 1L,
                )
            )
            val saved = dao.get()
            assertEquals("minimax_realtime", saved?.asrProvider)
            assertEquals("minimax", saved?.ttsProvider)
            assertEquals("Cantonese_KindWoman", saved?.ttsVoiceId)
        } finally {
            db5.close()
            context.deleteDatabase(v4DbName)
        }
    }

}

/** v0.8.0 Migration 5→6：asr_config ALTER TABLE 新增 7 列（§3.1.9 / §A.15.3）。 */
@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ElderDatabaseMigration5to6Test {
    private lateinit var context: Context
    private lateinit var db: ElderDatabase
    private val dbName = "migration-5-6.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `migration 5 to 6 adds llm provider columns and keeps existing config`() = runTest {
        // 起 v5 schema（与 v0.7.0 相同）
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE asr_config (
                            id INTEGER NOT NULL PRIMARY KEY,
                            api_key_enc TEXT NOT NULL,
                            asr_provider TEXT NOT NULL,
                            asr_endpoint TEXT NOT NULL,
                            asr_model TEXT NOT NULL,
                            tts_provider TEXT NOT NULL,
                            tts_endpoint TEXT NOT NULL,
                            tts_model TEXT NOT NULL,
                            tts_voice_id TEXT,
                            minimax_api_key_enc TEXT,
                            tts_minimax_api_key_enc TEXT,
                            updated_at INTEGER NOT NULL,
                            last_test_result TEXT,
                            minimax_last_test_result TEXT,
                            tts_minimax_last_test_result TEXT
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT INTO asr_config (id, api_key_enc, asr_provider, asr_endpoint, asr_model,
                            tts_provider, tts_endpoint, tts_model, minimax_api_key_enc, updated_at)
                        VALUES (1, 'api_key', 'minimax_realtime', 'https://x', 'minimax-r',
                            'minimax', 'https://y', 'minimax-t', 'minimax_api_key', 123)
                        """.trimIndent(),
                    )
                }
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        helper.close()

        // 跑 5→6 migration
        db = Room.databaseBuilder(context, ElderDatabase::class.java, dbName)
            .addMigrations(ElderDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()

        val config = db.asrConfigDao().get()
        assertTrue("Migration 5→6 应保留 v5 既有配置", config != null)
        assertEquals("api_key", config?.apiKeyEnc)
        assertEquals("minimax_api_key", config?.minimaxApiKeyEnc)
        // llm_provider 默认值
        assertEquals("minimax", config?.llmProvider)
        // 新增列应为空
        assertEquals(null, config?.qwenLlmApiKeyEnc)
        assertEquals(null, config?.deepseekLlmApiKeyEnc)
        assertEquals(null, config?.qwenLlmLastTestResult)
        assertEquals(null, config?.deepseekLlmLastTestResult)
    }
}
