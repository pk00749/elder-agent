// 对应 docs/v0.10.0.md §6.6：OssSyncWorker（CoroutineWorker + UNMETERED Constraints）。
// 触发点：
//   1. SaveAgent.saveDiary() 落库后 enqueue
//   2. ConnectivityManager.NetworkCallback 监听到 Wi-Fi 时 retryPending
//   3. App 前台 (ProcessLifecycleOwner ON_START)
// 失败：WorkManager 自带指数 backoff(1h / 8h / 24h);失败 3 次后 OssSyncRepository 把 oss_sync_status 标 failed。
package com.elder.android.data.oss

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elder.android.di.ServiceLocator

class OssSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // §6.6: 失败 3 次 → 软放弃;WorkManager backoff 仍触发但 repository 直接判 failed
        val repo = ServiceLocator.ossSyncRepo
        val synced = repo.retryPending(limit = 20)
        return Result.success(
            androidx.work.Data.Builder().putInt("synced", synced).build()
        )
    }

    companion object {
        const val WORK_NAME = "oss_sync_worker"

        /**
         * 触发一次后台同步；与 OssSyncRepository.enqueueDebounced() 配合防抖动。
         */
        fun enqueueOnce(context: Context) {
            val repo = ServiceLocator.ossSyncRepo
            if (!repo.enqueueDebounced()) return
            val request = OneTimeWorkRequestBuilder<OssSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()
                )
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    60_000L,  // 最小 1 分钟 → 指数 backoff(1h/8h/24h 等)
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
