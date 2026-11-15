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

  /**
   * 同じ施設の相手がいても、施設情報なしで別の場所として残すと決める（→ adr/0037）。
   * 以後は聞かず、「未取得」にも数えない。確認待ちの場所には必ず解決記録があるので、行を足す必要はない。
   */
  @Query("UPDATE place_resolutions SET keptSeparate = 1 WHERE placeId = :placeId")
  suspend fun keepSeparate(placeId: Long)
}
