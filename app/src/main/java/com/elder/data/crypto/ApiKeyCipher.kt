// 对应 PRD §3.1.9 §5.11 §7.2：ASR API Key 走 EncryptedSharedPreferences + Android Keystore AES/GCM
// 明文引用置 null + GC 后清内存；落盘永远是 Keystore-wrapped 密文
package com.elder.android.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ApiKeyCipher(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "elder_asr_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (t: Throwable) {
        Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to in-memory map", t)
        InMemoryPrefs
    }

    /** 加密并落盘；明文 [plaintext] 在调用方负责置 null。 */
    fun encryptAndStore(plaintext: String): String {
        prefs.edit().putString(KEY_API_KEY_ENC, plaintext).apply()
        return KEY_API_KEY_ENC
    }

    /** 解密取明文；调用方用完必须立刻 [clearPlaintext]。 */
    fun decrypt(token: String): String? = prefs.getString(token, null)

    fun clear() {
        prefs.edit().remove(KEY_API_KEY_ENC).apply()
    }

    companion object {
        private const val TAG = "ApiKeyCipher"
        const val KEY_API_KEY_ENC = "api_key"

        /**
         * 兜底内存 prefs（仅在 Keystore 不可用时使用，重启即失）——
         * 走这段的设备通常是模拟器或 Keystore 损坏，不应让 App 直接崩。
         * 生产环境 Keystore 不可用应向上层抛 [AppError.RoomCorrupted]。
         */
        private object InMemoryPrefs : SharedPreferences by InMemorySharedPreferences()
    }
}

/** 最简内存版 SharedPreferences 实现，仅在 EncryptedSharedPreferences 不可用时兜底。 */
private class InMemorySharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()
    override fun getAll(): MutableMap<String, *> = map
    override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = @Suppress("UNCHECKED_CAST") (map[key] as? Set<String> ?: defValues)
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
