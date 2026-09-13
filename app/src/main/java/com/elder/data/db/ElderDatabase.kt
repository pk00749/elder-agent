// v3.0 MVP Room 数据库：3 张表（§5.10 / §5.11 / §5.12）
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
    ],
    version = 2,
    exportSchema = false,
)
abstract class ElderDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun asrConfigDao(): AsrConfigDao
    abstract fun deviceMetaDao(): DeviceMetaDao

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

        fun get(context: Context): ElderDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ElderDatabase::class.java,
                "elder_v3.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
