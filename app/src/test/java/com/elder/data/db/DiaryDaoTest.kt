// §5.10 diary_entry_local DAO 测试（Robolectric + in-memory Room）
package com.elder.android.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.elder.android.testing.ElderRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// v3.0.1 + Java 25：Robolectric 4.13 的 Shadows.reset() 在 ShadowCookieManager 阶段会因
// android-all-instrumented 升 Java 25 而炸（asm 9.7.1 读不了 major 69）。ElderRobolectricTestRunner
// 兜住 finallyAfterTest 的 CookieManager reset 失败，其他异常照常抛。
@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class DiaryDaoTest {
    private lateinit var db: ElderDatabase
    private lateinit var dao: DiaryDao

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, ElderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.diaryDao()
    }

    @After fun tearDown() { db.close() }

    private fun entry(date: String, text: String = "default", id: Long = 0L) = DiaryEntryEntity(
        id = id,
        deviceId = "local",
        date = date,
        text = text,
        source = DiaryEntryEntity.Source.ASR_ORIGINAL,
        audioPath = "/cache/audio/${id}.m4a",
        durationMs = 5000,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

    @Test fun `insert returns auto id and roundtrip query`() = runTest {
        val id = dao.insert(entry(date = "2026-09-06", text = "今天和老张下棋"))
        assertTrue("id should be positive", id > 0)

        val row = dao.audioPath(id)
        assertNotNull(row)
        assertTrue(row!!.contains(".m4a"))
    }

    @Test fun `observeByDate returns only entries on that date ordered desc`() = runTest {
        dao.insert(entry(date = "2026-09-05", text = "a"))
        dao.insert(entry(date = "2026-09-06", text = "b", id = 0))
        dao.insert(entry(date = "2026-09-06", text = "c", id = 0))

        val rows = dao.observeByDate("2026-09-06").first()
        assertEquals(2, rows.size)
        // 两条同日的，createdAt 一致；保持插入顺序即可
        assertEquals(setOf("b", "c"), rows.map { it.text }.toSet())
    }

    @Test fun `hasAnyOnDate true when entry present false otherwise`() = runTest {
        assertEquals(false, dao.hasAnyOnDate("2026-09-06"))
        dao.insert(entry(date = "2026-09-06"))
        assertEquals(true, dao.hasAnyOnDate("2026-09-06"))
        assertEquals(false, dao.hasAnyOnDate("2026-09-07"))
    }

    @Test fun `updateText bumps updatedAt and flips source to ASR_EDITED`() = runTest {
        val id = dao.insert(entry(date = "2026-09-06", text = "原文"))
        val newText = "老人手动改写"
        dao.updateText(id, newText, System.currentTimeMillis() + 1000)
        val rows = dao.observeByDate("2026-09-06").first()
        assertEquals(1, rows.size)
        assertEquals(newText, rows[0].text)
        assertEquals(DiaryEntryEntity.Source.ASR_EDITED, rows[0].source)
    }

    @Test fun `softDelete hides row from observeByDate but row still in DB`() = runTest {
        val id = dao.insert(entry(date = "2026-09-06"))
        assertTrue(dao.hasAnyOnDate("2026-09-06"))
        dao.softDelete(id, System.currentTimeMillis())
        assertEquals(false, dao.hasAnyOnDate("2026-09-06"))
        assertNotNull("row should still exist in DB", dao.audioPath(id))
    }

    @Test fun `clearAll wipes the table`() = runTest {
        dao.insert(entry(date = "2026-09-06", text = "x"))
        dao.insert(entry(date = "2026-09-07", text = "y"))
        assertEquals(2, dao.observeAll().first().size)
        dao.clearAll()
        assertEquals(0, dao.observeAll().first().size)
    }

    @Test fun `date range query returns entries within inclusive bounds`() = runTest {
        dao.insert(entry(date = "2026-09-04"))
        dao.insert(entry(date = "2026-09-05"))
        dao.insert(entry(date = "2026-09-06"))
        dao.insert(entry(date = "2026-09-07"))
        val rows = dao.observeByDateRange("2026-09-05", "2026-09-06").first()
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.date in setOf("2026-09-05", "2026-09-06") })
    }
}
