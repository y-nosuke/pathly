package com.pathly.data.places

import com.google.android.libraries.places.api.model.Place
import com.pathly.domain.model.PlaceCategory

/**
 * Places SDK の応答から業種を取り出す。
 *
 * 正は機械可読な `primaryType` の方で、表示名（ロケール依存）はその属性として添えるだけ。
 * `primaryType` が無い POI（業種の付いていない地点）はカテゴリ無しとして扱う。
 */
internal fun Place.toPlaceCategory(): PlaceCategory? {
  val code = primaryType?.takeIf { it.isNotBlank() } ?: return null
  return PlaceCategory(code, primaryTypeDisplayName?.takeIf { it.isNotBlank() })
}

/**
 * 名前・住所・業種を引くときに要求するフィールド。
 *
 * `PRIMARY_TYPE` と `PRIMARY_TYPE_DISPLAY_NAME` は同じ課金区分（Pro）なので、両方要求しても
 * 呼び出し単価は変わらない。取得箇所ごとに列挙がずれると「ある画面だけ業種が入らない」
 * という差が出るため、1 か所にまとめる。
 */
internal val PLACE_DETAIL_FIELDS = listOf(
  Place.Field.ID,
  Place.Field.DISPLAY_NAME,
  Place.Field.FORMATTED_ADDRESS,
  Place.Field.PRIMARY_TYPE,
  Place.Field.PRIMARY_TYPE_DISPLAY_NAME,
  Place.Field.LOCATION,
)
