package com.pathly.presentation.tracking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathly.domain.model.Plan
import com.pathly.domain.model.PlanTrackSource
import com.pathly.domain.repository.GpsTrackRepository
import com.pathly.domain.repository.PlanRepository
import com.pathly.presentation.plans.dayCountLabel
import com.pathly.presentation.plans.formatPlanDate
import com.pathly.ui.theme.PathlySpacing
import com.pathly.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import javax.inject.Inject

/** 記録の開始で聞く中身: 始めた経路と、今日が日付に入る計画と、もう結び付けた計画（最初の計画）。 */
data class PlanLinkPrompt(val trackId: Long, val plans: List<Plan>, val today: LocalDate, val linkedPlanId: Long)

/**
 * 記録を始めたとき、今日が日付に入る保存済みの計画があれば、最初の計画に結び付けたうえで、それでよいかを聞く
 * （→ docs/specs/plans.md の「記録の開始で聞く」）。記録はすぐ始まり、聞くのはその後。選び直す・外すのは利用者。
 */
@HiltViewModel
class PlanLinkPromptViewModel @Inject constructor(
  private val planRepository: PlanRepository,
  private val gpsTrackRepository: GpsTrackRepository,
) : ViewModel() {

  private val logger = Logger("PlanLinkPrompt")

  private val _prompt = MutableStateFlow<PlanLinkPrompt?>(null)
  val prompt: StateFlow<PlanLinkPrompt?> = _prompt.asStateFlow()

  private var job: Job? = null

  /** 記録の開始を押した。経路ができるのを待ち、今日の計画があれば聞く。 */
  fun onTrackingStarted(today: LocalDate = LocalDate.now(), startedAtMillis: Long = System.currentTimeMillis()) {
    job?.cancel()
    job = viewModelScope.launch {
      try {
        val plans = planRepository.plansOn(today)
        if (plans.isEmpty()) return@launch
        // 経路はサービスが作るので、記録中の経路が出てくるのを待つ（開始できなかったら出てこないので、待つのをやめる）。
        val trackId = withTimeoutOrNull(TRACK_WAIT_MILLIS) {
          // 押す前から残っていた記録中の経路（中断したものなど）ではなく、押した後に始まった経路を待つ。
          gpsTrackRepository.getActiveTrackRealtime()
            .mapNotNull { track -> track?.takeIf { it.startTime.time >= startedAtMillis - START_SLACK_MILLIS }?.id }
            .first()
        } ?: return@launch
        val sorted = plans.sortedWith(compareBy({ it.startDate }, { it.dayCount }, { it.id }))
        // 最初の計画にもう結び付けておく（聞くのに答えなくても、結び付いた状態になる）。
        planRepository.linkTrack(sorted.first().id, trackId, PlanTrackSource.START)
        _prompt.value = PlanLinkPrompt(trackId, sorted, today, linkedPlanId = sorted.first().id)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        logger.e("Failed to prepare plan link prompt", e)
      }
    }
  }

  /** 選んだ計画にする（最初の計画のままなら何もしない）。 */
  fun choose(planId: Long) {
    val prompt = _prompt.value ?: return
    _prompt.value = null
    if (planId == prompt.linkedPlanId) return
    write("Failed to relink track to plan") { planRepository.linkTrack(planId, prompt.trackId, PlanTrackSource.START) }
  }

  /** 結び付けない（もう結び付けた計画から外す）。 */
  fun unlink() {
    val prompt = _prompt.value ?: return
    _prompt.value = null
    write("Failed to unlink track from plan") { planRepository.unlinkTrack(prompt.linkedPlanId, prompt.trackId) }
  }

  /** 閉じる（結び付けたまま）。 */
  fun close() {
    _prompt.value = null
  }

  private fun write(failure: String, block: suspend () -> Unit) {
    viewModelScope.launch {
      try {
        block()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        logger.e(failure, e)
      }
    }
  }

  private companion object {
    const val TRACK_WAIT_MILLIS = 15_000L

    /** 押した時刻と経路の開始の時刻のずれ（端末の時計の読み方の差）を見込む幅。 */
    const val START_SLACK_MILLIS = 5_000L
  }
}

/**
 * 「今日の計画に結び付けました」。最初の計画を選んだ状態（もう結び付いている）で出し、選び直して「OK」、外すなら「結び付けない」。
 * 外を押して閉じても、結び付けたまま。
 */
@Composable
internal fun PlanLinkPromptDialog(prompt: PlanLinkPrompt, onChoose: (planId: Long) -> Unit, onUnlink: () -> Unit, onClose: () -> Unit) {
  var selected by rememberSaveable(prompt.trackId) { mutableLongStateOf(prompt.linkedPlanId) }
  AlertDialog(
    onDismissRequest = onClose,
    modifier = Modifier.testTag("planLinkPrompt"),
    title = { Text("今日の計画に結び付けました") },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
          text = "この記録を計画の実績にします。別の計画にするときは選び直してください。後から計画の詳細でも変えられます。",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(PathlySpacing.sm))
        prompt.plans.forEach { plan ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .selectable(selected = selected == plan.id, role = Role.RadioButton, onClick = { selected = plan.id })
              .testTag("planLinkPromptPlan:${plan.id}"),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = selected == plan.id, onClick = null)
            Spacer(modifier = Modifier.width(PathlySpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
              Text(text = plan.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(
                text = planWhen(plan, prompt.today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onChoose(selected) }, modifier = Modifier.testTag("planLinkPromptOk")) { Text("OK") }
    },
    dismissButton = { TextButton(onClick = onUnlink, modifier = Modifier.testTag("planLinkPromptUnlink")) { Text("結び付けない") } },
  )
}

/** 「11/30(日) 日帰り」「11/29(土)〜12/1(月)・2日目」。 */
private fun planWhen(plan: Plan, today: LocalDate): String {
  val start = plan.startDate ?: return dayCountLabel(plan.dayCount)
  if (plan.dayCount <= 1) return "${formatPlanDate(start, today)} 日帰り"
  val day = (today.toEpochDay() - start.toEpochDay() + 1).toInt()
  return "${formatPlanDate(start, today)}〜${formatPlanDate(plan.endDate!!, today)}・${day}日目"
}
