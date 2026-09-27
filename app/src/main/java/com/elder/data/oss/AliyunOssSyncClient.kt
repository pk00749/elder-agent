// 对应 docs/v0.10.0.md §6.5：AliyunOssSyncClient（阿里云 OSS Java SDK 封装）。
// 实际 SDK 调用通过 OssOps 接口注入;prod 实现见 OssOpsAliyun（runtime 加载 aliyun-sdk-oss）。
// 为避免 compile-time 强依赖 aliyun-sdk-oss(可选 SDK,降低 APK 体积),prod 实现用反射 lazy init;
// 测试环境用 setOpsForTest() 注入桩。
package com.elder.android.data.oss

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "AliyunOssSyncClient"

class AliyunOssSyncClient : OssSyncClient {
    @Volatile private var ops: OssOps = DefaultOssOps()
    private val mutex = Object()

    override fun setOpsForTest(ops: OssOps) {
        synchronized(mutex) { this.ops = ops }
    }

    override suspend fun putObject(
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
    ): OssUploadResult = withContext(Dispatchers.IO) {
        val fullKey = joinKey(prefix, key)
        val now = System.currentTimeMillis()
        try {
            currentOps().putObject(
                endpoint = endpoint,
                bucket = bucket,
                accessKeyId = accessKeyId,
                accessKeySecret = accessKeySecret,
                stsToken = stsToken,
                key = fullKey,
                file = file,
                contentType = contentType,
            )
            OssUploadResult(objectKey = fullKey, syncedAtMs = now)
        } catch (e: OssSyncException) {
            throw e  // 已经是结构化异常,原样抛
        } catch (e: Throwable) {
            Log.w(TAG, "putObject failed: ${e.javaClass.simpleName}: ${e.message}")
            throw mapException(e)
        }
    }

    override suspend fun putText(
        prefix: String,
        key: String,
        body: String,
        endpoint: String,
        bucket: String,
        region: String,
        accessKeyId: String,
        accessKeySecret: String,
        stsToken: String?,
    ): OssUploadResult = withContext(Dispatchers.IO) {
        // 文本走临时文件 → putObject(避免 putObject 直接吃 File,统一接口)
        val tmp = File.createTempFile("oss_text_", ".json")
        try {
            tmp.writeText(body, Charsets.UTF_8)
            putObject(
                prefix = prefix,
                key = key,
                file = tmp,
                contentType = "application/json; charset=utf-8",
                endpoint = endpoint,
                bucket = bucket,
                region = region,
                accessKeyId = accessKeyId,
                accessKeySecret = accessKeySecret,
                stsToken = stsToken,
            )
        } finally {
            tmp.delete()
        }
    }

    private fun currentOps(): OssOps = synchronized(mutex) { ops }

    private fun joinKey(prefix: String, key: String): String {
        val p = prefix.trim('/')
        val k = key.trimStart('/')
        return if (p.isEmpty()) k else "$p/$k"
    }

    private fun mapException(e: Throwable): OssSyncException {
        val msg = e.message ?: ""
        val code = when {
            msg.contains("403", ignoreCase = true) || msg.contains("AccessDenied", ignoreCase = true) ||
                msg.contains("InvalidAccessKeyId", ignoreCase = true) -> OssError.OSS_AUTH_FAILED
            msg.contains("404", ignoreCase = true) || msg.contains("NoSuchBucket", ignoreCase = true) -> OssError.OSS_BUCKET_NOT_FOUND
            msg.contains("400", ignoreCase = true) || msg.contains("InvalidArgument", ignoreCase = true) -> OssError.OSS_BAD_REQUEST
            else -> OssError.OSS_NETWORK_ERROR
        }
        return OssSyncException(code = code, message = msg, cause = e)
    }
}

/**
 * Prod OssOps：阿里云 OSS Java SDK 反射 lazy init。
 * aliyun-sdk-oss 是可选依赖(AGENTS.md §A.16.3);首次调用时反射加载,失败抛 ClassNotFoundException → OssSyncException(OSS_AUTH_FAILED)。
 */
private class DefaultOssOps : OssOps {
    private val sdkClass: Class<*>? by lazy {
        runCatching { Class.forName("com.aliyun.oss.OSSClient") }.getOrNull()
    }

    override fun putObject(
        endpoint: String,
        bucket: String,
        accessKeyId: String,
        accessKeySecret: String,
        stsToken: String?,
        key: String,
        file: File,
        contentType: String,
    ) {
        val cls = sdkClass ?: throw OssSyncException(
            OssError.OSS_AUTH_FAILED,
            "aliyun-sdk-oss not on classpath;请在 app/build.gradle.kts 加 implementation(\"com.aliyun.oss:aliyun-sdk-oss:3.17.4\")",
        )
        // 反射调用 OSSClient(endpoint, provider, conf).putObject(bucket, key, file)
        // 仅 prod 路径使用;测试桩走 setOpsForTest,不会触发反射。
        runCatching {
            val provider = cls.classLoader
                ?.loadClass("com.aliyun.oss.common.auth.DefaultCredentialProvider")
                ?.getDeclaredConstructor(String::class.java, String::class.java, String::class.java)
                ?.newInstance(accessKeyId, accessKeySecret, stsToken)
                ?: error("DefaultCredentialProvider not found")
            val confCls = cls.classLoader.loadClass("com.aliyun.oss.ClientBuilderConfiguration")
            val client = cls.getDeclaredConstructor(
                String::class.java, Class.forName("com.aliyun.oss.common.auth.CredentialsProvider"), confCls,
            ).newInstance(endpoint, provider, confCls.getDeclaredConstructor().newInstance())
            val putObject = cls.getMethod(
                "putObject",
                String::class.java, String::class.java, java.io.InputStream::class.java,
            )
            file.inputStream().use { input ->
                putObject.invoke(client, bucket, key, input)
            }
        }.getOrElse { e ->
            if (e is OssSyncException) throw e
            throw OssSyncException(OssError.OSS_NETWORK_ERROR, "OSSClient.putObject failed: ${e.message}", e)
        }
    }
}
