package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.GooglePlaceWithCategory

@Dao
interface GooglePlaceDao {

  /** Google 由来データを追加／更新する（再取得で上書き）。 */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(googlePlace: GooglePlaceEntity)

  @Query("SELECT * FROM google_places WHERE placeId = :placeId")
  suspend fun getByPlace(placeId: Long): GooglePlaceEntity?

  /** [getByPlace] に業種（マスタ）を結合したもの。表示にカテゴリ名が要るときはこちら。 */
  @Transaction
  @Query("SELECT * FROM google_places WHERE placeId = :placeId")
  suspend fun getWithCategoryByPlace(placeId: Long): GooglePlaceWithCategory?

  /**
   * TODO(v13-backfill): 移行用。業種がまだ入っていない行（v13 より前に解決した場所）。
   * 流し終えたら [com.pathly.data.work.PlaceCategoryBackfillWorker] ごと削除する。
   */
  @Query("SELECT * FROM google_places WHERE categoryId IS NULL")
  suspend fun getWithoutCategory(): List<GooglePlaceEntity>

  /** 業種だけを差し替える（名前・住所は触らない）。 */
  @Query("UPDATE google_places SET categoryId = :categoryId WHERE placeId = :placeId")
  suspend fun updateCategory(placeId: Long, categoryId: Long?)

  /** その googlePlaceId を持つ place の id（施設の同一性での同定に使う）。無ければ null。 */
  @Query("SELECT placeId FROM google_places WHERE googlePlaceId = :googlePlaceId LIMIT 1")
  suspend fun getPlaceIdByGoogleId(googlePlaceId: String): Long?
}
