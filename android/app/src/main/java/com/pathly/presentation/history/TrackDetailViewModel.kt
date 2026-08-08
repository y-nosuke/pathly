package com.pathly.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.data.settings.MapSurface
import com.pathly.data.settings.SettingsRepository
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopCandidate
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.domain.repository.WishlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

/**
 * 詳細画面の立ち寄り表示・編集と、補正後軌跡の表示。
 * 開いても自動検出はしない。検出は記録中（自動）と「再解析」（追加提案・非破壊）、
 * 命名は加えて「場所を取得」ボタンで行う。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrackDetailViewModel @Inject constructor(
  private val placeRepository: PlaceRepository,
  private val gpsTrackRepository: GpsTrackRepository,
  private val wishlistRepository: WishlistRepository,
  private val settingsRepository: SettingsRepository,
) : ViewModel() {

  private val _stops = MutableStateFlow<List<Stop>>(emptyList())
  val stops: StateFlow<List<Stop>> = _stops.asStateFlow()

  /** 「登録済みの場所」を地図に出すか（履歴詳細の画面別トグル）。 */
  val showRegisteredPlaces: StateFlow<Boolean> =
    settingsRepository.showRegisteredPlaces(MapSurface.HISTORY)

  /** 地図に出す登録済みの場所（全 place）。トグルON時に描画する。 */
  val registeredPlaces: StateFlow<List<RegisteredPlace>> = placeRepository.observeRegisteredPlaces()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  fun toggleShowRegisteredPlaces() {
    settingsRepository.setShowRegisteredPlaces(MapSurface.HISTORY, !showRegisteredPlaces.value)
  }

  // 保存済みの補正後点列を反映したトラック。読み込み時に一度だけ設定する。
  private val _displayTrack = MutableStateFlow<GpsTrack?>(null)
  val displayTrack: StateFlow<GpsTrack?> = _displayTrack.asStateFlow()

  // 削除失敗などの一時メッセージ（表示したらクリアする）。
  private val _message = MutableStateFlow<String?>(null)
  val message: StateFlow<String?> = _message.asStateFlow()

  // 再解析の候補（一覧に無い立ち寄り＋表示名）。null=非表示、空リスト=「候補なし」を表示。
  private val _reanalyzeCandidates = MutableStateFlow<List<StopCandidate>?>(null)
  val reanalyzeCandidates: StateFlow<List<StopCandidate>?> = _reanalyzeCandidates.asStateFlow()

  private val loadedTrackId = MutableStateFlow<Long?>(null)

  /** 未取得（googlePlaceId 無し）の place 件数。「場所を取得」ボタンの表示に使う。 */
  val unresolvedCount: StateFlow<Int> = loadedTrackId
    .flatMapLatest { id -> if (id == null) flowOf(0) else placeRepository.unresolvedCountForTrack(id) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

  private var collectJob: Job? = null

  fun load(trackId: Long) {
    if (loadedTrackId.value == trackId) return
    loadedTrackId.value = trackId

    // 開いても検出はしない。保存済みの立ち寄りと補正後軌跡を表示するだけ。
    viewModelScope.launch { _displayTrack.value = gpsTrackRepository.getTrackById(trackId) }

    collectJob?.cancel()
    collectJob = viewModelScope.launch {
      placeRepository.getStopsForTrack(trackId).collect { _stops.value = it }
    }
  }

  fun updatePlaceName(placeId: Long, name: String) {
    viewModelScope.launch { placeRepository.updatePlaceName(placeId, name) }
  }

  /** 立ち寄り（訪問）のメモを更新する（stop 単位。空文字なら消す）。 */
  fun updateStopNote(stopId: Long, note: String?) {
    viewModelScope.launch { placeRepository.updateStopNote(stopId, note) }
  }

  /** 未取得の場所を Places で取り直す（手動・googlePlaceId 無しが対象）。 */
  fun resolveNames() {
    val trackId = loadedTrackId.value ?: return
    viewModelScope.launch { placeRepository.resolveUnresolvedNames(trackId) }
  }

  /**
   * 立ち寄り（訪問）を削除する（1件でも複数でも同じ）。参照が無くなった場所は自動で場所ごと削除し、
   * 他の履歴で使われている／行きたい登録がある場所は「この訪問だけ」消して保持する。
   * 確認ダイアログは出さず即時削除し、画面側のスナックバーから [undoDeletion] で取り消せる。
   */
  fun deleteStops(stopIds: List<Long>) {
    if (stopIds.isEmpty()) return
    viewModelScope.launch { placeRepository.deleteStops(stopIds) }
  }

  /** 直近の削除を取り消して元に戻す（スナックバーの「取り消す」）。 */
  fun undoDeletion() {
    viewModelScope.launch { placeRepository.undoLastDeletion() }
  }

  /**
   * 振り返り中の地図で POI をタップして場所を登録する。メモは常に保存し、行きたい ON なら
   * 優先度つきで wishlist にも入れる。
   */
  fun registerPlace(
    latitude: Double,
    longitude: Double,
    name: String?,
    wishlist: Boolean,
    priority: Priority,
    memo: String?,
    googlePlaceId: String? = null,
  ) {
    viewModelScope.launch {
      try {
        val reg = wishlistRepository.registerPlace(latitude, longitude, name, memo, googlePlaceId)
        if (wishlist) {
          wishlistRepository.addToWishlist(reg.placeId, priority)
        }
        _message.value = if (reg.alreadyExisted) {
          "この場所は登録済みです"
        } else {
          "「${name?.ifBlank { null } ?: "場所"}」を登録しました"
        }
      } catch (e: Exception) {
        _message.value = "場所の登録に失敗しました: ${e.message}"
      }
    }
  }

  /** POI 登録ダイアログのプレビュー用: placeId から施設情報（カテゴリ等）を取得する。 */
  suspend fun fetchPoiDetails(googlePlaceId: String): PlaceSearchResult? = wishlistRepository.fetchPlaceDetails(googlePlaceId)

  /** 誤検知の選び直し用: 座標の近くの POI 候補を取得する。 */
  suspend fun nearbyPois(latitude: Double, longitude: Double): List<PlaceSearchResult> = placeRepository.nearbyPois(latitude, longitude)

  /** 誤検知の訂正: この訪問だけを、選んだ候補／手入力名の場所へ付け替える。 */
  fun reassignStop(stopId: Long, chosen: PlaceSearchResult?, customName: String?) {
    viewModelScope.launch {
      try {
        placeRepository.reassignStopPlace(stopId, chosen, customName)
      } catch (e: Exception) {
        _message.value = "場所の選び直しに失敗しました: ${e.message}"
      }
    }
  }

  fun clearMessage() {
    _message.value = null
  }

  /**
   * 再解析: その経路を検出し直し、**既存の立ち寄りに無い候補**を選択ダイアログに出す（非破壊）。
   * 実際の追加は [addStops] でユーザーが選んだ分だけ行う。
   */
  fun reanalyze() {
    val trackId = loadedTrackId.value ?: return
    viewModelScope.launch {
      _reanalyzeCandidates.value = placeRepository.detectMissingStops(trackId)
    }
  }

  /** 再解析の候補から選んだ立ち寄りだけを追加する。既存の立ち寄りには触れない。 */
  fun addStops(candidates: List<StopCandidate>) {
    val trackId = loadedTrackId.value ?: return
    _reanalyzeCandidates.value = null
    if (candidates.isEmpty()) return
    viewModelScope.launch { placeRepository.addStops(trackId, candidates) }
  }

  /** 再解析の候補ダイアログを閉じる（追加しない）。 */
  fun dismissReanalyze() {
    _reanalyzeCandidates.value = null
  }

  /**
   * 手動追加: ユーザーが地図で指した地点を立ち寄りとして追加する（検出に頼らない完全手動）。
   * 到着／出発は画面側で最寄り軌跡点から決めて渡す。追加後は stops の Flow で自動反映される。
   */
  fun addManualStop(
    latitude: Double,
    longitude: Double,
    arrivalTime: Date,
    departureTime: Date,
    name: String?,
    googlePlaceId: String?,
  ) {
    val trackId = loadedTrackId.value ?: return
    viewModelScope.launch {
      placeRepository.addManualStop(trackId, latitude, longitude, arrivalTime, departureTime, name, googlePlaceId)
    }
  }
}
