package com.pathly.presentation.stops

import androidx.compose.runtime.Composable
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Stop
import com.pathly.presentation.common.NearbyCandidatePickerDialog

/**
 * 誤検知の訂正: 確定済みの立ち寄り（[stop]）の場所を、近くの POI 候補から選び直す／自分で入力し直す
 * ダイアログ。**この訪問だけ**付け替える（他の経路・訪問は不変）。記録画面・履歴詳細で共用する。
 * 候補提示の中身は共通の [NearbyCandidatePickerDialog] に委譲する。
 *
 * 座標の近くの候補に出ない施設（代表点が敷地の中心にある・地図に POI が出ていない）は
 * それだけでは選べないので、**名前での検索**も渡す（場所詳細の紐付けと同じ）。
 */
@Composable
fun StopReassignDialog(
  stop: Stop,
  onFetchCandidates: suspend (Double, Double) -> List<PlaceSearchResult>,
  onConfirm: (chosen: PlaceSearchResult?, customName: String?) -> Unit,
  onDismiss: () -> Unit,
  onSearchPredictions: (suspend (String) -> List<PlacePrediction>)? = null,
  onFetchPrediction: (suspend (String) -> PlaceSearchResult?)? = null,
) {
  NearbyCandidatePickerDialog(
    latitude = stop.place.latitude,
    longitude = stop.place.longitude,
    reloadKey = stop.id,
    title = "場所を選び直す",
    currentLabel = "現在: ${stop.place.name ?: stop.place.googleName ?: "未命名"}",
    description = "この訪問だけを、正しい場所に付け替えます（他の経路・訪問は変わりません）。",
    confirmLabel = "この場所に直す",
    allowCustomName = true,
    onFetchCandidates = onFetchCandidates,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    onSearchPredictions = onSearchPredictions,
    onFetchPrediction = onFetchPrediction,
  )
}
