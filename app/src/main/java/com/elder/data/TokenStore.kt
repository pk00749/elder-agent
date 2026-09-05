// Token 存储 —— DataStore-backed（§7.4 单 JWT 7 天有效，§11.11 无 refresh token）
// v2.1.2 增加 device_token：MVP anonymous-device 鉴权唯一标识
//
// 字段可空性：
//   - deviceToken：始终存在（首次启动 ensureDeviceToken 生成）
//   - role / token / userId：用户在 IdentitySelection 选定身份 + initDevice/bind 后才有
//   - elderId：仅 elder 端 bind/confirm accept 后回填
package com.elder.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "elder_auth")

class TokenStore(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private object Keys {
        val DEVICE_TOKEN = stringPreferencesKey("device_token")  // v2.1.2：始终存在
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = stringPreferencesKey("user_id")
        val ROLE = stringPreferencesKey("role")     // "elder" | "family"
        val ELDER_ID = stringPreferencesKey("elder_id")
    }

    data class Snapshot(
        val deviceToken: String,           // MVP 唯一必需
        val role: String? = null,          // "elder" | "family"
        val token: String? = null,
        val userId: String? = null,
        val elderId: String? = null,
    )

    val snapshot: StateFlow<Snapshot?> = context.dataStore.data.map { prefs ->
        val deviceToken = prefs[Keys.DEVICE_TOKEN] ?: return@map null
        Snapshot(
            deviceToken = deviceToken,
            role = prefs[Keys.ROLE],
            token = prefs[Keys.TOKEN],
            userId = prefs[Keys.USER_ID],
            elderId = prefs[Keys.ELDER_ID],
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun current(): Snapshot? = snapshot.first()
    fun currentSync(): Snapshot? = snapshot.value

    /** 写鉴权四元组 —— 不动 device_token。 */
    suspend fun save(token: String, userId: String, role: String, elderId: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token
            prefs[Keys.USER_ID] = userId
            prefs[Keys.ROLE] = role
            if (elderId != null) prefs[Keys.ELDER_ID] = elderId
        }
    }

    /** 仅记身份（v2.1.2 elder pre-bind：用户在 IdentitySelection 选 elder 后立即持久化 role）。 */
    suspend fun saveRole(role: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ROLE] = role
        }
    }

    /**
     * 读 device_token；缺则生成 UUID v4 并持久化（v2.1.2 anonymous-device 流程）。
     *
     * 必须在首次调 /v1/auth/anonymous-device 或首次 elder 端操作之前调用。
     */
    suspend fun ensureDeviceToken(): String {
        val current = snapshot.value?.deviceToken
        if (current != null) return current
        val fresh = UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.DEVICE_TOKEN] = fresh }
        return fresh
    }

    suspend fun updateElderId(elderId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ELDER_ID] = elderId
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
