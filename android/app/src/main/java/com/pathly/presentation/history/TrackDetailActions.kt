package com.pathly.presentation.history

import com.pathly.domain.model.SameFacilityPending
import com.pathly.domain.model.StopCandidate
import java.util.Date

// 経路詳細（[TrackDetailScreen]）に渡す引数のまとまり。場所の編集（PlaceEditUi）・立ち寄りの手での追加
// （ManualStopUi）と同じく、画面のまとまりごとに 1 つで渡す（引数を 1 つずつ並べると、どれが何の操作か追いにくい）。
// data class にして、中身が同じなら画面の再描画を省けるようにする（操作は ViewModel の関数参照で渡す）。

/** 立ち寄りの操作（一覧の行・まとめて選んだときの操作・期間の編集）。 */
data class TrackStopActions(
  /** 場所の名前を変える（その場所の全ての訪問に効く）。 */
  val onEditPlaceName: (placeId: Long, name: String) -> Unit = { _, _ -> },
  /** この訪問のメモ（空なら消す）。 */
  val onEditNote: (stopId: Long, note: String?) -> Unit = { _, _ -> },
  /** 行の旗で、その場所の「行きたい」をワンタップで付け外しする（#92）。 */
  val onToggleWishlist: (placeId: Long) -> Unit = {},
  /** 滞在期間の手直し。GPS 点・補正後の点は触らない。 */
  val onUpdateDuration: (stopId: Long, arrival: Date, departure: Date) -> Unit = { _, _, _ -> },
  val onDelete: (stopIds: List<Long>) -> Unit = {},
  /** 同じ場所への複数の訪問を 1 件にまとめる（→ adr/0024）。判定は ViewModel/Repository 側。 */
  val onMerge: (stopIds: List<Long>) -> Unit = {},
  /** 直前の削除・まとめを取り消す（スナックバーの「取り消す」）。 */
  val onUndo: () -> Unit = {},
  /** 名前の無い立ち寄りの名前を Google から引き直す。 */
  val onResolveNames: () -> Unit = {},
)

/** 再解析（一覧に無い立ち寄りを探し直す）。[candidates] が null の間は再解析していない。 */
data class StopReanalysis(
  val candidates: List<StopCandidate>? = null,
  val onStart: () -> Unit = {},
  val onAdd: (List<StopCandidate>) -> Unit = {},
  val onDismiss: () -> Unit = {},
)

/**
 * 同じ施設の確認待ち（→ adr/0037）。[dialogOpen] の間、[pending] の先頭から 1 件ずつ聞く。
 * 「別の場所として残す」は場所の編集（PlaceEditUi.keepAsSeparatePlace）で行う。
 */
data class SameFacilityReview(
  val pending: List<SameFacilityPending> = emptyList(),
  val dialogOpen: Boolean = false,
  val onOpen: () -> Unit = {},
  val onDismiss: () -> Unit = {},
  val onMerge: (SameFacilityPending) -> Unit = {},
)
