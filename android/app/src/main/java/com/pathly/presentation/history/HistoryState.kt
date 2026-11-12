package com.pathly.presentation.history

import androidx.lifecycle.SavedStateHandle
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.PlaceRegion
import com.pathly.domain.model.RegionCount
import com.pathly.domain.model.RegionKey
import com.pathly.domain.model.categoryFacetCounts
import com.pathly.domain.model.matchesAnyOf
import com.pathly.domain.model.regionCounts
import com.pathly.presentation.common.getEnum
import com.pathly.presentation.common.getEnumSet
import com.pathly.presentation.common.getRegionSet
import com.pathly.presentation.common.matchesNameQuery
import com.pathly.presentation.common.putEnum
import com.pathly.presentation.common.putEnumSet
import com.pathly.presentation.common.putRegionSet
import com.pathly.presentation.tracking.OverlayTrack
import com.pathly.presentation.tracking.TrackFocusRequest
import com.pathly.util.DateFormatters
import java.text.Collator
import java.util.Locale

/** お気に入りの絞り込み（1軸・3状態）。命名・立ち寄り絞り込みとは独立。 */
enum class TrackFavoriteFilter(val chipLabel: String) {
  ANY("お気に入り"),
  FAVORITE("お気に入り"),
  NOT_FAVORITE("お気に入り以外"),
}

/** 命名状況の絞り込み（1軸・3状態）。 */
enum class TrackNamedFilter(val chipLabel: String) {
  ANY("名前"),
  NAMED("名前あり"),
  UNNAMED("未命名"),
}

/** 立ち寄りの有無の絞り込み（1軸・3状態）。 */
enum class TrackStopFilter(val chipLabel: String) {
  ANY("立ち寄り"),
  HAS_STOPS("立ち寄りあり"),
  NO_STOPS("立ち寄りなし"),
}

/** 一覧の並べ替え軸。既定の向き（降順=大きい/新しいが先）も持つ。 */
enum class TrackSort(val label: String, val defaultDescending: Boolean) {
  DATE("日付順", true),
  STOP_COUNT("立ち寄り数順", true),
  DISTANCE("距離順", true),
  DURATION("時間順", true),
  NAME("名前順", false),
}

/** 履歴の見せ方（一覧か地図か）。 */
enum class HistoryViewMode { LIST, MAP }

data class HistoryState(
  val tracks: List<GpsTrack> = emptyList(),
  val activeTrack: GpsTrack? = null,
  // 絞り込みは5軸独立（お気に入り / 命名 / 立ち寄り / 業種 / 地域）。業種・地域以外はそれぞれ3状態。
  val favoriteFilter: TrackFavoriteFilter = TrackFavoriteFilter.ANY,
  val namedFilter: TrackNamedFilter = TrackNamedFilter.ANY,
  val stopFilter: TrackStopFilter = TrackStopFilter.ANY,
  // 立ち寄った場所の業種。複数選べて、選んだどれかに立ち寄っていれば残す（OR）。空＝指定なし。
  val categoryFilter: Set<PlaceCategoryFacet> = emptySet(),
  // 立ち寄った場所の地域。複数選べて、選んだどれかに立ち寄っていれば残す（OR）。空＝指定なし。
  val regionFilter: Set<RegionKey> = emptySet(),
  // 名前の検索語（経路の名前、未命名なら一覧の見出しの日付に含むものだけ残す）。空＝指定なし。ほかの絞り込みとは AND。
  val nameQuery: String = "",
  val sort: TrackSort = TrackSort.DATE,
  val sortDescending: Boolean = TrackSort.DATE.defaultDescending,
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  // 一覧で見るか地図で見るか。絞り込みは両方で共有する。
  val viewMode: HistoryViewMode = HistoryViewMode.LIST,
  // 地図表示で、地図に重ねた見出し・検索欄・絞り込みをたたんでいるか。
  val controlsCollapsed: Boolean = false,
  // 地図表示で選んで重ねている経路（選んだ順）。絞り込みから外れても外すまで残す。
  val selected: List<OverlayTrack> = emptyList(),
  // 地図を寄せる対象（線を読み終えたら画面が寄せて、[HistoryViewModel.consumeFocus] で消す）。
  val focusRequest: TrackFocusRequest? = null,
) {
  /** 絞り込み・並べ替え（軸と向き）が、開いたときのまま（初期状態）か。違えば「初期状態に戻す」を出す。 */
  val isInitialListOptions: Boolean
    get() = noFilter && sort == HistoryState().sort && sortDescending == HistoryState().sortDescending

  /** 絞り込み（名前の検索を含む）を一切かけていないか。 */
  val noFilter: Boolean
    get() = favoriteFilter == TrackFavoriteFilter.ANY &&
      namedFilter == TrackNamedFilter.ANY &&
      stopFilter == TrackStopFilter.ANY &&
      categoryFilter.isEmpty() &&
      regionFilter.isEmpty() &&
      nameQuery.isBlank()

  /** かけている絞り込みの数（名前の検索・お気に入り・命名・立ち寄り・業種・地域）。たたんだ帯に出す。 */
  val activeFilterCount: Int
    get() = listOf(
      nameQuery.isNotBlank(),
      favoriteFilter != TrackFavoriteFilter.ANY,
      namedFilter != TrackNamedFilter.ANY,
      stopFilter != TrackStopFilter.ANY,
      categoryFilter.isNotEmpty(),
      regionFilter.isNotEmpty(),
    ).count { it }

  /**
   * 業種ごとの、その業種に立ち寄った経路の件数（絞り込みの選択肢に添える）。業種以外の絞り込みに合う経路で数え、
   * 全体には立ち寄りがあるのに絞り込みで 0 件になった業種は 0 で残る（→ [categoryFacetCounts]）。
   */
  val categoryCounts: Map<PlaceCategoryFacet, Int>
    get() = categoryFacetCounts(tracks, { it.stopCategories }) { matchesBase(it) && regionOk(it) }

  /** 地域ごとの、そこに立ち寄った経路の件数。地域以外の絞り込みに合う経路で数える（→ [regionCounts]）。 */
  val regionCounts: List<RegionCount>
    get() = regionCounts(tracks, ::regionsOf) { matchesBase(it) && categoryOk(it) }

  /** 現在の絞り込み・並べ替えを適用した経路一覧。 */
  val visibleTracks: List<GpsTrack>
    get() {
      val filtered = tracks.filter { matchesBase(it) && categoryOk(it) && regionOk(it) }

      // 各軸は昇順の比較器で定義し、降順ならまとめて反転する。
      // 元の tracks は開始が新しい順なので、同値のときはその並び（＝安定ソート）を保つ。
      val ascending: Comparator<GpsTrack> = when (sort) {
        TrackSort.DATE -> compareBy { it.startTime }

        TrackSort.STOP_COUNT -> compareBy { it.stopCount }

        TrackSort.DISTANCE -> compareBy { it.totalDistanceMeters }

        TrackSort.DURATION -> compareBy { durationMillisOf(it) }

        TrackSort.NAME -> {
          val collator = Collator.getInstance(Locale.JAPANESE)
          // 未命名は名前が無いので昇順で先頭・降順で末尾（null 相当）。
          compareBy(nullsFirst(collator)) { it.name?.takeIf { n -> n.isNotBlank() } }
        }
      }
      return filtered.sortedWith(if (sortDescending) ascending.reversed() else ascending)
    }

  private fun categoryOk(track: GpsTrack): Boolean = categoryFilter.isEmpty() || track.stopCategories.any { it in categoryFilter }

  private fun regionOk(track: GpsTrack): Boolean = regionFilter.matchesAnyOf(regionsOf(track))

  /** 業種・地域以外の絞り込み（お気に入り・命名・立ち寄り・名前の検索）に合うか。 */
  private fun matchesBase(track: GpsTrack): Boolean {
    val favoriteOk = when (favoriteFilter) {
      TrackFavoriteFilter.ANY -> true
      TrackFavoriteFilter.FAVORITE -> track.isFavorite
      TrackFavoriteFilter.NOT_FAVORITE -> !track.isFavorite
    }
    val namedOk = when (namedFilter) {
      TrackNamedFilter.ANY -> true
      TrackNamedFilter.NAMED -> track.hasName
      TrackNamedFilter.UNNAMED -> !track.hasName
    }
    val stopOk = when (stopFilter) {
      TrackStopFilter.ANY -> true
      TrackStopFilter.HAS_STOPS -> track.stopCount > 0
      TrackStopFilter.NO_STOPS -> track.stopCount == 0
    }
    val nameOk = matchesNameQuery(listTitleOf(track), nameQuery)
    return favoriteOk && namedOk && stopOk && nameOk
  }
}

/** 経路の地域（立ち寄った場所の地域）。立ち寄りの無い経路は場所が分からないので地域不明に入れる。 */
private fun regionsOf(track: GpsTrack): Set<PlaceRegion> = track.stopRegions.ifEmpty { setOf(PlaceRegion.UNKNOWN) }

/** 一覧のカードの見出し（名前。未命名なら日付）。名前の検索はこれに当てる。 */
internal fun listTitleOf(track: GpsTrack): String = track.name?.takeIf { track.hasName } ?: DateFormatters.shortDate(track.startTime)

/** 経路の所要時間（ミリ秒）。終了時刻が無ければ 0（並べ替えの安定用）。 */
private fun durationMillisOf(track: GpsTrack): Long = track.endTime?.let { it.time - track.startTime.time }?.coerceAtLeast(0L) ?: 0L

private const val KEY_FAVORITE = "history.favoriteFilter"
private const val KEY_NAMED = "history.namedFilter"
private const val KEY_STOP = "history.stopFilter"
private const val KEY_CATEGORY = "history.categoryFilter"
private const val KEY_REGION = "history.regionFilter"
private const val KEY_NAME_QUERY = "history.nameQuery"
private const val KEY_SORT = "history.sort"
private const val KEY_SORT_DESCENDING = "history.sortDescending"
private const val KEY_VIEW_MODE = "history.viewMode"
private const val KEY_CONTROLS_COLLAPSED = "history.controlsCollapsed"

/** [handle] に保存した絞り込み・並べ替えを反映した状態。保存が無ければ既定のまま。 */
fun HistoryState.withListOptionsFrom(handle: SavedStateHandle): HistoryState {
  val sort = handle.getEnum(KEY_SORT, sort)
  return copy(
    favoriteFilter = handle.getEnum(KEY_FAVORITE, favoriteFilter),
    namedFilter = handle.getEnum(KEY_NAMED, namedFilter),
    stopFilter = handle.getEnum(KEY_STOP, stopFilter),
    categoryFilter = handle.getEnumSet<PlaceCategoryFacet>(KEY_CATEGORY),
    regionFilter = handle.getRegionSet(KEY_REGION),
    nameQuery = handle.get<String>(KEY_NAME_QUERY).orEmpty(),
    sort = sort,
    sortDescending = handle.get<Boolean>(KEY_SORT_DESCENDING) ?: sort.defaultDescending,
    viewMode = handle.getEnum(KEY_VIEW_MODE, viewMode),
    controlsCollapsed = handle.get<Boolean>(KEY_CONTROLS_COLLAPSED) ?: controlsCollapsed,
  )
}

/** 絞り込み・並べ替えを [handle] に保存する（アプリが裏で落とされても戻せるように）。 */
fun HistoryState.saveListOptionsTo(handle: SavedStateHandle) {
  handle.putEnum(KEY_FAVORITE, favoriteFilter)
  handle.putEnum(KEY_NAMED, namedFilter)
  handle.putEnum(KEY_STOP, stopFilter)
  handle.putEnumSet(KEY_CATEGORY, categoryFilter)
  handle.putRegionSet(KEY_REGION, regionFilter)
  handle[KEY_NAME_QUERY] = nameQuery
  handle.putEnum(KEY_SORT, sort)
  handle[KEY_SORT_DESCENDING] = sortDescending
  handle.putEnum(KEY_VIEW_MODE, viewMode)
  handle[KEY_CONTROLS_COLLAPSED] = controlsCollapsed
}
