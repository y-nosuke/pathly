package com.pathly.domain.model

/** 画面の明暗。知らない値は端末の設定に従う。 */
enum class ThemeMode(val key: String) {
  /** 端末の設定に従う（既定）。 */
  SYSTEM("system"),

  /** いつも明るい。 */
  LIGHT("light"),

  /** いつも暗い。 */
  DARK("dark"),
  ;

  companion object {
    val DEFAULT = SYSTEM

    fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: DEFAULT
  }
}
