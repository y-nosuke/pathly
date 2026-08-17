package com.pathly.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.data.places.PlacesTextSearcher
import com.pathly.data.settings.MapSurface
import com.pathly.data.settings.SettingsRepository
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.NearbyRegisterPrompt
import com.pathly.domain.model.NearbyStopPrompt
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopCandidate
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlaceRepository
import com.pathly.domain.repository.WishlistRepository
import com.pathly.domain.usecase.AddManualStopUseCase
import com.pathly.domain.usecase.PlaceEditUseCase
import com.pathly.presentation.places.PlaceDeleteUndo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
  private val placeEditUseCase: PlaceEditUseCase,
  private val addManualStopUseCase: AddManualStopUseCase,
  // 「Googleで情報を取得」の名前検索フォールバック用（場所詳細と同じ編集機能を出すため）。
  private val placesTextSearcher: PlacesTextSearcher,
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

  // 場所を削除した直後の取り消し待ち（スナックバーで「取り消す」を出す）。
  private val _deleteUndo = MutableStateFlow(PlaceDeleteUndo())
  val deleteUndo: StateFlow<PlaceDeleteUndo> = _deleteUndo.asStateFlow()

  // 再解析の候補（一覧に無い立ち寄り＋表示名）。null=非表示、空リスト=「候補なし」を表示。
  private val _reanalyzeCandidates = MutableStateFlow<List<StopCandidate>?>(null)
  val reanalyzeCandidates: StateFlow<List<StopCandidate>?> = _reanalyzeCandidates.asStateFlow()

  private val loadedTrackId = MutableStateFlow<Long?>(null)

  /**
   * 記録中に開いたときの「立ち寄り中（ライブ）」。表示中のトラックが記録中の当該トラックのときだけ流す
   * （他トラックを開いていれば null）。地図にだけ出す（一覧・保存には出さない）。
   */
  val currentStop: StateFlow<Stop?> = combine(placeRepository.currentStop, loadedTrackId) { stop, id ->
    stop?.takeIf { it.trackId == id }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
    visited: Boolean,
    memo: String?,
    googlePlaceId: String? = null,
    forceNewPlace: Boolean = false,
  ) {
    viewModelScope.launch {
      try {
        val reg = placeEditUseCase.register(latitude, longitude, name, wishlist, priority, visited, memo, googlePlaceId, forceNewPlace)
        _message.value = registeredMessage(reg.alreadyExisted, name)
      } catch (e: Exception) {
        _message.value = "場所の登録に失敗しました: ${e.message}"
      }
    }
  }

  private val _nearbyRegisterPrompt = MutableStateFlow<NearbyRegisterPrompt?>(null)

  /** 空き地点の登録で近くに既存の場所が見つかったときの確認待ち（紐付け/新規をユーザーが選ぶ）。 */
  val nearbyRegisterPrompt: StateFlow<NearbyRegisterPrompt?> = _nearbyRegisterPrompt.asStateFlow()

  /**
   * 地図タップからの場所登録。近くに既存の場所があれば登録せず確認待ちにする。
   * 「登録済みの場所」を地図に表示中なら、ユーザーは既存を見たうえでの操作なので確認しない。
   */
  fun registerPlaceWithNearbyCheck(
    latitude: Double,
    longitude: Double,
    name: String?,
    wishlist: Boolean,
    priority: Priority,
    visited: Boolean,
    memo: String?,
    googlePlaceId: String?,
    googleName: String? = null,
  ) {
    viewModelScope.launch {
      try {
        val result = placeEditUseCase.registerWithNearbyCheck(
          latitude,
          longitude,
          name,
          wishlist,
          priority,
          visited,
          memo,
          googlePlaceId,
          nearbyAlreadyVisible = showRegisteredPlaces.value,
          googleName = googleName,
        )
        when (result) {
          is PlaceEditUseCase.RegisterResult.NearbyFound ->
            _nearbyRegisterPrompt.value = NearbyRegisterPrompt(
              result.nearby,
              latitude,
              longitude,
              name,
              wishlist,
              priority,
              visited,
              memo,
            )

          is PlaceEditUseCase.RegisterResult.Registered ->
            _message.value = registeredMessage(result.alreadyExisted, name)
        }
      } catch (e: Exception) {
        _message.value = "場所の登録に失敗しました: ${e.message}"
      }
    }
  }

  /** 近接確認で「この場所に紐付け」を選んだとき。 */
  fun confirmNearbyLink() {
    val prompt = _nearbyRegisterPrompt.value ?: return
    _nearbyRegisterPrompt.value = null
    linkRegisterToPlace(prompt.nearby.placeId, prompt.wishlist, prompt.priority, prompt.visited, prompt.memo)
  }

  /** 近接確認で「新規で登録」を選んだとき。座標同定せず必ず新しい場所を作る。 */
  fun confirmNearbyNew() {
    val prompt = _nearbyRegisterPrompt.value ?: return
    _nearbyRegisterPrompt.value = null
    registerPlace(
      prompt.latitude,
      prompt.longitude,
      prompt.name,
      prompt.wishlist,
      prompt.priority,
      prompt.visited,
      prompt.memo,
      googlePlaceId = null,
      forceNewPlace = true,
    )
  }

  fun dismissNearbyPrompt() {
    _nearbyRegisterPrompt.value = null
  }

  private fun registeredMessage(alreadyExisted: Boolean, name: String?): String = if (alreadyExisted) {
    "この場所は登録済みです"
  } else {
    "「${name?.ifBlank { null } ?: "場所"}」を登録しました"
  }

  /** 近接確認で「この場所に紐付け」を選んだとき: 既存 place に行きたい/メモを反映する（新規は作らない）。 */
  fun linkRegisterToPlace(placeId: Long, wishlist: Boolean, priority: Priority, visited: Boolean, memo: String?) {
    viewModelScope.launch {
      try {
        placeEditUseCase.linkToExisting(placeId, wishlist, priority, visited, memo)
        _message.value = "この場所に紐付けました"
      } catch (e: Exception) {
        _message.value = "紐付けに失敗しました: ${e.message}"
      }
    }
  }

  /** 統一の場所シートで既存 place を編集するため、単一 place をリアクティブに購読する。 */
  fun observePlace(placeId: Long): Flow<PlaceListItem?> = wishlistRepository.observePlace(placeId)

  /** その場所を含むお出掛けの一覧（編集シートの訪問履歴）。 */
  fun visitsFor(placeId: Long): Flow<List<PlaceVisit>> = wishlistRepository.getVisits(placeId)

  /**
   * 経路詳細のまま既存 place を編集して保存する（名前・メモ・行きたい・優先度・訪問済み・
   * Google 施設の紐付けを差分適用）。場所詳細と同じ UseCase で同じことができるようにする。
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
        placeEditUseCase.saveEdits(item, name, note, wishlist, priority, visited, link)
        _message.value = "保存しました"
      } catch (e: Exception) {
        _message.value = "保存に失敗しました: ${e.message}"
      }
    }
  }

  /**
   * 場所そのものを削除する。確認ダイアログは出さず即時削除し、スナックバーの「取り消す」
   * （[undoDelete]）で戻せる（場所一覧・記録画面と同じ流儀）。
   */
  fun deletePlace(placeId: Long) {
    viewModelScope.launch {
      val name = registeredPlaces.value.firstOrNull { it.placeId == placeId }?.displayName
      try {
        wishlistRepository.deletePlace(placeId)
        _deleteUndo.value = _deleteUndo.value.deleted(name)
      } catch (e: Exception) {
        _message.value = "削除に失敗しました: ${e.message}"
      }
    }
  }

  /** 直近の削除を取り消して元に戻す（スナックバーの「取り消す」）。 */
  fun undoDelete() {
    viewModelScope.launch {
      try {
        wishlistRepository.undoLastPlaceDeletion()
      } catch (e: Exception) {
        _message.value = "取り消しに失敗しました: ${e.message}"
      }
    }
  }

  /** 「Googleで情報を取得」の名前検索フォールバック: キーワード候補を返す。 */
  suspend fun predictPlaces(query: String): List<PlacePrediction> = placesTextSearcher.predict(query)

  /** 名前検索で選んだ候補を、座標つきの施設情報に確定する。 */
  suspend fun fetchPlaceResult(placeId: String): PlaceSearchResult? = placesTextSearcher.fetch(placeId)

  /** 場所シートのプレビュー用: placeId から施設情報（カテゴリ等）を取得する。 */
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
   * 再解析: その経路を検出し直し、**既存の立ち寄りに無い候補**を地図のピンと候補オーバーレイに出す（非破壊）。
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

  /** 再解析の候補表示を閉じる（追加しない）。 */
  fun dismissReanalyze() {
    _reanalyzeCandidates.value = null
  }

  private val _nearbyStopPrompt = MutableStateFlow<NearbyStopPrompt?>(null)

  /** 手動の立ち寄り追加で近くに既存の場所が見つかったときの確認待ち。 */
  val nearbyStopPrompt: StateFlow<NearbyStopPrompt?> = _nearbyStopPrompt.asStateFlow()

  /**
   * 手動で立ち寄りを追加する。近くに既存の場所があれば追加せず確認待ちにする。
   * 「登録済みの場所」を地図に表示中なら、ユーザーは既存を見たうえでの操作なので確認しない。
   */
  fun addManualStopWithNearbyCheck(
    latitude: Double,
    longitude: Double,
    arrivalTime: Date,
    departureTime: Date,
    name: String?,
    googlePlaceId: String?,
    googleName: String? = null,
  ) {
    val trackId = loadedTrackId.value ?: return
    viewModelScope.launch {
      try {
        val result = addManualStopUseCase.addWithNearbyCheck(
          trackId,
          latitude,
          longitude,
          arrivalTime,
          departureTime,
          name,
          googlePlaceId,
          nearbyAlreadyVisible = showRegisteredPlaces.value,
          googleName = googleName,
        )
        when (result) {
          is AddManualStopUseCase.AddResult.NearbyFound ->
            _nearbyStopPrompt.value = NearbyStopPrompt(
              result.nearby,
              latitude,
              longitude,
              arrivalTime,
              departureTime,
              name,
            )

          is AddManualStopUseCase.AddResult.Added -> _message.value = "立ち寄りを追加しました"
        }
      } catch (e: Exception) {
        _message.value = "立ち寄りの追加に失敗しました: ${e.message}"
      }
    }
  }

  /** 近接確認で「この場所に紐付け」を選んだとき。 */
  fun confirmNearbyStopLink() {
    val prompt = _nearbyStopPrompt.value ?: return
    _nearbyStopPrompt.value = null
    addManualStopForPlace(prompt.nearby.placeId, prompt.arrivalTime, prompt.departureTime)
  }

  /** 近接確認で「新規で追加」を選んだとき。座標同定せず必ず新しい場所を作る。 */
  fun confirmNearbyStopNew() {
    val prompt = _nearbyStopPrompt.value ?: return
    _nearbyStopPrompt.value = null
    val trackId = loadedTrackId.value ?: return
    viewModelScope.launch {
      try {
        addManualStopUseCase.addAsNew(
          trackId,
          prompt.latitude,
          prompt.longitude,
          prompt.arrivalTime,
          prompt.departureTime,
          prompt.name,
        )
        _message.value = "立ち寄りを追加しました"
      } catch (e: Exception) {
        _message.value = "立ち寄りの追加に失敗しました: ${e.message}"
      }
    }
  }

  fun dismissNearbyStopPrompt() {
    _nearbyStopPrompt.value = null
  }

  /** 地図の登録済みマーカーを選んで、既存 place にこの訪問を紐付ける（新規 place を作らない）。 */
  fun addManualStopForPlace(placeId: Long, arrivalTime: Date, departureTime: Date) {
    val trackId = loadedTrackId.value ?: return
    viewModelScope.launch {
      addManualStopUseCase.addForExistingPlace(trackId, placeId, arrivalTime, departureTime)
      _message.value = "立ち寄りを追加しました"
    }
  }
}
