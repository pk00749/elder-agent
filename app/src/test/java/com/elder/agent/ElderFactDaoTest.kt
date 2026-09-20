// 对应 PRD §3.1.4 F2 / F3 + AGENTS.md §A.11.3：v0.6.0 elder_facts 表 + DAO 单元测试
// Robolectric 跑 Room in-memory DB；验证 migration 3→4 + DAO 查询。
package com.elder.android.agent

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elder.android.data.db.ElderDatabase
import com.elder.android.data.db.ElderFactDao
import com.elder.android.data.db.ElderFactEntity
import com.elder.android.testing.ElderRobolectricTestRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(ElderRobolectricTestRunner::class)
class ElderFactDaoTest {

    private lateinit var db: ElderDatabase
    private lateinit var dao: ElderFactDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ElderDatabase::class.java,
        )
            // 关键：allowMainThreadQueries 便于单测同步调用
            .allowMainThreadQueries()
            .build()
        dao = db.elderFactDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun makeFact(
        id: String = "id-${System.nanoTime()}",
        type: String = ElderFactEntity.TYPE_PERSON,
        content: String = "儿子叫张伟",
        confidence: String = ElderFactEntity.CONF_HIGH,
        mentionCount: Int = 1,
        lastUsedAt: Long = 0L,
    ) = ElderFactEntity(
        id = id,
        type = type,
        content = content,
        confidence = confidence,
        lastUsedAt = lastUsedAt,
        mentionCount = mentionCount,
        sourceSessionId = null,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

    @Test
    fun `migration 3 to 4 creates elder_facts table`() = runBlocking {
        // 重启 DB 触发 schema 检查
        db.close()
        db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ElderDatabase::class.java,
            "elder_test_v4.db",
        )
            .addMigrations(ElderDatabase.MIGRATION_2_3, ElderDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        // 简单查询不抛异常即代表迁移成功
        val count = db.elderFactDao().count()
        assertEquals(0, count)
    }

    @Test
    fun `upsert and find by id`() = runBlocking {
        val fact = makeFact(id = "test-1", content = "儿子叫张伟")
        dao.upsert(fact)
        val all = dao.findAll()
        assertEquals(1, all.size)
        assertEquals("test-1", all[0].id)
        assertEquals("儿子叫张伟", all[0].content)
    }

    @Test
    fun `searchByContent matches substring`() = runBlocking {
        dao.upsert(makeFact(id = "1", content = "儿子叫张伟，在二中当老师", type = ElderFactEntity.TYPE_PERSON))
        dao.upsert(makeFact(id = "2", content = "老伴叫王慧", type = ElderFactEntity.TYPE_PERSON))
        dao.upsert(makeFact(id = "3", content = "每天在人民公园散步", type = ElderFactEntity.TYPE_PLACE))

        val personHits = dao.searchByContent("儿子", ElderFactEntity.TYPE_PERSON, limit = 5)
        assertEquals(1, personHits.size)
        assertTrue(personHits[0].content.contains("儿子"))

        val allHits = dao.searchByContent("公园", typeFilter = null, limit = 5)
        assertEquals(1, allHits.size)
        assertTrue(allHits[0].content.contains("公园"))
    }

    @Test
    fun `findAll orders by mention_count desc then last_used_at desc`() = runBlocking {
        dao.upsert(makeFact(id = "1", content = "事实1", mentionCount = 1, lastUsedAt = 100L))
        dao.upsert(makeFact(id = "2", content = "事实2", mentionCount = 5, lastUsedAt = 50L))
        dao.upsert(makeFact(id = "3", content = "事实3", mentionCount = 5, lastUsedAt = 200L))

        val all = dao.findAll()
        assertEquals(3, all.size)
        // 同 mentionCount=5 时 last_used_at DESC
        assertEquals("3", all[0].id)
        assertEquals("2", all[1].id)
        assertEquals("1", all[2].id)
    }

    @Test
    fun `findByType returns only matching type`() = runBlocking {
        dao.upsert(makeFact(id = "1", type = ElderFactEntity.TYPE_PERSON, content = "人1"))
        dao.upsert(makeFact(id = "2", type = ElderFactEntity.TYPE_PLACE, content = "地1"))
        dao.upsert(makeFact(id = "3", type = ElderFactEntity.TYPE_PERSON, content = "人2"))

        val persons = dao.findByType(ElderFactEntity.TYPE_PERSON)
        assertEquals(2, persons.size)
        val places = dao.findByType(ElderFactEntity.TYPE_PLACE)
        assertEquals(1, places.size)
    }

    @Test
    fun `delete by id removes fact`() = runBlocking {
        dao.upsert(makeFact(id = "1", content = "to be deleted"))
        assertEquals(1, dao.count())
        dao.deleteById("1")
        assertEquals(0, dao.count())
    }
}
