package com.pathly.presentation.stops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.NearbyStopPrompt
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Stop
import com.pathly.presentation.common.CloseInfoWindowWithSheet
import com.pathly.presentation.common.FloatingSheetState
import com.pathly.presentation.common.MapInfoWindowState
import com.pathly.presentation.common.NearbyConfirmPurpose
import com.pathly.presentation.common.NearbyPlaceConfirmDialog
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.presentation.places.PlaceEditUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Date

/**
 * 立ち寄りを手で足す・付け直す UI が使う操作と状態。実体は [ManualStopActions]（各画面の ViewModel が持つ）。
 *
 * 画面のテストは [None] から必要なものだけを差し替える（`object : ManualStopUi by ManualStopUi.None { ... }`）。
 */
interface ManualStopUi {
  /** 手動の立ち寄り追加で近くに既存の場所が見つかったときの確認待ち。 */
  val nearbyStopPrompt: StateFlow<NearbyStopPrompt?>

  /**
   * 手動で立ち寄りを追加する。近くに既存の場所があれば追加せず確認待ちにする。
   * [pointedOnMap] は地図の空き地点を指したとき（別の場所として足す → AddManualStopUseCase.addWithNearbyCheck）。
   */
  fun addManualStopWithNearbyCheck(
    latitude: Double,
    longitude: Double,
    arrivalTime: Date,
    departureTime: Date,
    name: String?,
    googlePlaceId: String?,
    googleName: String? = null,
    pointedOnMap: Boolean = false,
  )

  fun confirmNearbyStopLink()
  fun confirmNearbyStopNew()
  fun dismissNearbyStopPrompt()

  /** 既存の場所にこの訪問を紐付ける（新規の場所を作らない）。 */
  fun addManualStopForPlace(placeId: Long, arrivalTime: Date, departureTime: Date)

  /** 誤検知の訂正: この訪問だけを、選んだ候補／手入力名の場所へ付け替える。 */
  fun reassignStop(stopId: Long, chosen: PlaceSearchResult?, customName: String?)

  /** 何もしない（画面のテストの既定）。 */
  object None : ManualStopUi {
    override val nearbyStopPrompt: StateFlow<NearbyStopPrompt?> = MutableStateFlow(null)

    override fun addManualStopWithNearbyCheck(
      latitude: Double,
      longitude: Double,
      arrivalTime: Date,
      departureTime: Date,
      name: String?,
      googlePlaceId: String?,
      googleName: String?,
      pointedOnMap: Boolean,
    ) = Unit

    override fun confirmNearbyStopLink() = Unit
    override fun confirmNearbyStopNew() = Unit
    override fun dismissNearbyStopPrompt() = Unit
    override fun addManualStopForPlace(placeId: Long, arrivalTime: Date, departureTime: Date) = Unit
    override fun reassignStop(stopId: Long, chosen: PlaceSearchResult?, customName: String?) = Unit
  }
}

/**
 * 立ち寄りを手で足す UI のつなぎ込み。記録画面・経路詳細で使う。
 *
 * - [target] があれば、到着・出発を決めるシート（[ManualStopSheet]）を出す。シートは**この関数を置いた位置**に
 *   重なるので、画面の Box の中で、重ね順を保ちたい位置に置く。確定したら足して [onAdded] を呼ぶ。
 * - 近くに既存の場所があるときの確認ダイアログを出す。
 *
 * 施設の候補・名前での検索は [placeEdit] から引く（場所の登録と同じ）。
 *
 * @param points 到着・出発を選ぶ軌跡の点。
 * @param onRangeChange 選んでいる滞在区間（点のインデックス）。地図のハイライトに使う。
 * @param sheetState 地図の下パディングをシートに合わせたい画面は、自分で作った state を渡す。
 */
@Composable
internal fun ManualStopHost(
  manualStop: ManualStopUi,
  placeEdit: PlaceEditUi,
  target: ManualStopTarget?,
  points: List<GpsPoint>,
  onAdded: () -> Unit,
  onCancel: () -> Unit,
  onRangeChange: (start: Int, end: Int) -> Unit,
  modifier: Modifier = Modifier,
  infoWindow: MapInfoWindowState? = null,
  sheetState: FloatingSheetState? = null,
) {
  target?.let { t ->
    infoWindow?.let { CloseInfoWindowWithSheet(it) }
    ManualStopSheet(
      origin = t.origin,
      latitude = t.latitude,
      longitude = t.longitude,
      points = points,
      onFetchCandidates = placeEdit::nearbyPois,
      onSearchPredictions = placeEdit::predictPlaces,
      onFetchPrediction = placeEdit::fetchPlaceResult,
      onConfirm = { input ->
        val origin = t.origin
        if (origin is ManualStopOrigin.ExistingPlace) {
          manualStop.addManualStopForPlace(origin.placeId, input.arrivalTime, input.departureTime)
        } else {
          manualStop.addManualStopWithNearbyCheck(
            input.latitude,
            input.longitude,
            input.arrivalTime,
            input.departureTime,
            input.name,
            input.googlePlaceId,
            input.googleName,
            pointedOnMap = origin is ManualStopOrigin.MapPoint,
          )
        }
        onAdded()
      },
      onCancel = onCancel,
      onRangeChange = onRangeChange,
      modifier = modifier,
      sheetState = sheetState ?: rememberFloatingSheetState(peekFraction = 0.5f),
    )
  }

  // 近接確認: 近くに既存があれば紐付け／新規を選ぶ。
  val nearbyStopPrompt by manualStop.nearbyStopPrompt.collectAsStateWithLifecycle()
  nearbyStopPrompt?.let { prompt ->
    NearbyPlaceConfirmDialog(
      place = prompt.nearby,
      purpose = NearbyConfirmPurpose.ADD_STOP,
      onLink = manualStop::confirmNearbyStopLink,
      onCreateNew = manualStop::confirmNearbyStopNew,
      onDismiss = manualStop::dismissNearbyStopPrompt,
    )
  }
}

/**
 * 立ち寄りの「場所を選び直す」（誤検知の訂正）のつなぎ込み。記録画面・経路詳細で使う。
 * [stop] があればダイアログを出し、確定・キャンセルで [onDismiss] を呼ぶ。
 */
@Composable
internal fun StopReassignHost(
  stop: Stop?,
  manualStop: ManualStopUi,
  placeEdit: PlaceEditUi,
  onDismiss: () -> Unit,
  infoWindow: MapInfoWindowState? = null,
) {
  stop ?: return
  infoWindow?.let { CloseInfoWindowWithSheet(it) }
  StopReassignDialog(
    stop = stop,
    onFetchCandidates = placeEdit::nearbyPois,
    onSearchPredictions = placeEdit::predictPlaces,
    onFetchPrediction = placeEdit::fetchPlaceResult,
    onConfirm = { chosen, customName ->
      manualStop.reassignStop(stop.id, chosen, customName)
      onDismiss()
    },
    onDismiss = onDismiss,
  )
}
