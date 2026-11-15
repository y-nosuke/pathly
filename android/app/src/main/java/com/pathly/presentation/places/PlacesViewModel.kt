package com.pathly.presentation.places

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.data.places.PlacesTextSearcher
import com.pathly.data.settings.MapSurface
import com.pathly.data.settings.SettingsRepository
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.NearbyRegisterPrompt
import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.PlaceGrouping
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.PlaceWithDistance
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegionKey
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.SameFacilityPlace
import com.pathly.domain.repository.WishlistRepository
import com.pathly.domain.usecase.PlaceEditUseCase
import com.pathly.presentation.common.toggled
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlacesViewModel @Inject constructor(
  private val wishlistRepository: WishlistRepository,
  private val placesTextSearcher: PlacesTextSearcher,
  private val settingsRepository: SettingsRepository,
  private val placeEditUseCase: PlaceEditUseCase,
  private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

  // 絞り込み・並べ替えはアプリが裏で落とされても戻せるよう savedStateHandle から始める。
  // 分け方・開閉は設定から（開いた直下から分けた見た目で出す）。
  private val _uiState = MutableStateFlow(
    settingsRepository.placeGrouping.value.let { grouping ->
      PlacesState(grouping = grouping, openGroups = settingsRepository.openPlaceGroups(grouping).value)
    }.withListOptionsFrom(savedStateHandle),
  )
  val uiState: StateFlow<PlacesState> = _uiState.asStateFlow()

  init {
    observePlaces()
    observeShowRegisteredPlaces()
    observeGrouping()
  }

  /** 一覧の分け方と、その分け方で開いているまとまり（設定に保存・アプリを閉じても残る）。 */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun observeGrouping() {
    viewModelScope.launch {
      settingsRepository.placeGrouping
        .flatMapLatest { grouping -> settingsRepository.openPlaceGroups(grouping).map { grouping to it } }
        .collect { (grouping, open) -> _uiState.update { it.copy(grouping = grouping, openGroups = open) } }
    }
  }

  /** 一覧の分け方（分けない／地域／業種）を変える。開閉は分け方ごとに覚えているものに切り替わる。 */
  fun setGrouping(grouping: PlaceGrouping) {
    settingsRepository.setPlaceGrouping(grouping)
  }

  /** まとまりの見出しを押して開く／閉じる（いまの分け方の開閉として覚える）。 */
  fun toggleGroup(key: String) {
    val grouping = _uiState.value.grouping
    if (grouping == PlaceGrouping.NONE) return
    settingsRepository.setOpenPlaceGroups(grouping, settingsRepository.openPlaceGroups(grouping).value.toggled(key))
  }

  /** 場所詳細の地図に「登録済みの場所」を出すトグル（画面別・永続化）。 */
  private fun observeShowRegisteredPlaces() {
    viewModelScope.launch {
      settingsRepository.showRegisteredPlaces(MapSurface.PLACE_DETAIL).collect { show ->
        _uiState.update { it.copy(showRegisteredPlaces = show) }
      }
    }
  }

  fun toggleShowRegisteredPlaces() {
    settingsRepository.setShowRegisteredPlaces(MapSurface.PLACE_DETAIL, !_uiState.value.showRegisteredPlaces)
  }

  /** この画面の地図の「登録済みの場所」の絞り込み（地図ごとに独立。場所一覧とも別）。 */
  val mapPlaceFilter: StateFlow<MapPlaceFilter> = settingsRepository.mapPlaceFilter(MapSurface.PLACE_DETAIL)

  fun setMapPlaceFilter(filter: MapPlaceFilter) {
    settingsRepository.setMapPlaceFilter(MapSurface.PLACE_DETAIL, filter)
  }

  /**
   * 場所の登録・編集・削除・まとめる（記録画面・経路詳細と共有）。結果の一言は一覧側のスナックバーに出す。
   * 編集の保存では出さない（場所詳細を閉じて一覧に戻ったときに「保存しました」が出ても何のことか分からないため）。
   */
  val placeEdit = PlaceEditActions(
    scope = viewModelScope,
    placeEditUseCase = placeEditUseCase,
    wishlistRepository = wishlistRepository,
    placesTextSearcher = placesTextSearcher,
    // 近接確認で「見えている」とみなす場所（表示 ON で、地図の絞り込みに合うか、[mainPlaceId]＝場所詳細の
    // 主マーカーとして出ている）。
    shownOnMap = { mainPlaceId ->
      val show = _uiState.value.showRegisteredPlaces
      val filter = mapPlaceFilter.value
      ({ place -> show && (place.placeId == mainPlaceId || filter.matches(place)) })
    },
    placeNameOf = { placeId -> _uiState.value.items.firstOrNull { it.place.id == placeId }?.displayName },
    onMessage = ::notify,
    onError = { message -> _uiState.update { it.copy(errorMessage = message) } },
    notifySaved = false,
  )

  private fun observePlaces() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      try {
        wishlistRepository.getPlaces().collect { items ->
          _uiState.update {
            it.copy(
              items = items,
              isLoading = false,
              errorMessage = null,
            )
          }
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isLoading = false,
            errorMessage = "場所の読み込みに失敗しました: ${e.message}",
          )
        }
      }
    }
  }

  /** 行きたいの絞り込み（指定なし/行きたい/行きたい以外）。 */
  fun setWishlistFilter(filter: WishlistFilter) {
    updateListOptions { it.copy(wishlistFilter = filter) }
  }

  /** 訪問状況の絞り込み（指定なし/訪問済み/未訪問）。 */
  fun setVisitedFilter(filter: VisitedFilter) {
    updateListOptions { it.copy(visitedFilter = filter) }
  }

  /** 業種の絞り込みで、その業種を選ぶ／外す（複数選べる）。 */
  fun toggleCategoryFilter(facet: PlaceCategoryFacet) {
    updateListOptions { it.copy(categoryFilter = it.categoryFilter.toggled(facet)) }
  }

  /** 業種の絞り込みだけをまとめて外す（ほかの軸はそのまま）。 */
  fun clearCategoryFilter() {
    updateListOptions { it.copy(categoryFilter = emptySet()) }
  }

  /** 地域の絞り込みで、その都道府県・市区町村を選ぶ／外す（複数選べる）。 */
  fun toggleRegionFilter(region: RegionKey) {
    updateListOptions { it.copy(regionFilter = it.regionFilter.toggled(region)) }
  }

  /** 地域の絞り込みだけをまとめて外す（ほかの軸はそのまま）。 */
  fun clearRegionFilter() {
    updateListOptions { it.copy(regionFilter = emptySet()) }
  }

  /** 名前の検索語を変える（入力のたびに一覧を絞る）。 */
  fun setNameQuery(query: String) {
    updateListOptions { it.copy(nameQuery = query) }
  }

  /** 一覧表示と地図表示を切り替える（絞り込みはそのまま）。 */
  fun setViewMode(mode: PlacesViewMode) {
    updateListOptions { it.copy(viewMode = mode) }
  }

  /** 地図表示で、地図に重ねた見出し・検索欄・絞り込みをたたむ／ひろげる。 */
  fun setControlsCollapsed(collapsed: Boolean) {
    updateListOptions { it.copy(controlsCollapsed = collapsed) }
  }

  /**
   * 絞り込みの欄の軸を全解除する（行きたい・訪問状況・業種・地域をまとめて指定なしに戻す）。
   * 名前の検索語は検索欄の × で消すので残す。並べ替えも保持。
   */
  fun clearFilters() {
    updateListOptions {
      it.copy(
        wishlistFilter = WishlistFilter.ANY,
        visitedFilter = VisitedFilter.ANY,
        categoryFilter = emptySet(),
        regionFilter = emptySet(),
      )
    }
  }

  /** 絞り込み・名前の検索・並べ替え（軸と向き）をまとめて初期状態に戻す。一覧／地図の表示の切り替えはそのまま。 */
  fun resetListOptions() {
    val initial = PlacesState()
    updateListOptions {
      it.copy(
        wishlistFilter = initial.wishlistFilter,
        visitedFilter = initial.visitedFilter,
        categoryFilter = initial.categoryFilter,
        regionFilter = initial.regionFilter,
        nameQuery = initial.nameQuery,
        sort = initial.sort,
        sortDescending = initial.sortDescending,
      )
    }
  }

  /** 並べ替え軸の変更。軸ごとの既定の向き（新しい/多い/高いが先）に合わせる。 */
  fun setSort(sort: PlaceSort) {
    updateListOptions { it.copy(sort = sort, sortDescending = sort.defaultDescending) }
  }

  /** 並べ替えの昇順/降順を反転する。 */
  fun toggleSortDirection() {
    updateListOptions { it.copy(sortDescending = !it.sortDescending) }
  }

  /** 統一の場所シートで既存 place を編集するため、単一 place の現在値を取得する。 */
  suspend fun loadPlace(placeId: Long): PlaceListItem? = wishlistRepository.getPlace(placeId)

  /** 一覧の行や詳細から「行きたい」を付け外しする。 */
  fun toggleWishlist(item: PlaceListItem) {
    viewModelScope.launch {
      try {
        placeEditUseCase.toggleWishlist(item.place.id)
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "更新に失敗しました: ${e.message}") }
      }
    }
  }

  /** 場所の名前を手動で編集する（空なら未命名に戻す）。 */
  fun renamePlace(placeId: Long, name: String) {
    viewModelScope.launch {
      try {
        wishlistRepository.renamePlace(placeId, name)
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "名前の変更に失敗しました: ${e.message}") }
      }
    }
  }

  /** 場所のメモ（places.note）を更新する（行きたい登録と独立）。 */
  fun updateNote(placeId: Long, note: String?) {
    viewModelScope.launch {
      try {
        wishlistRepository.updatePlaceNote(placeId, note)
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "更新に失敗しました: ${e.message}") }
      }
    }
  }

  /** 行きたいの優先度を更新する。 */
  fun updatePriority(wishlistId: Long, priority: Priority) {
    viewModelScope.launch {
      try {
        wishlistRepository.updateWishlist(wishlistId, priority)
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "更新に失敗しました: ${e.message}") }
      }
    }
  }

  fun setVisited(id: Long, visited: Boolean) {
    viewModelScope.launch {
      try {
        wishlistRepository.setVisited(id, visited)
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "更新に失敗しました: ${e.message}") }
      }
    }
  }

  // ---- キーワード検索（追加＞検索して追加） ----

  private var predictJob: Job? = null

  /** 検索画面を開いたとき。セッション開始＋状態リセット。 */
  fun startSearch() {
    placesTextSearcher.startSession()
    _uiState.update { it.copy(search = SearchState()) }
  }

  fun onSearchQueryChange(query: String) {
    _uiState.update { it.copy(search = it.search.copy(query = query)) }
    predictJob?.cancel()
    if (query.isBlank()) {
      _uiState.update { it.copy(search = it.search.copy(predictions = emptyList(), isSearching = false)) }
      return
    }
    predictJob = viewModelScope.launch {
      delay(300) // デバウンス（打鍵ごとに叩かない）
      _uiState.update { it.copy(search = it.search.copy(isSearching = true)) }
      val preds = placesTextSearcher.predict(query)
      _uiState.update {
        it.copy(
          search = it.search.copy(predictions = preds, isSearching = false),
        )
      }
    }
  }

  /** 候補を選んで確定（fetchPlace）。取得できたら入力フォーム用に result を保持する。 */
  fun selectPrediction(placeId: String) {
    viewModelScope.launch {
      val result = placesTextSearcher.fetch(placeId)
      if (result == null) {
        _uiState.update { it.copy(errorMessage = "場所の取得に失敗しました（オフライン等）") }
      } else {
        _uiState.update { it.copy(search = it.search.copy(result = result)) }
      }
    }
  }

  /**
   * 一覧側スナックバーに出すワンショット通知（登録・紐付けなど）。地図で追加・検索して追加の画面は、
   * このトークンが進んだのを合図に一覧へ戻る。
   */
  private fun notify(message: String) {
    _uiState.update {
      it.copy(
        registerToken = it.registerToken + 1,
        registerMessage = message,
      )
    }
  }

  /** 確定フォームから戻る（候補選び直し）。 */
  fun clearSearchResult() {
    _uiState.update { it.copy(search = it.search.copy(result = null)) }
  }

  fun clearError() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  /** 絞り込み・並べ替えを変えて、savedStateHandle にも残す。 */
  private fun updateListOptions(transform: (PlacesState) -> PlacesState) {
    _uiState.update(transform)
    _uiState.value.saveListOptionsTo(savedStateHandle)
  }
}
