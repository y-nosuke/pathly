package com.pathly.presentation.places

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.MapRegion
import com.pathly.domain.model.MapSurface
import com.pathly.domain.model.NearbyRegisterPrompt
import com.pathly.domain.model.PlaceCategoryFacet
import com.pathly.domain.model.PlaceGrouping
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.PlaceWithDistance
import com.pathly.domain.model.RegionKey
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.SameFacilityPlace
import com.pathly.domain.model.StatsList
import com.pathly.domain.repository.PlaceSearchRepository
import com.pathly.domain.repository.SettingsRepository
import com.pathly.domain.repository.WishlistRepository
import com.pathly.domain.usecase.PlaceEditUseCase
import com.pathly.presentation.common.ListMapMode
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
  private val placeSearchRepository: PlaceSearchRepository,
  private val settingsRepository: SettingsRepository,
  private val placeEditUseCase: PlaceEditUseCase,
  private val savedStateHandle: SavedStateHandle,
  private val listOptionsHub: PlaceListOptionsHub,
) : ViewModel() {

  // 絞り込み・並べ替えはアプリが裏で落とされても戻せるよう savedStateHandle から始める。
  // 分け方・開閉は設定から（開いた直下から分けた見た目で出す）。
  private val _uiState = MutableStateFlow(
    settingsRepository.placeGrouping.value.let { grouping ->
      PlacesState(grouping = grouping, openGroups = settingsRepository.openPlaceGroups(grouping).value)
    }.withListOptionsFrom(savedStateHandle).let { state ->
      // 並べ替え・絞り込みは、もう一方の一覧（場所タブ／計画の「場所を選ぶ」）で決めたものがあればそれから始める。
      listOptionsHub.options.value?.let { state.withListOptions(it) } ?: state
    },
  )
  val uiState: StateFlow<PlacesState> = _uiState.asStateFlow()

  init {
    // もう一方の一覧から引き継いだ並べ替え・絞り込みも、アプリが裏で落とされても戻せるように残す。
    _uiState.value.saveListOptionsTo(savedStateHandle)
    observePlaces()
    observeGrouping()
    observeSharedListOptions()
  }

  /** もう一方の一覧で並べ替え・絞り込みを変えたら、こちらにも移す（→ [PlaceListOptionsHub]）。 */
  private fun observeSharedListOptions() {
    viewModelScope.launch {
      listOptionsHub.options.collect { options ->
        if (options != null && options != _uiState.value.listOptions) {
          _uiState.update { it.withListOptions(options) }
          _uiState.value.saveListOptionsTo(savedStateHandle)
        }
      }
    }
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

  /** 統計の帯を統計のカードに広げているか（アプリを閉じても残す）。 */
  val statsExpanded: StateFlow<Boolean> = settingsRepository.statsExpanded(StatsList.PLACES)

  fun setStatsExpanded(expanded: Boolean) {
    settingsRepository.setStatsExpanded(StatsList.PLACES, expanded)
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

  /** その画面の地図に「登録済みの場所」を出すか（画面別・永続化）。 */
  fun showRegisteredPlaces(surface: MapSurface): StateFlow<Boolean> = settingsRepository.showRegisteredPlaces(surface)

  fun toggleShowRegisteredPlaces(surface: MapSurface) {
    settingsRepository.setShowRegisteredPlaces(surface, !settingsRepository.showRegisteredPlaces(surface).value)
  }

  /** その画面の地図の「登録済みの場所」の絞り込み（地図ごとに独立。場所一覧とも別）。 */
  fun mapPlaceFilter(surface: MapSurface): StateFlow<MapPlaceFilter> = settingsRepository.mapPlaceFilter(surface)

  fun setMapPlaceFilter(surface: MapSurface, filter: MapPlaceFilter) {
    settingsRepository.setMapPlaceFilter(surface, filter)
  }

  /** 場所の登録・編集・削除・まとめる（記録画面・経路詳細と共有）。結果の一言は今いる画面（一覧・詳細）の下に出す。 */
  val placeEdit = PlaceEditActions(
    scope = viewModelScope,
    placeEditUseCase = placeEditUseCase,
    wishlistRepository = wishlistRepository,
    placeSearchRepository = placeSearchRepository,
    // 近接確認で「見えている」とみなす場所。場所詳細の地図から登録するとき（[mainPlaceId] がある）だけ、
    // 表示 ON で地図の絞り込みに合うか、主マーカーとして出ている場所。地図で選んで追加・検索して追加は、
    // 登録済みのマーカーから紐付けられないので、いつも近接確認を出す（何も見えていない扱い）。
    shownOnMap = { mainPlaceId ->
      if (mainPlaceId == null) {
        { false }
      } else {
        val show = settingsRepository.showRegisteredPlaces(MapSurface.PLACE_DETAIL).value
        val filter = settingsRepository.mapPlaceFilter(MapSurface.PLACE_DETAIL).value
        ({ place -> show && (place.placeId == mainPlaceId || filter.matches(place)) })
      }
    },
    placeNameOf = { placeId -> _uiState.value.items.firstOrNull { it.place.id == placeId }?.displayName },
    onMessage = ::notify,
    onError = { message -> _uiState.update { it.copy(errorMessage = message) } },
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
  fun setViewMode(mode: ListMapMode) {
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

  /** 並べ替えの向きを決める（表示のメニューで選ぶ）。 */
  fun setSortDescending(descending: Boolean) {
    updateListOptions { it.copy(sortDescending = descending) }
  }

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

  /**
   * 地域の見出しから地図で見るときの、その地域（範囲と、境界を描く Place ID。Places で地名から引く）。[near] はその地域の場所が
   * 入る範囲で、同じ名前の地域を取り違えないよう候補をそこに寄せる。地域不明・オフライン・引けないときは null。
   */
  suspend fun findRegion(region: RegionKey, near: GeoBounds?): MapRegion? = region.searchQuery?.let {
    placeSearchRepository.findRegion(it, topLevel = region.city == null, near = near)
  }

  private var predictJob: Job? = null

  /** 検索画面を開いたとき。セッション開始＋状態リセット。 */
  fun startSearch() {
    placeSearchRepository.startSession()
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
      val preds = placeSearchRepository.predict(query)
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
      val result = placeSearchRepository.fetch(placeId)
      if (result == null) {
        _uiState.update { it.copy(errorMessage = "場所の取得に失敗しました（オフライン等）") }
      } else {
        _uiState.update { it.copy(search = it.search.copy(result = result)) }
      }
    }
  }

  /**
   * 下のスナックバーに出す一言（登録・紐付け・保存など）。地図で追加・検索して追加の画面は、
   * トークンが進んだのを合図に一覧へ戻る。
   */
  private fun notify(message: String) {
    _uiState.update {
      it.copy(
        registerToken = it.registerToken + 1,
        registerMessage = message,
      )
    }
  }

  /** 一言を出し終えた（一覧と詳細を行き来するたびに同じ一言を出さないよう消化する）。 */
  fun consumeRegisterMessage() {
    _uiState.update { it.copy(registerMessage = null) }
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
    listOptionsHub.publish(_uiState.value.listOptions)
  }
}
