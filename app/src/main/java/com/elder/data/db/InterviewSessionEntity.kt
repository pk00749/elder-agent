// 对应 PRD 0.5.0：本地 Agent 多轮访谈会话。
package com.elder.android.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "interview_session")
data class InterviewSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "turns_json") val turnsJson: String,
    @ColumnInfo(name = "draft_text") val draftText: String? = null,
    @ColumnInfo(name = "draft_summary") val draftSummary: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
