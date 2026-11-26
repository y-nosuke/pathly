package com.pathly.domain.model

/**
 * 位置の取り方。記録間隔と同じく、精度と電池のトレードオフをユーザーに選ばせる。
 *
 * [BALANCED] は「街区レベルで十分」という要求なので、OS は GNSS を使わず Wi-Fi や基地局から
 * 推定して済ませてよい。乗り物で移動していると Wi-Fi は当てにならず基地局も切り替わり続けるため、
 * 衛星が見えている区間でも測位が返らないことがある。[HIGH] は GNSS を積極的に使うので
 * そうした区間が埋まるが、電池を多く使う（トンネルなど衛星が見えない区間はどちらでも取れない）。
 */
enum class LocationAccuracy(val key: String) {
  /** 省電力。Wi-Fi・基地局中心（既定）。 */
  BALANCED("balanced"),

  /** 高精度。GNSS を積極的に使う。 */
  HIGH("high"),
  ;

  companion object {
    val DEFAULT = BALANCED

    fun fromKey(key: String?): LocationAccuracy = entries.firstOrNull { it.key == key } ?: DEFAULT
  }
}
