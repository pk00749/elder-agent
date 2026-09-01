// 网络 DTO（与 prd.md §6.1 / §6.3 字段对齐）
package com.elder.android.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class SmsCodeRequest(val phone: String)

@JsonClass(generateAdapter = false)
data class LoginRequest(val phone: String, val code: String)

@JsonClass(generateAdapter = false)
data class LoginResponse(
    val token: String,
    @Json(name = "user_id") val userId: String,
    val role: String,
)

@JsonClass(generateAdapter = false)
data class BindCodeRequest(
    @Json(name = "elder_device_token") val elderDeviceToken: String,
)

@JsonClass(generateAdapter = false)
data class BindCodeResponse(
    @Json(name = "bind_code") val bindCode: String,
    @Json(name = "expires_in") val expiresIn: Int,
)

@JsonClass(generateAdapter = false)
data class PendingItem(
    @Json(name = "bind_attempt_id") val bindAttemptId: String,
    @Json(name = "family_user_id") val familyUserId: String,
    @Json(name = "family_user_name") val familyUserName: String,
    @Json(name = "family_user_phone_tail") val familyUserPhoneTail: String,
    @Json(name = "bind_code") val bindCode: String,
    @Json(name = "created_at") val createdAt: String,
)

@JsonClass(generateAdapter = false)
data class PendingResponse(val pending: List<PendingItem>)

@JsonClass(generateAdapter = false)
data class ConfirmRequest(
    @Json(name = "bind_code") val bindCode: String,
    @Json(name = "elder_name") val elderName: String,
    val decision: String, // "accept" | "reject"
)

@JsonClass(generateAdapter = false)
data class ConfirmAcceptResponse(
    @Json(name = "elder_id") val elderId: String,
    @Json(name = "binding_id") val bindingId: String,
    val role: String,
    val token: String,
)

@JsonClass(generateAdapter = false)
data class ReminderCreateRequest(
    @Json(name = "elder_id") val elderId: String,
    val type: String,
    val payload: Map<String, Any?>,
    val status: String? = "active",
)

@JsonClass(generateAdapter = false)
data class Reminder(
    val id: String,
    @Json(name = "elder_id") val elderId: String,
    val type: String,
    val payload: Map<String, Any?>,
    val status: String,
    @Json(name = "channel_priority") val channelPriority: String,
    @Json(name = "created_at") val createdAt: String,
)

@JsonClass(generateAdapter = false)
data class ReminderListResponse(val reminders: List<Reminder>)

@JsonClass(generateAdapter = false)
data class AckRequest(
    val action: String, // "taken" | "ack" | "missed"
    val at: String? = null,
    @Json(name = "offline_buffered") val offlineBuffered: Boolean = false,
)

@JsonClass(generateAdapter = false)
data class Diary(
    val id: String,
    @Json(name = "elder_id") val elderId: String,
    val date: String,
    val text: String,
    val summary: String,
    @Json(name = "audio_segments") val audioSegments: List<Map<String, Any?>>,
    @Json(name = "created_at") val createdAt: String,
)

@JsonClass(generateAdapter = false)
data class DiaryListResponse(val diaries: List<Diary>)

@JsonClass(generateAdapter = false)
data class DiaryDetailResponse(
    val id: String,
    @Json(name = "elder_id") val elderId: String,
    val date: String,
    val text: String,
    val summary: String,
    @Json(name = "audio_segments") val audioSegments: List<Map<String, Any?>>,
    @Json(name = "created_at") val createdAt: String,
)

@JsonClass(generateAdapter = false)
data class PendingDiary(
    @Json(name = "pending_id") val pendingId: String,
    @Json(name = "audio_cos_key") val audioCosKey: String,
    val turns: List<Map<String, Any?>>,
    val text: String,
    val summary: String,
)

@JsonClass(generateAdapter = false)
data class FlushPendingRequest(@Json(name = "pending_diaries") val pendingDiaries: List<PendingDiary>)

@JsonClass(generateAdapter = false)
data class FlushPendingResponse(
    val synced: List<Map<String, String>>,
    val dropped: List<Map<String, String>>,
)

@JsonClass(generateAdapter = false)
data class AgentDiaryStartRequest(@Json(name = "elder_id") val elderId: String)

@JsonClass(generateAdapter = false)
data class AgentDiaryStartResponse(
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "max_turns") val maxTurns: Int,
    @Json(name = "greeting_text") val greetingText: String,
    @Json(name = "greeting_audio_url") val greetingAudioUrl: String,
    @Json(name = "greeting_audio_expires_in") val greetingAudioExpiresIn: Int,
)

@JsonClass(generateAdapter = false)
data class AgentTurnRequest(
    @Json(name = "turn_no") val turnNo: Int,
    @Json(name = "elder_text") val elderText: String,
    @Json(name = "elder_audio_cos_key") val elderAudioCosKey: String,
)

@JsonClass(generateAdapter = false)
data class AgentTurnResponse(
    @Json(name = "turn_no") val turnNo: Int,
    @Json(name = "assistant_text") val assistantText: String,
    @Json(name = "assistant_audio_url") val assistantAudioUrl: String,
    @Json(name = "assistant_audio_expires_in") val assistantAudioExpiresIn: Int,
    @Json(name = "should_finalize") val shouldFinalize: Boolean,
    @Json(name = "turns_left") val turnsLeft: Int,
)

@JsonClass(generateAdapter = false)
data class AgentFinalizeRequest(
    @Json(name = "turn_no") val turnNo: Int,
    @Json(name = "elder_text") val elderText: String,
    @Json(name = "elder_audio_cos_key") val elderAudioCosKey: String,
)

@JsonClass(generateAdapter = false)
data class AgentFinalizeResponse(
    @Json(name = "diary_id") val diaryId: String,
    val text: String,
    val summary: String,
)

@JsonClass(generateAdapter = false)
data class AsrRequest(
    @Json(name = "audio_url") val audioUrl: String,
    val format: String = "m4a",
)

@JsonClass(generateAdapter = false)
data class AsrResponse(val text: String, val confidence: Float)

@JsonClass(generateAdapter = false)
data class TtsRequest(val text: String)

@JsonClass(generateAdapter = false)
data class TtsResponse(
    @Json(name = "audio_url") val audioUrl: String,
    @Json(name = "expires_in") val expiresIn: Int,
)

@JsonClass(generateAdapter = false)
data class ApiError(
    val code: String,
    val message: String,
    val field: String? = null,
    @Json(name = "retry_after_seconds") val retryAfterSeconds: Int? = null,
)
