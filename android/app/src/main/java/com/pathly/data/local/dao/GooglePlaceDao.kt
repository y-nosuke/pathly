package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathly.data.local.entity.GooglePlaceEntity

@Dao
interface GooglePlaceDao {

  /** Google 由来データを追加／更新する（再取得で上書き）。 */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(googlePlace: GooglePlaceEntity)

  @Query("SELECT * FROM google_places WHERE placeId = :placeId")
  suspend fun getByPlace(placeId: Long): GooglePlaceEntity?
}
