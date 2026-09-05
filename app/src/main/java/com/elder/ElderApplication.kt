// PR 4 入口：手动 DI 注入点（避免 Hilt 复杂度）
package com.elder.android

import android.app.Application
import com.elder.android.di.ServiceLocator

class ElderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
