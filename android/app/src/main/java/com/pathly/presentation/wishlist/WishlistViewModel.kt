package com.pathly.presentation.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.Priority
import com.pathly.domain.repository.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WishlistViewModel @Inject constructor(
  private val wishlistRepository: WishlistRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(WishlistState())
  val uiState: StateFlow<WishlistState> = _uiState.asStateFlow()

  init {
    observeWishlist()
  }

  private fun observeWishlist() {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true)
      try {
        wishlistRepository.getWishlist().collect { items ->
          _uiState.value = _uiState.value.copy(
            items = items,
            isLoading = false,
            errorMessage = null,
          )
        }
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(
          isLoading = false,
          errorMessage = "行きたい場所の読み込みに失敗しました: ${e.message}",
        )
      }
    }
  }

  fun setFilter(filter: WishlistFilter) {
    _uiState.value = _uiState.value.copy(filter = filter)
  }

  fun addByCoordinate(
    latitude: Double,
    longitude: Double,
    name: String?,
    priority: Priority,
    memo: String?,
  ) {
    viewModelScope.launch {
      try {
        wishlistRepository.addByCoordinate(latitude, longitude, name, priority, memo)
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = "追加に失敗しました: ${e.message}")
      }
    }
  }

  fun updateItem(id: Long, priority: Priority, memo: String?) {
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

  fun remove(id: Long) {
    viewModelScope.launch {
      try {
        wishlistRepository.remove(id)
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(errorMessage = "削除に失敗しました: ${e.message}")
      }
    }
  }

  fun clearError() {
    _uiState.value = _uiState.value.copy(errorMessage = null)
  }
}
