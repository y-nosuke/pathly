package com.pathly.domain.repository

import com.pathly.domain.model.GroupOpenState
import com.pathly.domain.model.LocationAccuracy
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.MapSurface
import com.pathly.domain.model.PlaceGrouping
import com.pathly.domain.model.StatsList
import com.pathly.domain.model.ThemeMode
import com.pathly.domain.model.TrackGrouping
import kotlinx.coroutines.flow.StateFlow

/**
 * アプリ設定。値は StateFlow で公開し、変えるとすぐに流れる。アプリを閉じても残る。
 */
interface SettingsRepository {
  /** GPS記録間隔（秒）。UI が購読する。 */
  val gpsIntervalSeconds: StateFlow<Int>

  /** 現在の GPS 記録間隔（秒）。サービスが起動時に参照する。 */
  fun currentGpsIntervalSeconds(): Int

  fun setGpsIntervalSeconds(seconds: Int)

  /** 位置の取り方（省電力／高精度）。UI が購読する。 */
  val locationAccuracy: StateFlow<LocationAccuracy>

  /** 現在の設定。サービスが記録開始時に参照する。 */
  fun currentLocationAccuracy(): LocationAccuracy

  fun setLocationAccuracy(accuracy: LocationAccuracy)

  /** 画面の明暗（端末の設定に従う／明るい／暗い）。既定は端末の設定に従う。 */
  val themeMode: StateFlow<ThemeMode>

  fun setThemeMode(mode: ThemeMode)

  /**
   * 画面の色に壁紙の色（ダイナミックカラー）を使うか。既定は使わず Pathly の色（→ adr/0040）。
   * 使っても地図に描くもの（経路・マーカー）の色は変わらない。
   */
  val useDynamicColor: StateFlow<Boolean>

  fun setUseDynamicColor(use: Boolean)

  /** その画面で「登録済みの場所」を表示するか。画面ごとに独立して覚える。UI が購読する。既定は OFF。 */
  fun showRegisteredPlaces(surface: MapSurface): StateFlow<Boolean>

  fun setShowRegisteredPlaces(surface: MapSurface, show: Boolean)

  /** その画面の地図の「登録済みの場所」の絞り込み。場所一覧の絞り込みとは別。UI が購読する。既定は絞り込みなし。 */
  fun mapPlaceFilter(surface: MapSurface): StateFlow<MapPlaceFilter>

  fun setMapPlaceFilter(surface: MapSurface, filter: MapPlaceFilter)

  /** 場所一覧の分け方。UI が購読する。既定は分けない。 */
  val placeGrouping: StateFlow<PlaceGrouping>

  fun setPlaceGrouping(grouping: PlaceGrouping)

  /** その分け方で開いているまとまりのキー。分け方ごとに覚える。UI が購読する。既定はすべて閉じる（空）。 */
  fun openPlaceGroups(grouping: PlaceGrouping): StateFlow<Set<String>>

  fun setOpenPlaceGroups(grouping: PlaceGrouping, keys: Set<String>)

  /** 履歴一覧の分け方。UI が購読する。既定は分けない。 */
  val historyGrouping: StateFlow<TrackGrouping>

  fun setHistoryGrouping(grouping: TrackGrouping)

  /** 履歴一覧で押して変えた開閉。UI が購読する。既定は何も押していない（空）。 */
  val historyGroupOpenState: StateFlow<GroupOpenState>

  fun setHistoryGroupOpenState(state: GroupOpenState)

  /** その一覧で統計のカードを広げているか。UI が購読する。既定は畳んだ帯。 */
  fun statsExpanded(list: StatsList): StateFlow<Boolean>

  fun setStatsExpanded(list: StatsList, expanded: Boolean)

  companion object {
    const val DEFAULT_GPS_INTERVAL_SECONDS = 10

    /** 選択できる間隔（秒）。 */
    val GPS_INTERVAL_OPTIONS = listOf(5, 10, 30, 60)
  }
}
