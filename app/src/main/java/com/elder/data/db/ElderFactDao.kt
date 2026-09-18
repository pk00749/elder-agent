// 对应 PRD §3.1.4 F3 elder_facts DAO
// search_memory 用 LIKE；remember_fact 用 upsert；findSimilar 走 Kotlin 计算 overlap（SQL 不适合复杂字符串相似度）
package com.elder.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ElderFactDao {
    /** F3 search_memory：纯 LIKE 查询 content 字段；按 mentionCount DESC, lastUsedAt DESC 排序。 */
    @Query(
        """
        SELECT * FROM elder_facts
        WHERE (:typeFilter IS NULL OR type = :typeFilter)
          AND (content LIKE '%' || :query || '%')
        ORDER BY mention_count DESC, last_used_at DESC
        LIMIT :limit
        """,
    )
    suspend fun searchByContent(
        query: String,
        typeFilter: String? = null,
        limit: Int = 5,
    ): List<ElderFactEntity>

    /** F3 remember_fact / F4 prompt 注入：取全部事实（应用层再排序+截断）。 */
    @Query("SELECT * FROM elder_facts ORDER BY mention_count DESC, last_used_at DESC")
    suspend fun findAll(): List<ElderFactEntity>

    /** F3 remember_fact 去重：找 content 与新 fact 有显著重叠的旧 fact。 */
    @Query("SELECT * FROM elder_facts WHERE type = :type")
    suspend fun findByType(type: String): List<ElderFactEntity>

    @Query("SELECT COUNT(*) FROM elder_facts")
    suspend fun count(): Int

    /** F3 remember_fact 写入：idempotent（REPLACE by primary key）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fact: ElderFactEntity)

    @Query("DELETE FROM elder_facts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM elder_facts")
    suspend fun clear()
}
