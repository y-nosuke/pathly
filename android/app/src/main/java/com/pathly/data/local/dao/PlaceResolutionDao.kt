package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathly.data.local.entity.PlaceResolutionEntity

@Dao
interface PlaceResolutionDao {

  /** 解決記録を追加／更新する（再取得で上書き）。 */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(resolution: PlaceResolutionEntity)

  @Query("SELECT * FROM place_resolutions WHERE placeId = :placeId")
  suspend fun getByPlace(placeId: Long): PlaceResolutionEntity?
}
