// 对应 docs/v0.10.0.md §6.2：阿里云 OSS 凭据 Keystore-wrapped 加密封装。
// 与 ApiKeyCipher 同模式(EncryptedSharedPreferences + MasterKey AES256_GCM),prefs 文件独立。
package com.elder.android.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class OssKeyCipher(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "elder_oss_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (t: Throwable) {
        Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to in-memory map", t)
        OssInMemorySharedPreferences()
    }

    /** 解密取明文；调用方用完立刻置 null + GC。 */
    fun decrypt(token: String): String? = prefs.getString(token, null)

    /** 加密并落盘。 */
    fun encryptAndStore(token: String, plaintext: String) {
        prefs.edit().putString(token, plaintext).apply()
    }

    fun clear(token: String? = null) {
        prefs.edit().apply {
            if (token == null) clear() else remove(token)
        }.apply()
    }

    companion object {
        private const val TAG = "OssKeyCipher"
        // alias 必须与 OssConfigEntity 字段一一对应
        const val KEY_ACCESS_KEY_ID_ENC = "oss_access_key_id"
        const val KEY_ACCESS_KEY_SECRET_ENC = "oss_access_key_secret"
        const val KEY_STS_TOKEN_ENC = "oss_sts_token"
    }
}

/** 内存兜底 prefs(Keystore 不可用时)。 */
private class OssInMemorySharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()
    override fun getAll(): MutableMap<String, *> = map
    override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        @Suppress("UNCHECKED_CAST") (map[key] as? Set<String> ?: defValues)
    override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = apply { pending[key] = values }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value }
        override fun remove(key: String): SharedPreferences.Editor = apply { pending[key] = null }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) map.clear()
            map.putAll(pending)
        }
    }
}
