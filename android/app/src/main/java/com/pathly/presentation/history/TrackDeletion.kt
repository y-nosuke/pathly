package com.pathly.presentation.history

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.util.DateFormatters
import com.pathly.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 経路の削除を、取り消せる間だけ待つ入れ物（スナックバーの「取り消す」）。
 *
 * 経路を消すと点列・立ち寄りまで CASCADE で消えるので、消してから書き戻すのではなく、**消すのを待つ**。
 * 待っている間は一覧から隠し（[hiddenIds]）、スナックバーが「取り消す」を押されずに閉じたら本当に消す（[commit]）。
 * 待っている途中でアプリが落ちたときは消えずに残る（安全な側）。
 *
 * 経路詳細で消すと画面を戻るので、スナックバーは画面ではなくアプリ全体の器（PathlyNavHost）が出す。
 */
@Singleton
class TrackDeletionQueue @Inject constructor(
  private val gpsTrackRepository: GpsTrackRepository,
) {
  /** 取り消しを待っている削除。[label] はスナックバーに出す経路の呼び名。 */
  data class Pending(val track: GpsTrack, val label: String)

  private val _pending = MutableStateFlow<Pending?>(null)
  val pending: StateFlow<Pending?> = _pending.asStateFlow()

  // 一覧から隠す経路。待っている間と、消し終わる（DB の更新が一覧に届く）までの間。
  private val _hiddenIds = MutableStateFlow<Set<Long>>(emptySet())
  val hiddenIds: StateFlow<Set<Long>> = _hiddenIds.asStateFlow()

  // 消す処理は画面を離れても続ける。テストでは差し替える。
  internal var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val logger = Logger("TrackDeletionQueue")

  /**
   * [track] を消す予定にする。前に待っていた削除があれば、それは今消す（スナックバーは最新の 1 件だけ出す）。
   * 同じ経路がもう待っている・消している途中なら何もしない（別の画面から開き直して消したときに、
   * 前の待ちを確定させて取り消せなくしないため）。
   */
  fun request(track: GpsTrack) {
    if (track.id in _hiddenIds.value) return
    _pending.value?.let { commit(it.track.id) }
    _hiddenIds.update { it + track.id }
    // 消すのに要るのは経路の行だけ。点列は持たない（詳細から渡された経路は数万点のことがある）。
    val light = track.copy(points = emptyList(), smoothedOverride = null)
    _pending.value = Pending(track = light, label = trackLabel(track))
  }

  /** 待っている削除をやめて、一覧に戻す。 */
  fun undo(trackId: Long) {
    if (_pending.value?.track?.id != trackId) return
    _pending.value = null
    _hiddenIds.update { it - trackId }
  }

  /** 待っている削除を本当に行う。 */
  fun commit(trackId: Long) {
    val pending = _pending.value?.takeIf { it.track.id == trackId } ?: return
    _pending.value = null
    scope.launch {
      try {
        gpsTrackRepository.deleteTrack(pending.track)
        // 消えたことが一覧の元（DB の Flow）に届くまで隠したままにする。先に外すと、古い一覧で一瞬だけ行が戻って見える。
        withTimeoutOrNull(DB_UPDATE_WAIT_MS) {
          gpsTrackRepository.getAllTracks().first { tracks -> tracks.none { it.id == trackId } }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // 消せなかったときは一覧に戻すだけにする（アプリは落とさない）。
        logger.e("Failed to delete track $trackId", e)
      } finally {
        _hiddenIds.update { it - trackId }
      }
    }
  }

  private fun trackLabel(track: GpsTrack): String = track.name?.takeIf { it.isNotBlank() } ?: DateFormatters.shortDate(track.startTime)

  private companion object {
    const val DB_UPDATE_WAIT_MS = 5_000L
  }
}

/** アプリ全体の器から [TrackDeletionQueue] を使うための ViewModel。 */
@HiltViewModel
class TrackDeletionViewModel @Inject constructor(
  private val queue: TrackDeletionQueue,
) : ViewModel() {
  val pending: StateFlow<TrackDeletionQueue.Pending?> = queue.pending

  fun undo(trackId: Long) = queue.undo(trackId)

  fun commit(trackId: Long) = queue.commit(trackId)
}

/**
 * 経路を消す予定にしたら「取り消す」付きのスナックバーを出し、取り消されなければ消す。
 * アプリ全体の器（PathlyNavHost の Scaffold）に 1 つだけ置く。
 */
@Composable
fun TrackDeletionSnackbarEffect(viewModel: TrackDeletionViewModel, snackbarHostState: SnackbarHostState) {
  val pending by viewModel.pending.collectAsStateWithLifecycle()
  val current = pending ?: return
  LaunchedEffect(current) {
    val result = snackbarHostState.showSnackbar(
      message = "「${current.label}」を削除しました",
      actionLabel = "取り消す",
      duration = SnackbarDuration.Short,
    )
    if (result == SnackbarResult.ActionPerformed) {
      viewModel.undo(current.track.id)
    } else {
      viewModel.commit(current.track.id)
    }
  }
}
