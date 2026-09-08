// §5.12 device_meta 仓储；首启初始化；后续 read/write
package com.elder.android.data

import android.content.Context
import com.elder.android.BuildConfig
import com.elder.android.data.db.DeviceMetaDao
import com.elder.android.data.db.DeviceMetaEntity
import com.elder.android.data.db.ElderDatabase
import com.elder.android.data.db.FontScale
import java.util.TimeZone
import java.util.UUID

class DeviceMetaRepository(private val dao: DeviceMetaDao) {
    suspend fun get(): DeviceMetaEntity? = dao.get()

    suspend fun ensureInitialized(now: Long = System.currentTimeMillis()): DeviceMetaEntity {
        dao.get()?.let { return it }
        val fresh = DeviceMetaEntity(
            id = 1,
            deviceId = "local",  // MVP 固定值；§5.10 device_id = "local"
            deviceToken = UUID.randomUUID().toString(),  // 预留给 v2.x family_user.device_token
            fontScale = FontScale.DEFAULT,
            ttsEnabled = true,
            timezone = TimeZone.getDefault().id,
            appVersion = BuildConfig.VERSION_NAME,
            firstLaunchAt = now,
            lastActiveAt = now,
        )
        dao.upsert(fresh)
        return fresh
    }

    suspend fun updateFontScale(scale: FontScale, now: Long = System.currentTimeMillis()) {
        val meta = ensureInitialized(now)
        dao.upsert(meta.copy(fontScale = scale, lastActiveAt = now))
    }

    suspend fun updateTtsEnabled(enabled: Boolean, now: Long = System.currentTimeMillis()) {
        val meta = ensureInitialized(now)
        dao.upsert(meta.copy(ttsEnabled = enabled, lastActiveAt = now))
    }

    suspend fun touchActive(now: Long = System.currentTimeMillis()) = dao.touchActive(now)

    suspend fun clear() = dao.clear()

    companion object {
        fun get(context: Context): DeviceMetaRepository =
            DeviceMetaRepository(ElderDatabase.get(context).deviceMetaDao())
    }
}
