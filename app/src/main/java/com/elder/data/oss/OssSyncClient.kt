// 对应 docs/v0.10.0.md §6.5：OssSyncClient 接口 + Aliyun 实现 + 错误码映射。
// 内部 OssOps 抽接口便于测试桩(mock OSSClient);prod 实现用阿里云 OSS Java SDK。
package com.elder.android.data.oss

import java.io.File

/** 上传错误码（与 §A.16.3 / §A.8 错误码风格对齐）。 */
object OssError {
    const val OSS_AUTH_FAILED = "OSS_AUTH_FAILED"
    const val OSS_NETWORK_ERROR = "OSS_NETWORK_ERROR"
    const val OSS_BUCKET_NOT_FOUND = "OSS_BUCKET_NOT_FOUND"
    const val OSS_BAD_REQUEST = "OSS_BAD_REQUEST"
}

/** 上传异常（带错误码 + message），由 Repository 捕获并落 diary_entry_local.oss_last_error。 */
class OssSyncException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** 上传结果。 */
data class OssUploadResult(
    val objectKey: String,
    val syncedAtMs: Long,
)

/** 内部 SDK 接口（便于测试桩；prod 实现 = AliyunOssOps）。 */
interface OssOps {
    fun putObject(
        endpoint: String,
        bucket: String,
        accessKeyId: String,
        accessKeySecret: String,
        stsToken: String?,
        key: String,
        file: File,
        contentType: String,
    )
}

/** 客户端接口（Repository / Worker 调用）。 */
interface OssSyncClient {
    /**
     * 上传本地音频文件到 OSS。
     * @return 上传后的完整对象 key(prefix + '/' + key)
     * @throws OssSyncException 失败抛异常,code ∈ OssError 常量
     */
    suspend fun putObject(
        prefix: String,
        key: String,
        file: File,
        contentType: String,
        endpoint: String,
        bucket: String,
        region: String,
        accessKeyId: String,
        accessKeySecret: String,
        stsToken: String?,
    ): OssUploadResult

    /**
     * 上传文本(JSON 字符串)到 OSS。
     */
    suspend fun putText(
        prefix: String,
        key: String,
        body: String,
        endpoint: String,
        bucket: String,
        region: String,
        accessKeyId: String,
        accessKeySecret: String,
        stsToken: String?,
    ): OssUploadResult

    /** 测试用：注入 mock OssOps。 */
    fun setOpsForTest(ops: OssOps)
}
