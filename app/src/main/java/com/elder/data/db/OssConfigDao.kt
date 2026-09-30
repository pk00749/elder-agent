// 对应 docs/v0.10.0.md §6.2：oss_config DAO（单行表 id=1）
package com.elder.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OssConfigDao {
    @Query("SELECT * FROM oss_config WHERE id = 1")
    suspend fun get(): OssConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OssConfigEntity)

    @Query("UPDATE oss_config SET last_sync_at = :syncAt, last_sync_result = :resultJson, updated_at = :now WHERE id = 1")
    suspend fun updateSyncResult(syncAt: Long, resultJson: String, now: Long)

    @Query("DELETE FROM oss_config WHERE id = 1")
    suspend fun clear()
}
