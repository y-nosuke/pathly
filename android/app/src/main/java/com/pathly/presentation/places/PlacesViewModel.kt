package com.pathly.presentation.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.data.places.PlacesTextSearcher
import com.pathly.data.settings.MapSurface
import com.pathly.data.settings.SettingsRepository
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.repository.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlacesViewModel @Inject constructor(
  private val wishlistRepository: WishlistRepository,
  private val placesTextSearcher: PlacesTextSearcher,
  private val settingsRepository: SettingsRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(PlacesState())
  val uiState: StateFlow<PlacesState> = _uiState.asStateFlow()

  init {
    observePlaces()
    observeShowRegisteredPlaces()
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
    _uiState.update { it.copy(wishlistFilter = filter) }
  }

  /** 訪問状況の絞り込み（指定なし/訪問済み/未訪問）。 */
  fun setVisitedFilter(filter: VisitedFilter) {
    _uiState.update { it.copy(visitedFilter = filter) }
  }

  /** 絞り込みを全解除する（行きたい・訪問状況をまとめて指定なしに戻す）。並べ替えは保持。 */
  fun clearFilters() {
    _uiState.update {
      it.copy(
        wishlistFilter = WishlistFilter.ANY,
        visitedFilter = VisitedFilter.ANY,
      )
    }
  }

  /** 並べ替え軸の変更。軸ごとの既定の向き（新しい/多い/高いが先）に合わせる。 */
  fun setSort(sort: PlaceSort) {
    _uiState.update { it.copy(sort = sort, sortDescending = sort.defaultDescending) }
  }

  /** 並べ替えの昇順/降順を反転する。 */
  fun toggleSortDirection() {
    _uiState.update { it.copy(sortDescending = !it.sortDescending) }
  }

  /** その場所を含むお出掛け（経路）の一覧。詳細画面で購読する。 */
  fun visitsFor(placeId: Long): Flow<List<PlaceVisit>> = wishlistRepository.getVisits(placeId)

  /** 統一の場所シートで既存 place を編集するため、単一 place の現在値を取得する。 */
  suspend fun loadPlace(placeId: Long): PlaceListItem? = wishlistRepository.getPlace(placeId)

  /** POI 登録ダイアログのプレビュー用: placeId から施設情報（カテゴリ等）を取得する。 */
  suspend fun fetchPoiDetails(googlePlaceId: String): PlaceSearchResult? = wishlistRepository.fetchPlaceDetails(googlePlaceId)

  /** 手動追加の近接確認用: 近く（検出半径）に既存の場所があれば最寄り1件を返す。 */
  suspend fun nearbyPlace(latitude: Double, longitude: Double): RegisteredPlace? = wishlistRepository.findNearbyPlace(latitude, longitude)

  /** 近接確認で「この場所に紐付け」を選んだとき: 既存 place に行きたい/メモを反映する（新規は作らない）。 */
  fun linkRegisterToPlace(placeId: Long, wishlist: Boolean, priority: Priority, memo: String?) {
    viewModelScope.launch {
      try {
        if (!memo.isNullOrBlank()) wishlistRepository.updatePlaceNote(placeId, memo)
        if (wishlist) wishlistRepository.addToWishlist(placeId, priority)
        notify("この場所に紐付けました")
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "紐付けに失敗しました: ${e.message}") }
      }
    }
  }

  /** 地図タップからの登録。行きたいONのときだけ wishlist にも入れる。 */
  fun registerPlace(
    latitude: Double,
    longitude: Double,
    name: String?,
    wishlist: Boolean,
    priority: Priority,
    memo: String?,
    googlePlaceId: String? = null,
    forceNewPlace: Boolean = false,
  ) {
    viewModelScope.launch {
      try {
        val reg = wishlistRepository.registerPlace(latitude, longitude, name, memo, googlePlaceId, forceNewPlace)
        if (wishlist) {
          wishlistRepository.addToWishlist(reg.placeId, priority)
        }
        notifyRegistered(reg.alreadyExisted)
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "登録に失敗しました: ${e.message}") }
      }
    }
  }

  /** 一覧の行や詳細から「行きたい」を付け外しする。 */
  fun toggleWishlist(item: PlaceListItem) {
    viewModelScope.launch {
      try {
        val wishlistId = item.wishlistId
        if (wishlistId != null) {
          wishlistRepository.removeFromWishlist(wishlistId)
        } else {
          wishlistRepository.addToWishlist(item.place.id, Priority.MEDIUM)
        }
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

  /**
   * 詳細画面の編集を一括で保存する（名前・メモ・行きたい・優先度・訪問済みを差分適用）。
   * 「行きたい」を今つけた場合は addToWishlist の返す id で訪問済みも設定できるよう、
   * 1コルーチンで順に処理する（個別コールバックだと新規 wishlistId を掴めないため）。
   */
  fun savePlaceEdits(
    item: PlaceListItem,
    name: String,
    note: String,
    wishlist: Boolean,
    priority: Priority,
    visited: Boolean,
    link: PlaceSearchResult? = null,
  ) {
    viewModelScope.launch {
      try {
        // 「Googleで情報を取得」で選んだ施設があれば、保存時に紐付ける（google_places・座標を更新）。
        if (link != null) {
          wishlistRepository.linkPlaceToGoogle(item.place.id, link)
        }
        if (name.trim() != (item.place.name ?: "").trim()) {
          wishlistRepository.renamePlace(item.place.id, name.trim())
        }
        val newNote = note.ifBlank { null }
        if (newNote != item.note) {
          wishlistRepository.updatePlaceNote(item.place.id, newNote)
        }
        val wishlistId = item.wishlistId
        when {
          // 新たに「行きたい」へ。付けた直後の id で訪問済みも反映する。
          wishlist && wishlistId == null -> {
            val newId = wishlistRepository.addToWishlist(item.place.id, priority)
            if (visited && item.visitCount == 0) wishlistRepository.setVisited(newId, true)
          }
          // 既に「行きたい」。優先度・訪問済みの変更分だけ反映する。
          wishlist && wishlistId != null -> {
            if (priority != item.priority) wishlistRepository.updateWishlist(wishlistId, priority)
            if (item.visitCount == 0 && visited != item.isManuallyVisited) {
              wishlistRepository.setVisited(wishlistId, visited)
            }
          }
          // 「行きたい」を外す（場所自体は残す）。
          !wishlist && wishlistId != null -> {
            wishlistRepository.removeFromWishlist(wishlistId)
          }
        }
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "保存に失敗しました: ${e.message}") }
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
   * 検索結果を登録する。[name] が Google 由来の名前と違えばユーザー名として設定する。
   * 行きたい ON なら wishlist にも入れる。
   */
  fun registerSearchResult(
    result: PlaceSearchResult,
    name: String?,
    wishlist: Boolean,
    priority: Priority,
    memo: String?,
  ) {
    viewModelScope.launch {
      try {
        val reg = wishlistRepository.registerSearchedPlace(result)
        val placeId = reg.placeId
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isNotEmpty() && trimmed != result.name?.trim()) {
          wishlistRepository.renamePlace(placeId, trimmed)
        }
        if (!memo.isNullOrBlank()) {
          wishlistRepository.updatePlaceNote(placeId, memo)
        }
        if (wishlist) {
          wishlistRepository.addToWishlist(placeId, priority)
        }
        _uiState.update { it.copy(search = SearchState()) }
        notifyRegistered(reg.alreadyExisted)
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "登録に失敗しました: ${e.message}") }
      }
    }
  }

  /** 場所詳細の「Googleで情報を取得」で、登録座標の近くの POI 候補を取得する（選び直しと同じ仕組み）。 */
  suspend fun nearbyPois(latitude: Double, longitude: Double): List<PlaceSearchResult> = wishlistRepository.nearbyPois(latitude, longitude)

  /** 「Googleで情報を取得」の名前検索フォールバック: キーワード候補を返す（座標がずれて周辺に出ない施設用）。 */
  suspend fun predictPlaces(query: String): List<PlacePrediction> = placesTextSearcher.predict(query)

  /** 名前検索で選んだ候補を、座標つきの施設情報に確定する。 */
  suspend fun fetchPlaceResult(placeId: String): PlaceSearchResult? = placesTextSearcher.fetch(placeId)

  /** 登録結果を一覧側スナックバーに伝えるワンショット通知（削除と同じ下部表示に統一）。 */
  private fun notifyRegistered(alreadyExisted: Boolean) = notify(if (alreadyExisted) "この場所は登録済みです" else "登録しました")

  /** 一覧側スナックバーに出すワンショット通知。 */
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

  /**
   * 場所そのものを削除する（行きたい登録・解決ログも一緒に消える）。確認ダイアログは出さず
   * 即時削除し、一覧側のスナックバーから [undoDelete] で取り消せる（undoToken で通知）。
   */
  fun deletePlace(placeId: Long) {
    viewModelScope.launch {
      val name = _uiState.value.items.firstOrNull { it.place.id == placeId }?.displayName
      try {
        wishlistRepository.deletePlace(placeId)
        _uiState.update {
          it.copy(
            undoDeleteToken = it.undoDeleteToken + 1,
            undoDeleteName = name,
          )
        }
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "削除に失敗しました: ${e.message}") }
      }
    }
  }

  /** 直近の削除を取り消して元に戻す（スナックバーの「取り消す」）。 */
  fun undoDelete() {
    viewModelScope.launch {
      try {
        wishlistRepository.undoLastPlaceDeletion()
      } catch (e: Exception) {
        _uiState.update { it.copy(errorMessage = "取り消しに失敗しました: ${e.message}") }
      }
    }
  }

  fun clearError() {
    _uiState.update { it.copy(errorMessage = null) }
  }
}
