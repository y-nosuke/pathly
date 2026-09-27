package com.pathly.domain.model

/**
 * 一覧を業種で絞り込むときの選択肢。業種の分類（[PlaceCategoryGroup]）に「未分類」を足したもの。
 *
 * - **未分類**: Google の施設情報が無い場所（座標だけ・未取得）。
 * - **その他**: 施設情報はあるが、業種が分からないか、どの分類にも当たらない場所。
 *
 * 地図のアイコンは両者を区別しない（どちらも既定のピン）ので、[PlaceCategoryGroup] には足さず別に持つ。
 */
enum class PlaceCategoryFacet(val label: String) {
  FOOD("飲食"),
  CAFE("カフェ"),
  SHOPPING("買い物"),
  PARK("公園"),
  CULTURE("観光"),
  ENTERTAINMENT("遊び"),
  TRANSIT("交通"),
  LODGING("宿泊"),
  SERVICE("用事"),
  OTHER("その他"),
  UNCATEGORIZED("未分類"),
  ;

  companion object {
    /**
     * 場所の業種の選択肢を決める。[hasGoogleInfo] は Google の施設情報（`google_places` の行）があるか、
     * [categoryCode] は Google の業種（`cafe` など）。
     */
    fun of(hasGoogleInfo: Boolean, categoryCode: String?): PlaceCategoryFacet {
      if (!hasGoogleInfo) return UNCATEGORIZED
      return when (PlaceCategoryGroup.of(categoryCode)) {
        PlaceCategoryGroup.FOOD -> FOOD
        PlaceCategoryGroup.CAFE -> CAFE
        PlaceCategoryGroup.SHOPPING -> SHOPPING
        PlaceCategoryGroup.PARK -> PARK
        PlaceCategoryGroup.CULTURE -> CULTURE
        PlaceCategoryGroup.ENTERTAINMENT -> ENTERTAINMENT
        PlaceCategoryGroup.TRANSIT -> TRANSIT
        PlaceCategoryGroup.LODGING -> LODGING
        PlaceCategoryGroup.SERVICE -> SERVICE
        PlaceCategoryGroup.OTHER -> OTHER
      }
    }

    /** 場所の業種の選択肢。Google の施設情報は place ID の有無で判断する。 */
    fun of(place: Place): PlaceCategoryFacet = of(place.googlePlaceId != null, place.category?.code)
  }
}
