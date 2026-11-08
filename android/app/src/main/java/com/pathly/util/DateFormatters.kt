package com.pathly.util

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * 画面表示用の日時フォーマット。
 *
 * 以前は `SimpleDateFormat` のインスタンスを object の val で共有していたが、
 * これには2つの問題があった。
 *  - `SimpleDateFormat` は**スレッド安全でない**のに、singleton を各所から呼んでいた
 *  - `Locale.getDefault()` をクラス初期化時に固定するため、実行中にロケールを変えても追随しない
 *
 * [DateTimeFormatter] は不変・スレッド安全なので共有でき、ロケールとタイムゾーンは
 * 整形のたびに現在値を適用する（[withLocale] / [withZone] はパターンを解析し直さない）。
 *
 * 画面に出す日付には曜日を添える（平日か休日か・何曜日のお出掛けかをすぐ分かるように）。
 * 曜日はアプリの文言に合わせて**日本語に固定**する（端末の言語が英語でも「(日)」。文言が日本語で固定のため）。
 */
object DateFormatters {

  private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
  private val SHORT_TIME = DateTimeFormatter.ofPattern("HH:mm")
  private val DATE = DateTimeFormatter.ofPattern("yyyy年MM月dd日(E)", Locale.JAPANESE)
  private val SHORT_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd(E)", Locale.JAPANESE)
  private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

  /** 時刻（HH:mm:ss）。 */
  fun time(date: Date): String = TIME.render(date)

  /** 時刻（HH:mm）。 */
  fun shortTime(date: Date): String = SHORT_TIME.render(date)

  /** 曜日つきの日付（yyyy年MM月dd日(E)。例: 2026年11月08日(日)）。 */
  fun date(date: Date): String = DATE.renderInJapanese(date)

  /** 曜日つきの日付（yyyy/MM/dd(E)。例: 2026/11/08(日)）。 */
  fun shortDate(date: Date): String = SHORT_DATE.renderInJapanese(date)

  /** ファイル名に入れる日時（yyyyMMdd-HHmm）。 */
  fun fileStamp(date: Date): String = FILE_STAMP.render(date)

  private fun DateTimeFormatter.render(date: Date): String = withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())
    .format(date.toInstant())

  // 曜日の名前を日本語に固定する（ロケールは差し替えない）。
  private fun DateTimeFormatter.renderInJapanese(date: Date): String = withZone(ZoneId.systemDefault()).format(date.toInstant())
}
