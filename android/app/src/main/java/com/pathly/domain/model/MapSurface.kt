package com.pathly.domain.model

/** 「登録済みの場所」トグルを保持する画面（それぞれ独立して ON/OFF を記憶する）。 */
enum class MapSurface(val key: String) {
  RECORDING("recording"),
  HISTORY("history"),
  PLACE_DETAIL("place_detail"),
  PLACE_ADD("place_add"),
  HISTORY_MAP("history_map"),
  PLAN_DETAIL("plan_detail"),
}
