// 对应 docs/v0.11.0.md §3.2：SaveExportRepository 用到的 OssSync 子集操作。
// v0.10.0 §6.5 已建 OssSyncRepository 含 syncDiary / enqueueDebounced;本接口仅
// 暴露 SaveExportRepository 调到的两个方法,便于测试桩替换(避免反射 OssSync 私有依赖)。
package com.elder.android.data.oss

interface OssSyncActions {
    /** v0.10.0 §6.5: 节流触发同步;返回 true 表示本次 enqueue 成功。 */
    fun enqueueDebounced(now: Long = System.currentTimeMillis()): Boolean

    /** v0.10.0 §6.5: 同步单条 diary。 */
    suspend fun syncDiary(diaryId: Long): OssSyncRepository.SyncOutcome
}
