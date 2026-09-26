package com.pathly.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.data.backup.BackupException
import com.pathly.data.backup.BackupSummary
import com.pathly.data.backup.DataBackupManager
import com.pathly.data.settings.LocationAccuracy
import com.pathly.data.settings.SettingsRepository
import com.pathly.data.tracking.TrackingController
import com.pathly.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val settingsRepository: SettingsRepository,
  private val backupManager: DataBackupManager,
  trackingController: TrackingController,
) : ViewModel() {

  private val logger = Logger("SettingsViewModel")

  val gpsIntervalSeconds: StateFlow<Int> = settingsRepository.gpsIntervalSeconds

  val gpsIntervalOptions: List<Int> = SettingsRepository.GPS_INTERVAL_OPTIONS

  fun setGpsIntervalSeconds(seconds: Int) {
    settingsRepository.setGpsIntervalSeconds(seconds)
  }

  val locationAccuracy: StateFlow<LocationAccuracy> = settingsRepository.locationAccuracy

  val locationAccuracyOptions: List<LocationAccuracy> = LocationAccuracy.entries

  fun setLocationAccuracy(accuracy: LocationAccuracy) {
    settingsRepository.setLocationAccuracy(accuracy)
  }

  // ---- データの書き出し・読み込み（→ docs/specs/backup.md） ----

  private val _backup = MutableStateFlow(BackupUiState(beforeImportSavedAtMillis = backupManager.beforeImportSavedAtMillis()))
  val backup: StateFlow<BackupUiState> = _backup.asStateFlow()

  /** 記録中（停止後の確定中を含む）は読み込めない。書き出しはいつでもできる。 */
  val isRecording: StateFlow<Boolean> = combine(trackingController.isTracking, trackingController.isFinalizing) { tracking, finalizing ->
    tracking || finalizing
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  fun export(uri: Uri) = runBusy {
    backupManager.export(uri)
    _backup.update { it.copy(message = BackupMessage.Exported) }
  }

  /** 選ばれたファイルを確かめ、置き換えてよいかの確認に進む。 */
  fun prepareImport(uri: Uri) = runBusy {
    val summary = backupManager.prepareImport(uri)
    _backup.update { it.copy(confirm = BackupConfirm(summary, isUndo = false)) }
  }

  /** 直前の読み込みの前に退避した状態へ戻す（確認に進む）。 */
  fun prepareUndoImport() = runBusy {
    val summary = backupManager.prepareRestoreBeforeImport()
    _backup.update { it.copy(confirm = BackupConfirm(summary, isUndo = true)) }
  }

  fun confirmImport() = runBusy {
    _backup.update { it.copy(confirm = null) }
    backupManager.commitImport()
    _backup.update { it.copy(restartRequired = true) }
  }

  fun cancelImport() {
    backupManager.discardCandidate()
    _backup.update { it.copy(confirm = null) }
  }

  fun dismissMessage() {
    _backup.update { it.copy(message = null) }
  }

  private fun runBusy(block: suspend () -> Unit) {
    if (_backup.value.busy) return
    _backup.update { it.copy(busy = true) }
    viewModelScope.launch {
      try {
        block()
      } catch (e: BackupException) {
        logger.w("Backup failed: ${e.reason}", e)
        _backup.update { it.copy(message = BackupMessage.Failed(e.reason)) }
      } catch (e: Exception) {
        logger.e("Backup failed", e)
        _backup.update { it.copy(message = BackupMessage.Failed(null)) }
      } finally {
        _backup.update { it.copy(busy = false) }
      }
    }
  }
}

data class BackupUiState(
  val busy: Boolean = false,
  /** 置き換えの確認中（null なら確認を出さない）。 */
  val confirm: BackupConfirm? = null,
  val message: BackupMessage? = null,
  /** 読み込みを予約した。再起動すると入れ替わる。 */
  val restartRequired: Boolean = false,
  /** 読み込み前の状態を退避した日時。無ければ「戻す」を出さない。 */
  val beforeImportSavedAtMillis: Long? = null,
)

data class BackupConfirm(val summary: BackupSummary, val isUndo: Boolean)

sealed interface BackupMessage {
  data object Exported : BackupMessage

  /** [reason] が null なら想定外の失敗。 */
  data class Failed(val reason: BackupException.Reason?) : BackupMessage
}
