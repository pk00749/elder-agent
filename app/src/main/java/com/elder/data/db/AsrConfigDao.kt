// §5.11 asr_config DAO
package com.elder.android.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AsrConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: AsrConfigEntity)

    @Query("SELECT * FROM asr_config WHERE id = 1 LIMIT 1")
    suspend fun get(): AsrConfigEntity?

    @Query("SELECT * FROM asr_config WHERE id = 1 LIMIT 1")
    fun observe(): Flow<AsrConfigEntity?>

    @Query("DELETE FROM asr_config")
    suspend fun clear()
}
