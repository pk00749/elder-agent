// §6.4 MVP 客户端错误码测试
package com.elder.android.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorTest {
    @Test fun `each AppError exposes its Code enum value`() {
        val all = listOf(
            AppError.AsrAuthFailed() to AppError.Code.ASR_AUTH_FAILED,
            AppError.AsrRateLimited() to AppError.Code.ASR_RATE_LIMITED,
            AppError.AsrBadRequest() to AppError.Code.ASR_BAD_REQUEST,
            AppError.AsrUpstream() to AppError.Code.ASR_UPSTREAM,
            AppError.AsrResponseInvalid() to AppError.Code.ASR_RESPONSE_INVALID,
            AppError.AsrNotConfigured() to AppError.Code.ASR_NOT_CONFIGURED,
            AppError.AsrEmptyTranscript() to AppError.Code.ASR_EMPTY_TRANSCRIPT,
            AppError.RecordingPermissionDenied() to AppError.Code.RECORDING_PERMISSION_DENIED,
            AppError.RecordingFailed() to AppError.Code.RECORDING_FAILED,
            AppError.NetworkUnavailable() to AppError.Code.NETWORK_UNAVAILABLE,
            AppError.StorageFull() to AppError.Code.STORAGE_FULL,
            AppError.RoomCorrupted() to AppError.Code.ROOM_CORRUPTED,
            AppError.Unknown() to AppError.Code.UNKNOWN,
        )
        for ((err, expected) in all) {
            assertEquals(expected, err.code)
            assertNotNull(err.message)
            assertTrue(err.message!!.isNotBlank())
        }
    }

    @Test fun `error message matches user-facing copy from PRD 6_4`() {
        // 每条错误码的 user message 必须和 PRD §6.4 一字不差
        assertEquals("API Key 不对", AppError.Code.ASR_AUTH_FAILED.userMessage)
        assertEquals("请先在设置 → AI 语音识别 配置 API", AppError.Code.ASR_NOT_CONFIGURED.userMessage)
        assertEquals("没听清，再说一次", AppError.Code.ASR_EMPTY_TRANSCRIPT.userMessage)
        assertEquals("网络不通，请检查 Wi-Fi", AppError.Code.NETWORK_UNAVAILABLE.userMessage)
    }

    @Test fun `AppError is throwable and preserves cause`() {
        val cause = RuntimeException("network down")
        val err = AppError.AsrUpstream(cause)
        assertEquals(cause, err.cause)
    }
}
