package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Google Places を「叩いたか」を place 単位で記録する問い合わせログ。
 * docs/designs/place-info-enrichment.md / adr/0001 参照。
 *
 * places を静的に保つため、動的な解決状態（叩いたか・いつ）はここに分離する。
 * **行がある＝問い合わせ済み**（結果の有無を問わず）。無ければ未実施。
 * 見つかった Google のデータ（place ID・名前等）は google_places に持つ。
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
)
