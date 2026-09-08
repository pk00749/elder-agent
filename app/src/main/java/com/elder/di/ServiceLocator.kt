// 手动 DI（避免 Hilt 复杂度；ServiceLocator 暴露 MVP 全部依赖）
// v3.0 MVP：老人端独立运行，无服务端；只持有 Room DB + Audio + ASR client
package com.elder.android.di

import android.content.Context
import com.elder.android.audio.AudioRecorder
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.DeviceMetaRepository
import com.elder.android.data.DiaryRepository
import com.elder.android.data.asr.AsrApiClient

object ServiceLocator {
    @Volatile private var inited = false

    lateinit var diaryRepo: DiaryRepository
        private set
    lateinit var asrConfigRepo: AsrConfigRepository
        private set
    lateinit var deviceMetaRepo: DeviceMetaRepository
        private set
    lateinit var audioRecorder: AudioRecorder
        private set
    lateinit var asrApi: AsrApiClient
        private set

    fun init(context: Context) {
        if (inited) return
        synchronized(this) {
            if (inited) return
            val app = context.applicationContext
            diaryRepo = DiaryRepository.get(app)
            asrConfigRepo = AsrConfigRepository.get(app)
            deviceMetaRepo = DeviceMetaRepository.get(app)
            audioRecorder = AudioRecorder(app)
            asrApi = AsrApiClient()
            inited = true
        }
    }
}
