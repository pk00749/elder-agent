// §5.12 device_meta DAO
package com.elder.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceMetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: DeviceMetaEntity)

    @Query("SELECT * FROM device_meta WHERE id = 1 LIMIT 1")
    suspend fun get(): DeviceMetaEntity?

    @Query("SELECT * FROM device_meta WHERE id = 1 LIMIT 1")
    fun observe(): Flow<DeviceMetaEntity?>

    @Query("UPDATE device_meta SET last_active_at = :now WHERE id = 1")
    suspend fun touchActive(now: Long)

    @Query("DELETE FROM device_meta")
    suspend fun clear()
}
