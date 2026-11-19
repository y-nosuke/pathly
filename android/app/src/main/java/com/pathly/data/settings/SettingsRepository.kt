package com.pathly.data.settings

import android.content.Context
import androidx.core.content.edit
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.PlaceGrouping
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * アプリ設定の保存・取得（SharedPreferences）。
 */
@Singleton
class SettingsRepository @Inject constructor(
  @ApplicationContext context: Context,
) {
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _gpsIntervalSeconds =
    MutableStateFlow(prefs.getInt(KEY_GPS_INTERVAL, DEFAULT_GPS_INTERVAL_SECONDS))

  /** GPS記録間隔（秒）。UI が購読する。 */
  val gpsIntervalSeconds: StateFlow<Int> = _gpsIntervalSeconds.asStateFlow()

  /** 現在の GPS 記録間隔（秒）。サービスが起動時に参照する。 */
  fun currentGpsIntervalSeconds(): Int = _gpsIntervalSeconds.value

  fun setGpsIntervalSeconds(seconds: Int) {
    prefs.edit { putInt(KEY_GPS_INTERVAL, seconds) }
    _gpsIntervalSeconds.value = seconds
  }

  private val _locationAccuracy =
    MutableStateFlow(LocationAccuracy.fromKey(prefs.getString(KEY_LOCATION_ACCURACY, null)))

  /** 位置の取り方（省電力／高精度）。UI が購読する。 */
  val locationAccuracy: StateFlow<LocationAccuracy> = _locationAccuracy.asStateFlow()

  /** 現在の設定。サービスが記録開始時に参照する。 */
  fun currentLocationAccuracy(): LocationAccuracy = _locationAccuracy.value

  fun setLocationAccuracy(accuracy: LocationAccuracy) {
    prefs.edit { putString(KEY_LOCATION_ACCURACY, accuracy.key) }
    _locationAccuracy.value = accuracy
  }

  private val _themeMode = MutableStateFlow(ThemeMode.fromKey(prefs.getString(KEY_THEME_MODE, null)))

  /** 画面の明暗（端末の設定に従う／明るい／暗い）。既定は端末の設定に従う。 */
  val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

  fun setThemeMode(mode: ThemeMode) {
    prefs.edit { putString(KEY_THEME_MODE, mode.key) }
    _themeMode.value = mode
  }

  private val _useDynamicColor = MutableStateFlow(prefs.getBoolean(KEY_USE_DYNAMIC_COLOR, false))

  /**
   * 画面の色に壁紙の色（ダイナミックカラー）を使うか。既定は使わず Pathly の色（→ adr/0040）。
   * 使っても地図に描くもの（経路・マーカー）の色は変わらない。
   */
  val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()

  fun setUseDynamicColor(use: Boolean) {
    prefs.edit { putBoolean(KEY_USE_DYNAMIC_COLOR, use) }
    _useDynamicColor.value = use
  }

  // 「登録済みの場所」を地図に出すトグル。画面（記録／履歴詳細／場所詳細／地図で選ぶ／履歴の地図）ごとに独立して保持する。
  private val showRegisteredPlacesFlows: Map<MapSurface, MutableStateFlow<Boolean>> =
    MapSurface.entries.associateWith { surface ->
      MutableStateFlow(prefs.getBoolean(keyShowPlaces(surface), false))
    }

  /** その画面で「登録済みの場所」を表示するか。UI が購読する。既定は OFF。 */
  fun showRegisteredPlaces(surface: MapSurface): StateFlow<Boolean> = showRegisteredPlacesFlows.getValue(surface).asStateFlow()

  fun setShowRegisteredPlaces(surface: MapSurface, show: Boolean) {
    prefs.edit { putBoolean(keyShowPlaces(surface), show) }
    showRegisteredPlacesFlows.getValue(surface).value = show
  }

  // 地図に出す「登録済みの場所」の絞り込み。表示のトグルと同じく画面（記録／履歴詳細／場所詳細／地図で選ぶ）ごとに
  // 独立して持ち、場所一覧の絞り込みとも別。アプリを閉じても残す。
  private val mapPlaceFilterFlows: Map<MapSurface, MutableStateFlow<MapPlaceFilter>> =
    MapSurface.entries.associateWith { surface -> MutableStateFlow(readMapPlaceFilter(surface)) }

  /** その画面の地図の「登録済みの場所」の絞り込み。UI が購読する。既定は絞り込みなし。 */
  fun mapPlaceFilter(surface: MapSurface): StateFlow<MapPlaceFilter> = mapPlaceFilterFlows.getValue(surface).asStateFlow()

  fun setMapPlaceFilter(surface: MapSurface, filter: MapPlaceFilter) {
    prefs.edit {
      putString(keyMapFilterWishlisted(surface), filter.wishlisted.toTriStateKey())
      putString(keyMapFilterVisited(surface), filter.visited.toTriStateKey())
      // 書き出し（BackupArchive）が StringSet を扱わないので、名前をカンマでつないだ文字列で持つ。
      putString(keyMapFilterCategories(surface), filter.categories.joinToString(",") { it.name })
    }
    mapPlaceFilterFlows.getValue(surface).value = filter
  }

  // 場所一覧の分け方。アプリを閉じても残す。知らない値は分けない扱い。
  private val _placeGrouping = MutableStateFlow(
    PlaceGrouping.entries.firstOrNull { it.name == prefs.getString(KEY_PLACE_GROUPING, null) } ?: PlaceGrouping.NONE,
  )

  /** 場所一覧の分け方。UI が購読する。既定は分けない。 */
  val placeGrouping: StateFlow<PlaceGrouping> = _placeGrouping.asStateFlow()

  fun setPlaceGrouping(grouping: PlaceGrouping) {
    prefs.edit { putString(KEY_PLACE_GROUPING, grouping.name) }
    _placeGrouping.value = grouping
  }

  // 場所一覧で開いているまとまり。分け方ごとに覚える（地域と業種を切り替えても、それぞれの開閉が残る）。
  private val openPlaceGroupsFlows: Map<PlaceGrouping, MutableStateFlow<Set<String>>> =
    PlaceGrouping.entries.associateWith { grouping ->
      MutableStateFlow(
        prefs.getString(keyOpenPlaceGroups(grouping), null).orEmpty().split(GROUP_KEY_SEPARATOR).filter { it.isNotEmpty() }.toSet(),
      )
    }

  /** その分け方で開いているまとまりのキー。UI が購読する。既定はすべて閉じる（空）。 */
  fun openPlaceGroups(grouping: PlaceGrouping): StateFlow<Set<String>> = openPlaceGroupsFlows.getValue(grouping).asStateFlow()

  fun setOpenPlaceGroups(grouping: PlaceGrouping, keys: Set<String>) {
    // 書き出し（BackupArchive）が StringSet を扱わないので、改行でつないだ文字列で持つ（地域の名前にカンマが入り得る）。
    prefs.edit { putString(keyOpenPlaceGroups(grouping), keys.sorted().joinToString(GROUP_KEY_SEPARATOR)) }
    openPlaceGroupsFlows.getValue(grouping).value = keys
  }

  // 知らない値（版が変わって名前が無くなった業種など）は読み飛ばし、指定なしとして扱う。
  private fun readMapPlaceFilter(surface: MapSurface): MapPlaceFilter {
    val categoryNames = prefs.getString(keyMapFilterCategories(surface), null).orEmpty().split(",").toSet()
    return MapPlaceFilter(
      wishlisted = prefs.getString(keyMapFilterWishlisted(surface), null).fromTriStateKey(),
      visited = prefs.getString(keyMapFilterVisited(surface), null).fromTriStateKey(),
      categories = PlaceCategoryFacet.entries.filter { it.name in categoryNames }.toSet(),
    )
  }

  companion object {
    const val DEFAULT_GPS_INTERVAL_SECONDS = 10

    /** 選択できる間隔（秒）。 */
    val GPS_INTERVAL_OPTIONS = listOf(5, 10, 30, 60)

    /** 書き出し（data/backup）にも含めるため公開する。 */
    const val PREFS_NAME = "pathly_settings"
    private const val KEY_GPS_INTERVAL = "gps_interval_seconds"
    private const val KEY_LOCATION_ACCURACY = "location_accuracy"
    private const val KEY_PLACE_GROUPING = "places_list_grouping"
    private const val KEY_USE_DYNAMIC_COLOR = "use_dynamic_color"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val GROUP_KEY_SEPARATOR = "\n"

    private fun keyOpenPlaceGroups(grouping: PlaceGrouping): String = "places_list_open_groups_${grouping.name.lowercase()}"

    private fun keyShowPlaces(surface: MapSurface): String = "show_registered_places_${surface.key}"

    private fun keyMapFilterWishlisted(surface: MapSurface): String = "map_place_filter_wishlisted_${surface.key}"
    private fun keyMapFilterVisited(surface: MapSurface): String = "map_place_filter_visited_${surface.key}"
    private fun keyMapFilterCategories(surface: MapSurface): String = "map_place_filter_categories_${surface.key}"

    private fun Boolean?.toTriStateKey(): String = when (this) {
      null -> "any"
      true -> "yes"
      false -> "no"
    }

    private fun String?.fromTriStateKey(): Boolean? = when (this) {
      "yes" -> true
      "no" -> false
      else -> null
    }
  }
}

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

/** 「登録済みの場所」トグルを保持する画面（それぞれ独立して ON/OFF を記憶する）。 */
enum class MapSurface(val key: String) {
  RECORDING("recording"),
  HISTORY("history"),
  PLACE_DETAIL("place_detail"),
  PLACE_ADD("place_add"),
  HISTORY_MAP("history_map"),
}
