// v3.0 MVP Room 数据库 + 0.5.0 Agent 会话与离线补做表 + v0.6.0 elder_facts 长期事实表。
// v3.0.1 §A.1.b：asr_config 砍掉 provider/endpoint/model/extra_headers_json/audio_format，
// version 1→2，MIGRATION_1_2 直接 drop 列；旧 API Key 强制清空（用户需重输百炼 Key）。
// v0.6.0 §3.1.4 F2 / F6 + AGENTS.md §A.11.3：version 3→4，新增 elder_facts 表（用 CREATE TABLE + 索引；不 drop 现有表）。
// v0.7.0 §3.1.9 / §A.14：version 4→5，asr_config 走 DROP+CREATE（16 列 schema），强制老人重输 4 份 Key。
// v0.8.0 §3.1.9 / §A.15：version 5→6，asr_config 走 ALTER TABLE 新增 7 列（llm_provider / llm_endpoint / llm_model / qwen_llm_api_key_enc / qwen_llm_last_test_result / deepseek_llm_api_key_enc / deepseek_llm_last_test_result），不 drop 既有数据。
// v0.10.0 §6：version 6→7，oss_config 表 CREATE TABLE（单行）+ diary_entry_local ALTER 增量 5 列（oss_object_key/oss_sync_status/oss_synced_at/oss_last_error/oss_attempts）。
package com.elder.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DiaryEntryEntity::class,
        AsrConfigEntity::class,
        DeviceMetaEntity::class,
        InterviewSessionEntity::class,
        PendingDiaryEntity::class,
        ElderFactEntity::class, // v0.6.0 新增：长期事实表
        OssConfigEntity::class,  // v0.10.0 §6.2 新增：OSS 配置(单行表 id=1)
    ],
    version = 7, // v0.10.0: 6→7 新增 oss_config 表 + diary_entry_local ALTER 5 列
    exportSchema = false,
)
abstract class ElderDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun asrConfigDao(): AsrConfigDao
    abstract fun deviceMetaDao(): DeviceMetaDao
    abstract fun interviewSessionDao(): InterviewSessionDao
    abstract fun pendingDiaryDao(): PendingDiaryDao
    abstract fun elderFactDao(): ElderFactDao // v0.6.0 新增
    abstract fun ossConfigDao(): OssConfigDao // v0.10.0 §6.2 新增

    companion object {
        @Volatile private var instance: ElderDatabase? = null

        /**
         * 对应 PRD §A.1.b：从 v1 的多 provider 字段砍到 v2 的 api_key 密文单字段。
         * 旧 DashScope/Whisper/Custom 配置直接丢弃（强制重输百炼 API Key）。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS asr_config")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS asr_config (
                        id INTEGER NOT NULL PRIMARY KEY,
                        api_key_enc TEXT NOT NULL,
                        updated_at INTEGER NOT NULL,
                        last_test_result TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diary_entry_local ADD COLUMN summary TEXT")
                db.execSQL("ALTER TABLE diary_entry_local ADD COLUMN session_id TEXT")
                db.execSQL("ALTER TABLE diary_entry_local ADD COLUMN pending_id TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_diary_entry_local_pending_id " +
                        "ON diary_entry_local(pending_id)"
                )
                db.execSQL("ALTER TABLE asr_config ADD COLUMN minimax_api_key_enc TEXT")
                db.execSQL("ALTER TABLE asr_config ADD COLUMN minimax_last_test_result TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS interview_session (
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
                    CREATE TABLE IF NOT EXISTS pending_diary (
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
            }
        }

        /**
         * v0.6.0 Migration 3→4：新增 elder_facts 表（§3.1.4 F2）。
         * 用 CREATE TABLE + 索引；不动现有表（AGENTS.md §A.11.3 要求不 drop）。
         * 老 data（diary_entry_local / interview_session / pending_diary / asr_config / device_meta）保留。
         */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS elder_facts (
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
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_elder_facts_type_last_used_at " +
                        "ON elder_facts(type, last_used_at)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_elder_facts_source_session_id " +
                        "ON elder_facts(source_session_id)"
                )
            }
        }

        /**
         * v0.7.0 Migration 4→5：asr_config 表 DROP+CREATE（§3.1.9 / §A.14）。
         * asr_config 不在 §18 锁定列表，允许 DROP 重建。
         * 强制老人重输 4 份 Key（api_key_enc 千问共用 / minimax_api_key_enc MiniMax LLM+ASR 共用 /
         * tts_minimax_api_key_enc MiniMax TTS 独立）。
         * 主屏需弹一次性 Toast 提示用户重新配置（由 ServiceLocator.init 检测 schema version 4→5 emit）。
         */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS asr_config")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS asr_config (
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
                    """.trimIndent()
                )
            }
        }

        /**
         * v0.8.0 Migration 5→6：asr_config 表 ALTER TABLE 新增 7 列（§3.1.9 / §A.15.3）。
         * asr_config 不在 §18 锁定列表，允许 ALTER TABLE；不 drop 既有 16 列数据。
         * 既有 4→5 DROP+CREATE 是 v0.7.0 重构已落库；本次仅追加，不影响用户已配 Key。
         */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE asr_config ADD COLUMN llm_provider TEXT NOT NULL DEFAULT 'minimax'")
                db.execSQL("ALTER TABLE asr_config ADD COLUMN llm_endpoint TEXT")
                db.execSQL("ALTER TABLE asr_config ADD COLUMN llm_model TEXT")
                db.execSQL("ALTER TABLE asr_config ADD COLUMN qwen_llm_api_key_enc TEXT")
                db.execSQL("ALTER TABLE asr_config ADD COLUMN qwen_llm_last_test_result TEXT")
                db.execSQL("ALTER TABLE asr_config ADD COLUMN deepseek_llm_api_key_enc TEXT")
                db.execSQL("ALTER TABLE asr_config ADD COLUMN deepseek_llm_last_test_result TEXT")
            }
        }

        /**
         * v0.10.0 Migration 6→7：oss_config 表 CREATE TABLE + diary_entry_local ALTER 5 列（§6.2 / §6.4）。
         * oss_config 不在 §18 锁定列表,允许 CREATE TABLE。
         * diary_entry_local §5.10 在锁定列表,仅 ALTER 追加;既有 17 列字段不动。
         * oss_sync_status NOT NULL DEFAULT 'pending' 让既有行满足 NOT NULL 约束。
         */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diary_entry_local ADD COLUMN oss_object_key TEXT")
                db.execSQL("ALTER TABLE diary_entry_local ADD COLUMN oss_sync_status TEXT NOT NULL DEFAULT 'pending'")
                db.execSQL("ALTER TABLE diary_entry_local ADD COLUMN oss_synced_at INTEGER")
                db.execSQL("ALTER TABLE diary_entry_local ADD COLUMN oss_last_error TEXT")
                db.execSQL("ALTER TABLE diary_entry_local ADD COLUMN oss_attempts INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS oss_config (
                        id INTEGER NOT NULL PRIMARY KEY,
                        endpoint TEXT NOT NULL,
                        bucket TEXT NOT NULL,
                        region TEXT NOT NULL,
                        prefix TEXT NOT NULL,
                        sync_on_wifi_only INTEGER NOT NULL DEFAULT 1,
                        access_key_id_enc TEXT NOT NULL,
                        access_key_secret_enc TEXT NOT NULL,
                        sts_token_enc TEXT,
                        last_sync_at INTEGER,
                        last_sync_result TEXT,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_oss_config_updated_at ON oss_config(updated_at)"
                )
            }
        }

        fun get(context: Context): ElderDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ElderDatabase::class.java,
                "elder_v3.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                .also { instance = it }
        }
    }
}
