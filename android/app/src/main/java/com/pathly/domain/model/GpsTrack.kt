package com.pathly.domain.model

import java.util.Date

data class GpsTrack(
  val id: Long,
  val startTime: Date,
  val endTime: Date?,
  val isActive: Boolean,
  /** ユーザーが付けた経路名。null/空なら未命名。 */
  val name: String? = null,
  /** お気に入り登録フラグ。 */
  val isFavorite: Boolean = false,
  /** この経路の立ち寄り件数（一覧の件数表示・並べ替え用。リポジトリが集計して渡す）。 */
  val stopCount: Int = 0,
  val points: List<GpsPoint> = emptyList(),
  /**
   * この経路の生GPS点の件数。履歴一覧は点をロードしないので集計値を受け取る。
   * 点を読み込む経路（詳細・記録中）では [points] の件数と一致する。
   */
  val pointCount: Int = points.size,
  /**
   * 保存済みの総移動距離（メートル）。記録の確定時に焼き込まれる。
   * null（記録中・v11 以前の未計算）なら [points] から都度計算する。
   */
  val storedDistanceMeters: Double? = null,
  val createdAt: Date,
  val updatedAt: Date,
  /**
   * 保存済みの補正後点列（smoothed_points）。リポジトリが読み込んで渡す。
   * null または空なら [points] から都度計算にフォールバックする（docs/designs/gps-smoothing.md）。
   */
  val smoothedOverride: List<GpsPoint>? = null,
) {
  /** 名前が付いているか（空白のみは未命名扱い）。 */
  val hasName: Boolean
    get() = !name.isNullOrBlank()

  /**
   * ノイズ補正後の点列（非破壊・表示や距離計算に使う）。原データ [points] は保持する。
   * 保存済みがあればそれを、無ければ都度計算した結果を使う。
   */
  val smoothedPoints: List<GpsPoint>
    get() = smoothedOverride?.takeIf { it.isNotEmpty() } ?: computedSmoothedPoints

  private val computedSmoothedPoints: List<GpsPoint> by lazy { TrackSmoother.smooth(points) }

  /**
   * 補正後の点列を、**途切れていない区間ごと**に分けたもの。地図の線も距離もこの単位で扱う。
   *
   * GPS が取れない時間帯（長いトンネル・アプリの更新や強制終了）の前後を 1 本に繋ぐと、
   * 通っていない直線が描かれ距離にも乗ってしまうため（→ adr/0022）。
   */
  val smoothedSegments: List<List<GpsPoint>>
    get() = TrackSegments.split(smoothedPoints)

  /** 途切れ（欠落）を含む経路か。 */
  val hasGap: Boolean
    get() = smoothedSegments.size > 1

  /**
   * 総移動距離（メートル）。確定済みなら保存値をそのまま使い、無ければ補正後の点列から計算する。
   * **途切れた区間はまたがない**（記録が無い区間を移動したことにしない）。
   *
   * 保存値を優先するのが要点で、以前は一覧を描くたびに全経路を平滑化し直していた。
   * Flow が再発行するたびインスタンスが作り直されて [computedSmoothedPoints] の
   * lazy キャッシュも捨てられるため、記録中は10秒ごとに全履歴分を再計算していた。
   */
  val totalDistanceMeters: Double
    get() = storedDistanceMeters ?: smoothedSegments.sumOf { TrackSmoother.totalDistanceMeters(it) }
}
