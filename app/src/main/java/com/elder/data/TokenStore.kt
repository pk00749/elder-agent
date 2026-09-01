// Token 存储 —— DataStore-backed（§7.4 单 JWT 7 天有效，§11.11 无 refresh token）
package com.elder.android.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "elder_auth")

class TokenStore(private val context: Context) {
    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = stringPreferencesKey("user_id")
        val ROLE = stringPreferencesKey("role")     // "elder" | "family"
        val ELDER_ID = stringPreferencesKey("elder_id")
    }

    data class Snapshot(
        val token: String,
        val userId: String,
        val role: String,
        val elderId: String?,
    )

    val snapshot: Flow<Snapshot?> = context.dataStore.data.map { prefs ->
        val token = prefs[Keys.TOKEN] ?: return@map null
        val userId = prefs[Keys.USER_ID] ?: return@map null
        val role = prefs[Keys.ROLE] ?: return@map null
        val elderId = prefs[Keys.ELDER_ID]
        Snapshot(token, userId, role, elderId)
    }

    suspend fun current(): Snapshot? = snapshot.first()

    suspend fun save(token: String, userId: String, role: String, elderId: String?) {
        context.dataStore.edit { prefs: Preferences ->
            prefs[Keys.TOKEN] = token
            prefs[Keys.USER_ID] = userId
            prefs[Keys.ROLE] = role
            if (elderId != null) prefs[Keys.ELDER_ID] = elderId
        }
    }

    suspend fun updateElderId(elderId: String) {
        context.dataStore.edit { prefs: Preferences ->
            prefs[Keys.ELDER_ID] = elderId
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
