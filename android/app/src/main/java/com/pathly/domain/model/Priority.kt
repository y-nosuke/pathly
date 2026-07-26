package com.pathly.domain.model

/**
 * 行きたい場所の優先度（行きたい度合い）。DB では [value]（0/1/2）で保持する。
 */
enum class Priority(val value: Int) {
  LOW(0),
  MEDIUM(1),
  HIGH(2),
  ;

  companion object {
    fun fromValue(value: Int): Priority = entries.firstOrNull { it.value == value } ?: MEDIUM
  }
}
