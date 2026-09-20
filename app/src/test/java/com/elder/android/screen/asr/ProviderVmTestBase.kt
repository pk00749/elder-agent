// v0.8.1 测试基础设施：把 Main dispatcher 切到 Unconfined + 给 viewModelScope.launch
// 时间跑完（Room/SQLite IO 会切到 Room 自有 executor，需要等）。
package com.elder.android.screen.asr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.elder.android.data.AsrConfigRepository
import com.elder.android.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
abstract class ProviderVmTestBase {

    protected val repo: AsrConfigRepository
        get() = ServiceLocator.asrConfigRepo

    init {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ServiceLocator.init(ctx)
        runBlocking { ServiceLocator.asrConfigRepo.clear() }
    }

    /** 给 viewModelScope.launch 时间跑完（Unconfined + Room IO）。 */
    protected fun waitForVm() = runBlocking { delay(50) }

    /** 测试结束清理 DB + 恢复 Main dispatcher。 */
    protected fun cleanupVm() {
        runBlocking { ServiceLocator.asrConfigRepo.clear() }
        Dispatchers.resetMain()
    }
}
