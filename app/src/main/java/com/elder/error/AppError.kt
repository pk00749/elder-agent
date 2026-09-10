// 对应 PRD §6.4 MVP 客户端错误码（v3.0 新增）
package com.elder.android.error

/**
 * MVP 客户端错误统一在 Kotlin 端用 [AppError] 承载，UI 层根据 [code] 定位提示文案。
 * 不走 §6.2 服务端错误码格式（无 HTTP status 概念）。
 */
sealed class AppError(
    val code: Code,
    override val message: String,
    override val cause: Throwable? = null,
) : Throwable(message, cause) {

    enum class Code(val userMessage: String) {
        ASR_AUTH_FAILED("API Key 不对"),
        ASR_RATE_LIMITED("太快了，等等再试"),
        ASR_BAD_REQUEST("配置有误，去设置检查"),
        ASR_UPSTREAM("对方服务器没响应"),
        ASR_RESPONSE_INVALID("对方返回看不懂"),
        ASR_NOT_CONFIGURED("请先在设置 → AI 语音识别 配置 API"),
        ASR_EMPTY_TRANSCRIPT("没听清，再说一次"),
        RECORDING_PERMISSION_DENIED("需要麦克风权限才能写日志"),
        RECORDING_FAILED("录音没成功，再试一次"),
        NETWORK_UNAVAILABLE("网络不通，请检查 Wi-Fi"),
        STORAGE_FULL("手机存储满了"),
        ROOM_CORRUPTED("本地数据损坏"),
        UNKNOWN("出了点小问题，再试一次"),
    }

    class AsrAuthFailed(cause: Throwable? = null) : AppError(Code.ASR_AUTH_FAILED, Code.ASR_AUTH_FAILED.userMessage, cause)
    class AsrRateLimited(cause: Throwable? = null) : AppError(Code.ASR_RATE_LIMITED, Code.ASR_RATE_LIMITED.userMessage, cause)
    class AsrBadRequest(cause: Throwable? = null) : AppError(Code.ASR_BAD_REQUEST, Code.ASR_BAD_REQUEST.userMessage, cause)
    class AsrUpstream(cause: Throwable? = null) : AppError(Code.ASR_UPSTREAM, Code.ASR_UPSTREAM.userMessage, cause)
    class AsrResponseInvalid(cause: Throwable? = null) : AppError(Code.ASR_RESPONSE_INVALID, Code.ASR_RESPONSE_INVALID.userMessage, cause)
    class AsrNotConfigured : AppError(Code.ASR_NOT_CONFIGURED, Code.ASR_NOT_CONFIGURED.userMessage)
    class AsrEmptyTranscript : AppError(Code.ASR_EMPTY_TRANSCRIPT, Code.ASR_EMPTY_TRANSCRIPT.userMessage)
    class RecordingPermissionDenied : AppError(Code.RECORDING_PERMISSION_DENIED, Code.RECORDING_PERMISSION_DENIED.userMessage)
    class RecordingFailed(cause: Throwable? = null) : AppError(Code.RECORDING_FAILED, Code.RECORDING_FAILED.userMessage, cause)
    class NetworkUnavailable : AppError(Code.NETWORK_UNAVAILABLE, Code.NETWORK_UNAVAILABLE.userMessage)
    class StorageFull : AppError(Code.STORAGE_FULL, Code.STORAGE_FULL.userMessage)
    class RoomCorrupted : AppError(Code.ROOM_CORRUPTED, Code.ROOM_CORRUPTED.userMessage)
    class Unknown(cause: Throwable? = null) : AppError(Code.UNKNOWN, Code.UNKNOWN.userMessage, cause)
}
