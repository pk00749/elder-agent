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
            .addMigrations(ElderDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        val diary = db.diaryDao().observeAll().first()
        assertEquals(1, diary.size)
        assertEquals("旧日记", diary[0].text)

        val config = db.asrConfigDao().get()
        assertEquals("api_key", config?.apiKeyEnc)
        assertTrue(config?.minimaxApiKeyEnc == null)

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
}
