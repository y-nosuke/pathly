package com.pathly.presentation.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.data.places.PlacesTextSearcher
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.Priority
import com.pathly.domain.repository.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlacesViewModel @Inject constructor(
  private val wishlistRepository: WishlistRepository,
  private val placesTextSearcher: PlacesTextSearcher,
) : ViewModel() {

  private val _uiState = MutableStateFlow(PlacesState())
  val uiState: StateFlow<PlacesState> = _uiState.asStateFlow()

  init {
    observePlaces()
  }

  private fun observePlaces() {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true)
      try {
        wishlistRepository.getPlaces().collect { items ->
          _uiState.value = _uiState.value.copy(
            items = items,
            isLoading = false,
            errorMessage = null,
          )
        }
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(
          isLoading = false,
          errorMessage = "場所の読み込みに失敗しました: ${e.message}",
        )
      }
    }
  }

  fun setFilter(filter: PlacesFilter) {
    _uiState.value = _uiState.value.copy(filter = filter)
  }

  /** その場所を含むお出掛け（経路）の一覧。詳細画面で購読する。 */
  fun visitsFor(placeId: Long): Flow<List<PlaceVisit>> = wishlistRepository.getVisits(placeId)

  /** 地図タップからの登録。行きたいONのときだけ wishlist にも入れる。 */
  fun registerPlace(
    latitude: Double,
    longitude: Double,
    name: String?,
    wishlist: Boolean,
    priority: Priority,
    memo: String?,
  ) {
    viewModelScope.launch {
      try {
        val placeId = wishlistRepository.registerPlace(latitude, longitude, name)
        if (wishlist) {
          wishlistRepository.addToWishlist(placeId, priority, memo)
        }
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = "登録に失敗しました: ${e.message}")
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
          wishlistRepository.addToWishlist(item.place.id, Priority.MEDIUM, null)
        }
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = "更新に失敗しました: ${e.message}")
      }
    }
  }

  /** 場所の名前を手動で編集する（空なら未命名に戻す）。 */
  fun renamePlace(placeId: Long, name: String) {
    viewModelScope.launch {
      try {
        wishlistRepository.renamePlace(placeId, name)
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = "名前の変更に失敗しました: ${e.message}")
      }
    }
  }

  fun updateWishlist(id: Long, priority: Priority, memo: String?) {
    viewModelScope.launch {
      try {
        wishlistRepository.updateWishlist(id, priority, memo)
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = "更新に失敗しました: ${e.message}")
      }
    }
  }

  fun setVisited(id: Long, visited: Boolean) {
    viewModelScope.launch {
      try {
        wishlistRepository.setVisited(id, visited)
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = "更新に失敗しました: ${e.message}")
      }
    }
  }

  // ---- キーワード検索（追加＞検索して追加） ----

  private var predictJob: Job? = null

  /** 検索画面を開いたとき。セッション開始＋状態リセット。 */
  fun startSearch() {
    placesTextSearcher.startSession()
    _uiState.value = _uiState.value.copy(search = SearchState())
  }

  fun onSearchQueryChange(query: String) {
    _uiState.value = _uiState.value.copy(search = _uiState.value.search.copy(query = query))
    predictJob?.cancel()
    if (query.isBlank()) {
      _uiState.value = _uiState.value.copy(search = _uiState.value.search.copy(predictions = emptyList(), isSearching = false))
      return
    }
    predictJob = viewModelScope.launch {
      delay(300) // デバウンス（打鍵ごとに叩かない）
      _uiState.value = _uiState.value.copy(search = _uiState.value.search.copy(isSearching = true))
      val preds = placesTextSearcher.predict(query)
      _uiState.value = _uiState.value.copy(
        search = _uiState.value.search.copy(predictions = preds, isSearching = false),
      )
    }
  }

  /** 候補を選んで確定（fetchPlace）。取得できたら入力フォーム用に result を保持する。 */
  fun selectPrediction(placeId: String) {
    viewModelScope.launch {
      val result = placesTextSearcher.fetch(placeId)
      if (result == null) {
        _uiState.value = _uiState.value.copy(errorMessage = "場所の取得に失敗しました（オフライン等）")
      } else {
        _uiState.value = _uiState.value.copy(search = _uiState.value.search.copy(result = result))
      }
    }
  }

  /** 検索結果を登録する。行きたい ON なら wishlist にも入れる。 */
  fun registerSearchResult(result: PlaceSearchResult, wishlist: Boolean, priority: Priority, memo: String?) {
    viewModelScope.launch {
      try {
        val placeId = wishlistRepository.registerSearchedPlace(result)
        if (wishlist) {
          wishlistRepository.addToWishlist(placeId, priority, memo)
        }
        _uiState.value = _uiState.value.copy(search = SearchState())
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = "登録に失敗しました: ${e.message}")
      }
    }
  }

  /** 確定フォームから戻る（候補選び直し）。 */
  fun clearSearchResult() {
    _uiState.value = _uiState.value.copy(search = _uiState.value.search.copy(result = null))
  }

  /** 場所そのものを削除する（行きたい・立ち寄り記録も一緒に消える）。 */
  fun deletePlace(placeId: Long) {
    viewModelScope.launch {
      try {
        wishlistRepository.deletePlace(placeId)
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = "削除に失敗しました: ${e.message}")
      }
    }
  }

  fun clearError() {
    _uiState.value = _uiState.value.copy(errorMessage = null)
  }
}
