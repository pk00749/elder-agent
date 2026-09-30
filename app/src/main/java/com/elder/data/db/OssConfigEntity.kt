// 对应 docs/v0.10.0.md §6.2：oss_config Room 表（v0.10.0 新增；单行表 id=1）
// 存储阿里云 OSS 凭据(endpoint/bucket/region/access key id/secret/可选 STS/prefix)
// 与 §5.11 asr_config 同样:密钥字段 Keystore-wrapped 密文(EncryptedSharedPreferences + Android Keystore AES/GCM)。
// 不在 §18 锁定列表;允许 v0.10.0 新增表。
package com.elder.android.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "oss_config",
    indices = [Index(value = ["updated_at"])],
)
data class OssConfigEntity(
    @PrimaryKey val id: Int = 1,                                  // 单行表
    @ColumnInfo(name = "endpoint") val endpoint: String,         // 例 https://oss-cn-hangzhou.aliyuncs.com
    @ColumnInfo(name = "bucket") val bucket: String,             // OSS bucket name
    @ColumnInfo(name = "region") val region: String,             // 例 cn-hangzhou
    @ColumnInfo(name = "prefix") val prefix: String,             // 对象 key 前缀,默认 elder/{device_id}/
    @ColumnInfo(name = "sync_on_wifi_only") val syncOnWifiOnly: Boolean = true,  // 默认 Wi-Fi Only
    @ColumnInfo(name = "access_key_id_enc") val accessKeyIdEnc: String,         // Keystore-wrapped 密文
    @ColumnInfo(name = "access_key_secret_enc") val accessKeySecretEnc: String, // Keystore-wrapped 密文
    @ColumnInfo(name = "sts_token_enc") val stsTokenEnc: String? = null,        // 可选 STS(临时凭据)
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long? = null,            // epoch ms
    @ColumnInfo(name = "last_sync_result") val lastSyncResult: String? = null,  // JSON {"synced":N,"failed":M}
    @ColumnInfo(name = "updated_at") val updatedAt: Long,                       // epoch ms
)
