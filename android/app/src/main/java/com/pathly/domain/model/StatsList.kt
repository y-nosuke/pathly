package com.pathly.domain.model

/** 統計の帯（と広げたカード）を置く一覧。[key] は設定のキーに使うので変えない。 */
enum class StatsList(val key: String) {
  HISTORY("history_list"),
  PLACES("places_list"),
}
