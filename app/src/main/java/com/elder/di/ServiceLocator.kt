// 手动 DI（避免 Hilt 复杂度；ServiceLocator 暴露 MVP 全部依赖）
package com.elder.android.di

import android.content.Context
import com.elder.android.BuildConfig
import com.elder.android.audio.AudioRecorder
import com.elder.android.audio.TtsPlayer
import com.elder.android.data.TokenStore
import com.elder.android.network.ApiClient

object ServiceLocator {
    @Volatile private var inited = false
    lateinit var tokenStore: TokenStore
        private set
    lateinit var apiClient: ApiClient
        private set
    lateinit var audioRecorder: AudioRecorder
        private set
    lateinit var ttsPlayer: TtsPlayer
        private set

    fun init(context: Context) {
        if (inited) return
        synchronized(this) {
            if (inited) return
            val app = context.applicationContext
            tokenStore = TokenStore(app)
            apiClient = ApiClient(
                tokenStore = tokenStore,
                accountBaseUrl = BuildConfig.ACCOUNT_BASE_URL,
                reminderBaseUrl = BuildConfig.REMINDER_BASE_URL,
                agentBaseUrl = BuildConfig.AGENT_BASE_URL,
                cosBaseUrl = BuildConfig.ACCOUNT_BASE_URL,
            )
            audioRecorder = AudioRecorder(app)
            ttsPlayer = TtsPlayer()
            inited = true
        }
    }
}
