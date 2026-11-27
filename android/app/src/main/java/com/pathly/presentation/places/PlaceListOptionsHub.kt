package com.pathly.presentation.places

import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.RegionKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 場所の一覧の並べ替え・向き・絞り込み（場所タブと計画の「場所を選ぶ」で共有するもの）。 */
data class PlaceListOptions(
  val wishlistFilter: WishlistFilter,
  val visitedFilter: VisitedFilter,
  val categoryFilter: Set<PlaceCategoryFacet>,
  val regionFilter: Set<RegionKey>,
  val sort: PlaceSort,
  val sortDescending: Boolean,
)

/**
 * 場所の一覧の並べ替え・絞り込みを、場所タブと計画の「場所を選ぶ」で共有する（アプリを使っている間だけ → docs/specs/plans.md）。
 * どちらで変えても、もう一方の一覧に移る。分け方・開閉は設定（SettingsRepository）にあり、もともと共有している。
 * 名前の検索語と一覧／地図の切り替えは、画面ごとに持つ（共有しない）。
 */
@Singleton
class PlaceListOptionsHub @Inject constructor() {
  private val _options = MutableStateFlow<PlaceListOptions?>(null)

  /** 最後にどちらかの一覧で決めた並べ替え・絞り込み。まだ何も決めていなければ null。 */
  val options: StateFlow<PlaceListOptions?> = _options.asStateFlow()

  fun publish(options: PlaceListOptions) {
    _options.value = options
  }
}

internal val PlacesState.listOptions: PlaceListOptions
  get() = PlaceListOptions(wishlistFilter, visitedFilter, categoryFilter, regionFilter, sort, sortDescending)

internal fun PlacesState.withListOptions(options: PlaceListOptions): PlacesState = copy(
  wishlistFilter = options.wishlistFilter,
  visitedFilter = options.visitedFilter,
  categoryFilter = options.categoryFilter,
  regionFilter = options.regionFilter,
  sort = options.sort,
  sortDescending = options.sortDescending,
)
