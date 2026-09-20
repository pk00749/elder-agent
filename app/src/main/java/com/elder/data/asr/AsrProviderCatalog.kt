// §3.1.9 / §A.12 / §A.14：ASR Provider 集中表（endpoint / model / provider raw）
// 新增 Provider 时：实现 Client + 在此注册即可。
package com.elder.android.data.asr

import com.elder.android.data.db.AsrProvider

/**
 * ASR Provider → endpoint / model 映射。
 * endpoint 与 model 必须与对应 Client 的 hardcoded 常量保持一致（§A.8 / §A.12）。
 */
object AsrProviderCatalog {
    fun endpointOf(p: AsrProvider): String = when (p) {
        AsrProvider.BAILIAN -> AsrApiClient.WS_URL
        AsrProvider.MINIMAX_REALTIME -> MiniMaxAsrClient.REST_URL
    }

    fun modelOf(p: AsrProvider): String = when (p) {
        AsrProvider.BAILIAN -> AsrApiClient.BAILIAN_MODEL
        AsrProvider.MINIMAX_REALTIME -> MiniMaxAsrClient.MINIMAX_ASR_MODEL
    }

    fun providerRaw(p: AsrProvider): String = when (p) {
        AsrProvider.BAILIAN -> AsrApiClient.BAILIAN_PROVIDER
        AsrProvider.MINIMAX_REALTIME -> MiniMaxAsrClient.MINIMAX_ASR_PROVIDER
    }

    /**
     * 给某个 raw provider string 解析对应的 client 实现（§A.14 ServiceLocator 工厂入口）。
     * 默认回退 Bailian 与 AsrProvider.fromRaw 一致。
     */
    fun resolveClient(provider: AsrProvider): AsrClient = when (provider) {
        AsrProvider.BAILIAN -> AsrApiClient()
        AsrProvider.MINIMAX_REALTIME -> MiniMaxAsrClient()
    }
}
