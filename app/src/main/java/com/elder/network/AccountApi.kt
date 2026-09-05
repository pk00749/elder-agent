// account-service API（§6.1 / §3.2 / §3.2.9 / §3.2.5 / §3.2.6）
package com.elder.android.network

import com.elder.android.network.dto.*
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AccountApi {
    @POST("v1/auth/anonymous-device")
    suspend fun initDevice(@Body body: InitDeviceRequest): InitDeviceResponse

    @POST("v1/bind/code")
    suspend fun createBindCode(@Body body: BindCodeRequest): BindCodeResponse

    @POST("v1/bind/elder")
    suspend fun familyBindAttempt(@Body body: FamilyBindAttemptRequest): FamilyBindAttemptResponse

    @POST("v1/bind/confirm")
    suspend fun elderConfirmBind(@Body body: ConfirmRequest): ConfirmAcceptResponse

    @GET("v1/bind/pending")
    suspend fun pendingBinds(@Header("X-Elder-Device-Token") deviceToken: String): PendingResponse

    @DELETE("v1/bind/elder/{elder_id}")
    suspend fun unbind(@Path("elder_id") elderId: String): Response<Unit>

    @GET("v1/me")
    suspend fun me(): MeResponse

    @GET("v1/diary")
    suspend fun listDiaries(
        @Query("elder_id") elderId: String,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
    ): DiaryListResponse

    @GET("v1/diary/{id}")
    suspend fun getDiary(@Path("id") id: String): DiaryDetailResponse

    @POST("v1/diary/flush-pending")
    suspend fun flushPending(@Body body: FlushPendingRequest): FlushPendingResponse
}

interface ReminderApi {
    @GET("v1/reminders")
    suspend fun listReminders(): ReminderListResponse

    @POST("v1/reminders")
    suspend fun createReminder(@Body body: ReminderCreateRequest): Reminder

    @POST("v1/reminders/{id}/ack")
    suspend fun ackReminder(@Path("id") id: String, @Body body: AckRequest)
}

interface AgentApi {
    @POST("v1/agent/diary-session")
    suspend fun startSession(@Body body: AgentDiaryStartRequest): AgentDiaryStartResponse

    @POST("v1/agent/diary-session/{id}/turn")
    suspend fun turn(@Path("id") id: String, @Body body: AgentTurnRequest): AgentTurnResponse

    @POST("v1/agent/diary-session/{id}/finalize")
    suspend fun finalize(@Path("id") id: String, @Body body: AgentFinalizeRequest): AgentFinalizeResponse

    @POST("v1/agent/asr")
    suspend fun asr(@Body body: AsrRequest): AsrResponse

    @POST("v1/agent/tts")
    suspend fun tts(@Body body: TtsRequest): TtsResponse
}

interface CosApi {
    @POST("v1/cos/presign-put")
    suspend fun presignPut(@Body body: CosPresignRequest): CosPresignResponse

    @GET("v1/cos/presign-get")
    suspend fun presignGet(@Query("key") key: String): CosPresignResponse
}

@JsonClass(generateAdapter = false)
data class CosPresignRequest(val key: String, val bucket: String, val ttl: Int = 86400)

@JsonClass(generateAdapter = false)
data class CosPresignResponse(
    val url: String,
    val key: String,
    @Json(name = "expires_in") val expiresIn: Int,
)

@JsonClass(generateAdapter = false)
data class MeResponse(
    @Json(name = "user_id") val userId: String,
    val role: String,
    @Json(name = "elder_id") val elderId: String? = null,
)

@JsonClass(generateAdapter = false)
data class FamilyBindAttemptRequest(
    @Json(name = "bind_code") val bindCode: String,
)

@JsonClass(generateAdapter = false)
data class FamilyBindAttemptResponse(
    @Json(name = "bind_attempt_id") val bindAttemptId: String,
    @Json(name = "bind_code") val bindCode: String,
    @Json(name = "elder_device_token") val elderDeviceToken: String,
    @Json(name = "created_at") val createdAt: String,
)
