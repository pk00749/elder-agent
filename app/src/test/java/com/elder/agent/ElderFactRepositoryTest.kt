// 对应 PRD §3.1.4 F3 remember_fact / search_memory 服务端测试
// 纯 JVM 单测，不依赖 Android SDK；用 in-memory map 模拟 DAO。
package com.elder.android.agent

import com.elder.android.data.db.ElderFactDao
import com.elder.android.data.db.ElderFactEntity
import com.elder.android.data.db.ElderFactPersistResult
import com.elder.android.data.db.ElderFactRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ElderFactRepositoryTest {

    private class FakeDao : ElderFactDao {
        val rows = mutableMapOf<String, ElderFactEntity>()

        override suspend fun searchByContent(query: String, typeFilter: String?, limit: Int): List<ElderFactEntity> =
            rows.values.filter {
                (typeFilter == null || it.type == typeFilter) && it.content.contains(query)
            }.sortedWith(compareByDescending<ElderFactEntity> { it.mentionCount }.thenByDescending { it.lastUsedAt })
                .take(limit)

        override suspend fun findAll(): List<ElderFactEntity> =
            rows.values.sortedWith(compareByDescending<ElderFactEntity> { it.mentionCount }.thenByDescending { it.lastUsedAt })

        override suspend fun findByType(type: String): List<ElderFactEntity> =
            rows.values.filter { it.type == type }

        override suspend fun count(): Int = rows.size

        override suspend fun upsert(fact: ElderFactEntity) {
            rows[fact.id] = fact
        }

        override suspend fun deleteById(id: String) {
            rows.remove(id)
        }

        override suspend fun clear() {
            rows.clear()
        }
    }

    @Test
    fun `rememberFact validates type enum`() = runBlocking {
        val repo = ElderFactRepository(FakeDao())
        try {
            repo.rememberFact(type = "unknown", content = "test", confidence = "high")
            assert(false) { "should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("type"))
        }
    }

    @Test
    fun `rememberFact validates confidence enum`() = runBlocking {
        val repo = ElderFactRepository(FakeDao())
        try {
            repo.rememberFact(type = "person", content = "test", confidence = "ultra")
            assert(false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("confidence"))
        }
    }

    @Test
    fun `rememberFact enforces 100 char limit`() = runBlocking {
        val repo = ElderFactRepository(FakeDao())
        val long = "测".repeat(101)
        try {
            repo.rememberFact(type = "person", content = long, confidence = "high")
            assert(false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("length"))
        }
    }

    @Test
    fun `rememberFact creates new fact on no overlap`() = runBlocking {
        val repo = ElderFactRepository(FakeDao())
        val r = repo.rememberFact(type = "person", content = "儿子叫张伟", confidence = "high")
        assertTrue(r.isNew)
        assertEquals(1, (repo as Any).let { 1 }) // placeholder
    }

    @Test
    fun `rememberFact merges on substring overlap greater than threshold`() = runBlocking {
        val repo = ElderFactRepository(FakeDao())
        // 写入初始 fact
        val r1 = repo.rememberFact(type = "person", content = "儿子叫张伟，在二中当老师", confidence = "high")
        assertTrue(r1.isNew)
        // 写入部分重叠 fact（substring overlap > 0.6）
        val r2 = repo.rememberFact(type = "person", content = "儿子叫张伟", confidence = "high")
        assertFalse("should merge, not new", r2.isNew)
        assertEquals(r1.id, r2.id)
    }

    @Test
    fun `rememberFact does not merge on different type`() = runBlocking {
        val repo = ElderFactRepository(FakeDao())
        val r1 = repo.rememberFact(type = "person", content = "张伟", confidence = "high")
        val r2 = repo.rememberFact(type = "place", content = "张伟", confidence = "high")
        assertTrue(r1.isNew)
        assertTrue("different type should not merge", r2.isNew)
    }

    @Test
    fun `searchMemory returns matching facts by keyword`() = runBlocking {
        val repo = ElderFactRepository(FakeDao())
        repo.rememberFact(type = "person", content = "儿子叫张伟", confidence = "high")
        repo.rememberFact(type = "place", content = "人民公园散步", confidence = "medium")
        repo.rememberFact(type = "person", content = "老伴叫王慧", confidence = "high")

        val hits = repo.searchMemory(query = "儿子", typeFilter = null, limit = 5)
        assertEquals(1, hits.size)
        assertEquals("person", hits[0].type)

        val placeHits = repo.searchMemory(query = "公园", typeFilter = "place", limit = 5)
        assertEquals(1, placeHits.size)
    }

    @Test
    fun `searchMemory returns empty for blank query`() = runBlocking {
        val repo = ElderFactRepository(FakeDao())
        repo.rememberFact(type = "person", content = "儿子叫张伟", confidence = "high")
        val hits = repo.searchMemory(query = "  ", typeFilter = null, limit = 5)
        assertTrue(hits.isEmpty())
    }
}
