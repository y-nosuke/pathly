package com.pathly.presentation.places

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * 場所を削除した直後の「まだ取り消せる」状態。[token] が増えるたびに一度だけ通知を出す。
 *
 * 場所の削除は確認ダイアログを出さず即時に行い、代わりにスナックバーの「取り消す」で戻す
 * （`docs/specs/screens.md`）。削除できる画面が場所一覧・場所詳細・記録画面・経路詳細に
 * 増えたので、どの画面でも同じ出し方になるようここに集約する。
 */
data class PlaceDeleteUndo(
  val token: Int = 0,
  val name: String? = null,
) {
  /** 削除を1件控えた新しい状態を返す（ViewModel 側で `_uiState.update { it.copy(...) }` に使う）。 */
  fun deleted(name: String?) = PlaceDeleteUndo(token = token + 1, name = name)
}

/** [undo] を監視して「取り消す」付きのスナックバーを出す。 */
@Composable
fun PlaceDeleteUndoEffect(
  undo: PlaceDeleteUndo,
  snackbarHostState: SnackbarHostState,
  onUndo: () -> Unit,
) {
  LaunchedEffect(undo.token) {
    // 初期値（まだ何も消していない）では出さない。
    if (undo.token == 0) return@LaunchedEffect
    val result = snackbarHostState.showSnackbar(
      message = "「${undo.name ?: "場所"}」を削除しました",
      actionLabel = "取り消す",
      duration = SnackbarDuration.Short,
    )
    if (result == SnackbarResult.ActionPerformed) onUndo()
  }
}
