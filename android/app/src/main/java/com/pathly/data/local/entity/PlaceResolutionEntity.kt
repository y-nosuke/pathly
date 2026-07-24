package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Google Places に場所名を問い合わせた記録（解決ログ）。docs/designs/places-and-stops.md 参照。
 *
 * places を静的に保つため、動的な解決状態（叩いたか・いつ・結果）はここに分離する。
 * **行がある＝問い合わせ済み**（結果の有無を問わず）。無ければ未実施。
 * [googlePlaceId] は見つかった Google の place ID。POI が無ければ null。
 */
@Entity(
  tableName = "place_resolutions",
  foreignKeys = [
    ForeignKey(
      entity = PlaceEntity::class,
      parentColumns = ["id"],
      childColumns = ["placeId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class PlaceResolutionEntity(
  @PrimaryKey
  val placeId: Long,
  val resolvedAt: Date,
  val googlePlaceId: String? = null,
)
