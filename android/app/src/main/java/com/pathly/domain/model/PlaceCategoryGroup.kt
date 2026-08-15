package com.pathly.domain.model

/**
 * 場所の業種を、地図のアイコンとして描き分けられる粒度に束ねたもの
 * （→ [../../../../../../../docs/adr/0017-normalize-place-category.md]）。
 *
 * Google の primaryType は数百あり、そのままではアイコンを用意しきれない。ここで意味の近いものを
 * まとめる。**判定は [PlaceCategory.code] だけを見る**（表示名はロケールで変わるので使わない）。
 *
 * 対応表は「主要な型を完全一致で拾い、残りは接尾辞で拾う」の 2 段構え。Google の型は増減するため、
 * 知らない型が来ても `ramen_restaurant` なら [FOOD]、`shoe_store` なら [SHOPPING] に落ちるようにして、
 * 一覧の網羅性に依存しないようにしている。どちらにも当たらなければ [OTHER]。
 */
enum class PlaceCategoryGroup {
  /** 飲食店。 */
  FOOD,

  /** カフェ・パン屋など。飲食店とは分けたい（滞在の意味が違う）。 */
  CAFE,

  /** 買い物（店・スーパー・商業施設）。 */
  SHOPPING,

  /** 公園・自然。 */
  PARK,

  /** 観光・文化・寺社（見に行く場所）。 */
  CULTURE,

  /** 遊ぶ場所（遊園地・動物園・映画館など）。 */
  ENTERTAINMENT,

  /** 駅・空港・駐車場など、移動の結節点。 */
  TRANSIT,

  /** 宿泊。 */
  LODGING,

  /** 生活の用事（病院・銀行・役所など）。 */
  SERVICE,

  /** 上記のどれでもない・業種が分からない。 */
  OTHER,

  ;

  companion object {
    // 完全一致の対応表。Google の型のうち、実際によく出るものと、接尾辞では拾えないものを挙げる。
    private val EXACT: Map<String, PlaceCategoryGroup> = buildMap {
      listOf(
        "restaurant", "fast_food_restaurant", "meal_takeaway", "meal_delivery",
        "bar", "pub", "bar_and_grill", "food_court", "buffet_restaurant", "fine_dining_restaurant",
      ).forEach { put(it, FOOD) }

      listOf(
        "cafe", "coffee_shop", "bakery", "cat_cafe", "dog_cafe", "dessert_shop",
        "ice_cream_shop", "juice_shop", "tea_house", "confectionery",
      ).forEach { put(it, CAFE) }

      listOf(
        "store", "supermarket", "convenience_store", "grocery_store", "shopping_mall",
        "department_store", "market", "wholesaler", "warehouse_store", "discount_store",
      ).forEach { put(it, SHOPPING) }

      listOf(
        "park", "national_park", "state_park", "garden", "botanical_garden", "dog_park",
        "hiking_area", "campground", "camping_cabin", "beach", "picnic_ground", "plaza",
      ).forEach { put(it, PARK) }

      listOf(
        "museum", "art_gallery", "tourist_attraction", "historical_landmark", "historical_place",
        "monument", "library", "cultural_center", "cultural_landmark", "observation_deck",
        "church", "hindu_temple", "mosque", "synagogue", "place_of_worship",
        "shinto_shrine", "buddhist_temple",
      ).forEach { put(it, CULTURE) }

      listOf(
        "amusement_park", "theme_park", "water_park", "zoo", "aquarium", "movie_theater",
        "stadium", "arena", "bowling_alley", "night_club", "casino", "concert_hall",
        "performing_arts_theater", "video_arcade", "karaoke", "gym", "fitness_center",
        "sports_complex", "swimming_pool", "golf_course", "ski_resort",
      ).forEach { put(it, ENTERTAINMENT) }

      listOf(
        "train_station", "subway_station", "light_rail_station", "bus_station", "bus_stop",
        "transit_station", "transit_depot", "airport", "international_airport", "ferry_terminal",
        "parking", "park_and_ride", "taxi_stand", "rest_stop",
      ).forEach { put(it, TRANSIT) }

      listOf(
        "hotel", "lodging", "motel", "hostel", "inn", "japanese_inn", "resort_hotel",
        "bed_and_breakfast", "guest_house", "campground_lodging", "extended_stay_hotel",
      ).forEach { put(it, LODGING) }

      listOf(
        "hospital", "doctor", "dentist", "pharmacy", "drugstore", "clinic", "veterinary_care",
        "bank", "atm", "post_office", "city_hall", "local_government_office", "police",
        "fire_station", "school", "university", "primary_school", "secondary_school",
        "gas_station", "electric_vehicle_charging_station", "car_repair", "car_wash",
        "laundry", "hair_salon", "beauty_salon", "barber_shop", "spa", "embassy", "courthouse",
      ).forEach { put(it, SERVICE) }
    }

    // 接尾辞での取りこぼし拾い。Google が型を増やしても既定のピンに落ちないようにする。
    // 順番に意味がある（`coffee_shop` は SHOPPING ではなく CAFE に入れたいので EXACT が先に効く）。
    private val SUFFIXES: List<Pair<String, PlaceCategoryGroup>> = listOf(
      "_restaurant" to FOOD,
      "_cafe" to CAFE,
      "_bakery" to CAFE,
      "_store" to SHOPPING,
      "_shop" to SHOPPING,
      "_market" to SHOPPING,
      "_station" to TRANSIT,
      "_airport" to TRANSIT,
      "_parking" to TRANSIT,
      "_park" to PARK,
      "_museum" to CULTURE,
      "_temple" to CULTURE,
      "_shrine" to CULTURE,
      "_church" to CULTURE,
      "_hotel" to LODGING,
      "_lodging" to LODGING,
      "_school" to SERVICE,
      "_hospital" to SERVICE,
    )

    /** 業種から分類を引く。業種が無い（Google が返さなかった）場合は [OTHER]。 */
    fun of(category: PlaceCategory?): PlaceCategoryGroup = of(category?.code)

    /** [PlaceCategory.code] から分類を引く。未知の型は接尾辞で拾い、それでも駄目なら [OTHER]。 */
    fun of(code: String?): PlaceCategoryGroup {
      val key = code?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return OTHER
      EXACT[key]?.let { return it }
      return SUFFIXES.firstOrNull { (suffix, _) -> key.endsWith(suffix) }?.second ?: OTHER
    }
  }
}
