package com.pathly.domain.model

/**
 * 点列を「途切れていない区間」に切り分ける。
 *
 * GPS が取れない時間帯（長いトンネル・アプリの更新や強制終了・圏外）があると、その前後の点は
 * 時間的に大きく離れる。これを 1 本の線として繋ぐと、**通っていない直線**が地図に描かれ、
 * 距離にもそのぶんが乗ってしまう。欠落は欠落のまま扱い、**区間をまたいで繋がない**
 * （→ adr/0022）。GPX の `<trkseg>` と同じ考え方。
 */
object TrackSegments {

  /**
   * これ以上空いたら「途切れた」とみなす（ミリ秒）。
   *
   * 設定できる最長の記録間隔が 60 秒なので、その 3 倍。1〜2 回の取りこぼしでは切らず、
   * 「しばらく取れていない」ときだけ切る。短いトンネルのように数十秒の欠落は、
   * 前後を直線で結んでもおおむね実態に合う（トンネルはほぼ直線）ので切らない。
   */
  const val GAP_MILLIS = 3 * 60 * 1000L

  /**
   * 点列を区間に切り分ける。点が無ければ空、途切れが無ければ 1 区間。
   * 入力は時刻の昇順である前提（保存も取得もその順）。
   */
  fun split(points: List<GpsPoint>, gapMillis: Long = GAP_MILLIS): List<List<GpsPoint>> {
    if (points.isEmpty()) return emptyList()
    val segments = mutableListOf<List<GpsPoint>>()
    var current = mutableListOf(points.first())
    for (i in 1 until points.size) {
      val previous = points[i - 1]
      val point = points[i]
      if (point.timestamp.time - previous.timestamp.time >= gapMillis) {
        segments.add(current)
        current = mutableListOf(point)
      } else {
        current.add(point)
      }
    }
    segments.add(current)
    return segments
  }

  /** 途切れが 1 箇所でもあるか（欠落を含む経路かどうかの表示に使う）。 */
  fun hasGap(points: List<GpsPoint>, gapMillis: Long = GAP_MILLIS): Boolean = split(points, gapMillis).size > 1
}
