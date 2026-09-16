// v3.0 MVP Room 数据库 + 0.5.0 Agent 会话与离线补做表。
// v3.0.1 §A.1.b：asr_config 砍掉 provider/endpoint/model/extra_headers_json/audio_format，
// version 1→2，MIGRATION_1_2 直接 drop 列；旧 API Key 强制清空（用户需重输百炼 Key）
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
    ],
    version = 3,
    exportSchema = false,
)
abstract class ElderDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun asrConfigDao(): AsrConfigDao
    abstract fun deviceMetaDao(): DeviceMetaDao
    abstract fun interviewSessionDao(): InterviewSessionDao
    abstract fun pendingDiaryDao(): PendingDiaryDao

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

        fun get(context: Context): ElderDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ElderDatabase::class.java,
                "elder_v3.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
