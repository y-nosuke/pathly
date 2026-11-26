package com.pathly.presentation.stops

import com.pathly.domain.model.NearbyStopPrompt
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.repository.LiveStopRepository
import com.pathly.domain.repository.StopRepository
import com.pathly.domain.usecase.AddManualStopUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

/**
 * 立ち寄りを手で足す・付け直す（記録画面と経路詳細で共有する）。
 *
 * 近くに既存の場所があるときの確認待ち（[nearbyStopPrompt]）はこの部品が持つ。画面ごとに違うところは
 * 持ち主の ViewModel から受ける:
 * - [trackId]: 立ち寄りを足す経路（記録中の経路・開いている経路）。無ければ何もしない。
 * - [shownOnMap]: 近接確認で「地図に見えている」とみなす場所（押した時点の条件で判断する）。
 * - [onMessage]・[onError]: 結果の一言と失敗を出す先。
 */
class ManualStopActions internal constructor(
  private val scope: CoroutineScope,
  private val addManualStopUseCase: AddManualStopUseCase,
  private val stopRepository: StopRepository,
  private val liveStopRepository: LiveStopRepository,
  private val trackId: () -> Long?,
  private val shownOnMap: () -> (RegisteredPlace) -> Boolean,
  private val onMessage: (String) -> Unit,
  private val onError: (String) -> Unit,
) : ManualStopUi {
  private val _nearbyStopPrompt = MutableStateFlow<NearbyStopPrompt?>(null)

  /** 手動の立ち寄り追加で近くに既存の場所が見つかったときの確認待ち。 */
  override val nearbyStopPrompt: StateFlow<NearbyStopPrompt?> = _nearbyStopPrompt.asStateFlow()

  /** 立ち寄りを足して「立ち寄りを追加しました」を出す。失敗は [onError] に出す。 */
  private fun launchAdd(block: suspend (trackId: Long) -> Boolean) {
    val id = trackId() ?: return
    scope.launch {
      try {
        if (block(id)) onMessage("立ち寄りを追加しました")
      } catch (e: Exception) {
        onError("立ち寄りの追加に失敗しました: ${e.message}")
      }
    }
  }

  /**
   * 手動で立ち寄りを追加する。近くに既存の場所があれば追加せず確認待ちにする。
   * 「登録済みの場所」を地図に表示中なら、ユーザーは既存を見たうえでの操作なので確認しない。
   */
  override fun addManualStopWithNearbyCheck(
    latitude: Double,
    longitude: Double,
    arrivalTime: Date,
    departureTime: Date,
    name: String?,
    googlePlaceId: String?,
    googleName: String?,
    pointedOnMap: Boolean,
  ) {
    val isShownOnMap = shownOnMap()
    launchAdd { trackId ->
      val result = addManualStopUseCase.addWithNearbyCheck(
        trackId,
        latitude,
        longitude,
        arrivalTime,
        departureTime,
        name,
        googlePlaceId,
        isShownOnMap = isShownOnMap,
        googleName = googleName,
        pointedOnMap = pointedOnMap,
      )
      when (result) {
        is AddManualStopUseCase.AddResult.NearbyFound -> {
          _nearbyStopPrompt.value = NearbyStopPrompt(result.nearby, latitude, longitude, arrivalTime, departureTime, name)
          false
        }

        is AddManualStopUseCase.AddResult.Added -> true
      }
    }
  }

  /** 近接確認で「この場所に紐付け」を選んだとき。 */
  override fun confirmNearbyStopLink() {
    val prompt = _nearbyStopPrompt.value ?: return
    _nearbyStopPrompt.value = null
    addManualStopForPlace(prompt.nearby.placeId, prompt.arrivalTime, prompt.departureTime)
  }

  /** 近接確認で「新規で追加」を選んだとき。座標同定せず必ず新しい場所を作る。 */
  override fun confirmNearbyStopNew() {
    val prompt = _nearbyStopPrompt.value ?: return
    _nearbyStopPrompt.value = null
    launchAdd { trackId ->
      addManualStopUseCase.addAsNew(trackId, prompt.latitude, prompt.longitude, prompt.arrivalTime, prompt.departureTime, prompt.name)
      true
    }
  }

  override fun dismissNearbyStopPrompt() {
    _nearbyStopPrompt.value = null
  }

  /** 地図の登録済みマーカーを選んで、既存 place にこの訪問を紐付ける（新規 place を作らない）。 */
  override fun addManualStopForPlace(placeId: Long, arrivalTime: Date, departureTime: Date) {
    launchAdd { trackId ->
      addManualStopUseCase.addForExistingPlace(trackId, placeId, arrivalTime, departureTime)
      true
    }
  }

  /**
   * 「今ここを立ち寄り」: その場で立ち寄り中にする（すでに立ち寄り中なら場所だけ選び直す）。[anchorLatitude]・[anchorLongitude] は
   * 押したときの現在地（離れたかの判定に使う）、[input] は選んだ場所と押した時刻（到着）。
   */
  override fun startStopHere(anchorLatitude: Double, anchorLongitude: Double, input: ManualStopInput) {
    val id = trackId() ?: return
    scope.launch {
      try {
        liveStopRepository.startLiveStop(
          trackId = id,
          anchorLatitude = anchorLatitude,
          anchorLongitude = anchorLongitude,
          arrivalTime = input.arrivalTime,
          latitude = input.latitude,
          longitude = input.longitude,
          name = input.name,
          googlePlaceId = input.googlePlaceId,
          googleName = input.googleName,
        )
      } catch (e: Exception) {
        onError("立ち寄り中にできませんでした: ${e.message}")
      }
    }
  }

  /** 誤検知の訂正: この訪問だけを、選んだ候補／手入力名の場所へ付け替える。 */
  override fun reassignStop(stopId: Long, chosen: PlaceSearchResult?, customName: String?) {
    scope.launch {
      try {
        stopRepository.reassignStopPlace(stopId, chosen, customName)
      } catch (e: Exception) {
        onError("場所の選び直しに失敗しました: ${e.message}")
      }
    }
  }
}
