package com.pathly.data.local.entity

import androidx.room.ColumnInfo
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
  /**
   * 問い合わせで分かった施設を、**既に別の place が持っていた**ときの、その施設の Google の place ID
   * （→ adr/0037）。持っている place（相手）は `google_places` から引く（相手を消したり、相手が別の施設に
   * 付け替わったりしても食い違わないように、place の id ではなく施設で持つ）。
   * null なら同じ施設の相手はいない。
   */
  val sameFacilityGooglePlaceId: String? = null,
  /**
   * 同じ施設の相手がいても、**施設情報なしで別の場所として残す**とユーザーが決めた（→ adr/0037）。
   * true なら二度と聞かず、「未取得」にも数えず、Google に問い合わせ直さない。
   */
  @ColumnInfo(defaultValue = "0")
  val keptSeparate: Boolean = false,
)
