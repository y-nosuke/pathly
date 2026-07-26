package com.pathly.presentation.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.Priority
import com.pathly.domain.repository.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlacesViewModel @Inject constructor(
  private val wishlistRepository: WishlistRepository,
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
