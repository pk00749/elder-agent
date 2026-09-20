// 对应 PRD §3.1.4 F2 elder_facts（v0.6.0 新增；纯 Room 表）
// 长期结构化事实：人物 / 地点 / 事件 / 偏好 / 健康
package com.elder.android.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "elder_facts",
    indices = [
        // F4 注入时按 mentionCount DESC, lastUsedAt DESC 排序
        Index(value = ["type", "last_used_at"]),
        // F3 search_memory 走 LIKE；content 默认索引足够（小表 ≤50 行）
        Index(value = ["source_session_id"]),
    ],
)
data class ElderFactEntity(
    @PrimaryKey val id: String,                       // UUID
    @ColumnInfo(name = "type") val type: String,                       // person|place|event|preference|health
    @ColumnInfo(name = "content") val content: String,                  // ≤100 汉字
    @ColumnInfo(name = "confidence") val confidence: String,            // high|medium|low
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
    @ColumnInfo(name = "mention_count") val mentionCount: Int = 0,
    @ColumnInfo(name = "source_session_id") val sourceSessionId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    companion object {
        // F2 type 枚举
        const val TYPE_PERSON = "person"
        const val TYPE_PLACE = "place"
        const val TYPE_EVENT = "event"
        const val TYPE_PREFERENCE = "preference"
        const val TYPE_HEALTH = "health"
        val VALID_TYPES = setOf(TYPE_PERSON, TYPE_PLACE, TYPE_EVENT, TYPE_PREFERENCE, TYPE_HEALTH)

        // F2 confidence 枚举
        const val CONF_HIGH = "high"
        const val CONF_MEDIUM = "medium"
        const val CONF_LOW = "low"
        val VALID_CONFIDENCES = setOf(CONF_HIGH, CONF_MEDIUM, CONF_LOW)

        // F2 content 长度限制（与 diary_entry.text 同限）
        const val MAX_CONTENT_CHARS = 100
    }
}
