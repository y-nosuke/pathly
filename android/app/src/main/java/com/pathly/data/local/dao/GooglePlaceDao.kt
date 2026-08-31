package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.GooglePlaceWithCategory

@Dao
interface GooglePlaceDao {

  /**
   * Google 由来データを追加／更新する（再取得で上書き）。
   *
   * REPLACE は使わない。REPLACE は削除してから入れ直すので、`googlePlaceId` の UNIQUE に
   * ぶつかった行を**黙って消して**別の place に付け替えてしまう。`@Upsert` は主キー（placeId）の
   * 衝突だけを更新に読み替え、施設の重複はエラーにする。**呼ぶ前に
   * [getPlaceIdByGoogleId] で他の place が持っていないか確かめること**（→ adr/0025）。
   */
  @Upsert
  suspend fun upsert(googlePlace: GooglePlaceEntity)

  @Query("SELECT * FROM google_places WHERE placeId = :placeId")
  suspend fun getByPlace(placeId: Long): GooglePlaceEntity?

  /** [getByPlace] に業種（マスタ）を結合したもの。表示にカテゴリ名が要るときはこちら。 */
  @Transaction
  @Query("SELECT * FROM google_places WHERE placeId = :placeId")
  suspend fun getWithCategoryByPlace(placeId: Long): GooglePlaceWithCategory?

  /** その googlePlaceId を持つ place の id（施設の同一性での同定に使う）。無ければ null。 */
  @Query("SELECT placeId FROM google_places WHERE googlePlaceId = :googlePlaceId LIMIT 1")
  suspend fun getPlaceIdByGoogleId(googlePlaceId: String): Long?
}
