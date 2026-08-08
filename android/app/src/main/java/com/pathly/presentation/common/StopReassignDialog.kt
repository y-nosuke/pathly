package com.pathly.presentation.common

import androidx.compose.runtime.Composable
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Stop

/**
 * 誤検知の訂正: 確定済みの立ち寄り（[stop]）の場所を、近くの POI 候補から選び直す／自分で入力し直す
 * ダイアログ。**この訪問だけ**付け替える（他の経路・訪問は不変）。記録画面・履歴詳細で共用する。
 * 候補提示の中身は共通の [NearbyCandidatePickerDialog] に委譲する。
 */
@Composable
fun StopReassignDialog(
  stop: Stop,
  onFetchCandidates: suspend (Double, Double) -> List<PlaceSearchResult>,
  onConfirm: (chosen: PlaceSearchResult?, customName: String?) -> Unit,
  onDismiss: () -> Unit,
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
  )
}
