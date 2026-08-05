package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Google Places が返した場所データ（place 単位）。ユーザー入力（places）とは分けて持つ。
 * 詳細は docs/designs/place-info-enrichment.md / adr/0001 を参照。
 *
 * **行がある＝Google で該当 POI が見つかった**（[googlePlaceId] は非 null）。
 * 「叩いたが該当なし」は place_resolutions に行だけ残し、ここには作らない。
 * name/address/category は POI に無ければ null。
 */
@Entity(
  tableName = "google_places",
  foreignKeys = [
    ForeignKey(
      entity = PlaceEntity::class,
      parentColumns = ["id"],
      childColumns = ["placeId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class GooglePlaceEntity(
  @PrimaryKey
  val placeId: Long,
  /** 見つかった Google の place ID。Place Details 取得の参照キーにもなる。 */
  val googlePlaceId: String,
  /** Google の施設名（displayName）。 */
  val name: String? = null,
  /** Google の住所（formattedAddress）。 */
  val address: String? = null,
  /** Google のカテゴリ（primaryTypeDisplayName）。 */
  val category: String? = null,
)
