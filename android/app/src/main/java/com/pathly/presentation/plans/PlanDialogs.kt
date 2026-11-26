package com.pathly.presentation.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pathly.R
import com.pathly.domain.model.Plan
import com.pathly.domain.model.PlanArrangement
import com.pathly.domain.model.PlanItem
import com.pathly.domain.model.PlanSlot
import com.pathly.ui.theme.PathlySpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** 名前とメモ（⋮ の「名前とメモ」）。 */
@Composable
internal fun PlanTitleDialog(plan: Plan, onSave: (title: String, note: String?) -> Unit, onDismiss: () -> Unit) {
  var title by rememberSaveable { mutableStateOf(plan.title) }
  var note by rememberSaveable { mutableStateOf(plan.note.orEmpty()) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("名前とメモ") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(PathlySpacing.sm)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("名前") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("planTitleField"),
        )
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text("メモ") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      TextButton(onClick = {
        onSave(title, note)
        onDismiss()
      }, enabled = title.isNotBlank()) { Text("保存") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  )
}

/**
 * 日付と日数（⋮ の「日付と日数」）。開始日は「日付なし」を選べる。日数は（−）（＋）と数字で入れる（上限なし）。
 * 日数を減らすと、消える日の行が最後の日の末尾へ移ることを添える。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanDatesDialog(
  plan: Plan,
  slots: List<PlanSlot>,
  onSave: (startDate: LocalDate?, dayCount: Int) -> Unit,
  onDismiss: () -> Unit,
) {
  var dated by rememberSaveable { mutableStateOf(plan.startDate != null) }
  var startEpochDay by rememberSaveable { mutableStateOf(plan.startDate?.toEpochDay() ?: LocalDate.now().toEpochDay()) }
  var daysText by rememberSaveable { mutableStateOf(plan.dayCount.toString()) }
  var pickerOpen by rememberSaveable { mutableStateOf(false) }
  val days = daysText.toIntOrNull()?.takeIf { it >= 1 }
  val start = LocalDate.ofEpochDay(startEpochDay)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("日付と日数") },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        ChoiceRow(text = "日付なし", selected = !dated, onClick = { dated = false }, tag = "planUndated")
        ChoiceRow(text = "日付あり", selected = dated, onClick = { dated = true }, tag = "planDated")
        if (dated) {
          OutlinedButton(
            onClick = { pickerOpen = true },
            modifier = Modifier
              .padding(start = 48.dp)
              .testTag("planStartDate"),
          ) { Text("開始日 ${formatPlanDate(start)}") }
        }
        Spacer(modifier = Modifier.padding(top = PathlySpacing.md))
        Text("日数", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = { daysText = ((days ?: 2) - 1).coerceAtLeast(1).toString() },
            enabled = (days ?: 1) > 1,
            modifier = Modifier.testTag("planDaysMinus"),
          ) { Text("−", style = MaterialTheme.typography.titleLarge) }
          OutlinedTextField(
            value = daysText,
            onValueChange = { value -> daysText = value.filter { it.isDigit() }.take(MAX_DAY_DIGITS) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = days == null,
            modifier = Modifier
              .width(88.dp)
              .testTag("planDaysField"),
          )
          IconButton(
            onClick = { daysText = ((days ?: 0) + 1).toString() },
            modifier = Modifier.testTag("planDaysPlus"),
          ) { Icon(painterResource(R.drawable.ic_add), contentDescription = "1 日増やす") }
          Text(if (days == 1) "（日帰り）" else "", style = MaterialTheme.typography.bodyMedium)
        }
        if (days != null && days > 1 && dated) {
          Text(
            text = "${formatPlanDate(start)}〜${formatPlanDate(start.plusDays(days - 1L))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        val moved = days?.let { PlanArrangement.movedByShrink(slots, it) } ?: 0
        if (moved > 0) {
          Text(
            text = "${days}日目より後の行 $moved 件は、${days}日目の末尾へ移ります",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
              .padding(top = PathlySpacing.sm)
              .testTag("planShrinkNote"),
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onSave(if (dated) start else null, days ?: return@TextButton)
          onDismiss()
        },
        enabled = days != null,
      ) { Text("保存") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  )

  if (pickerOpen) {
    // DatePicker は UTC の 0 時のミリ秒で日付を持つ。
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
      onDismissRequest = { pickerOpen = false },
      confirmButton = {
        TextButton(onClick = {
          pickerState.selectedDateMillis?.let { startEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
          pickerOpen = false
        }) { Text("決める") }
      },
      dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("やめる") } },
    ) {
      DatePicker(state = pickerState)
    }
  }
}

/**
 * 行の編集・メモの行を足す。[item] が null なら新しいメモの行。場所の行では文言は出さない（名前は場所のもの）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanItemDialog(
  item: PlanItem?,
  onSave: (title: String?, plannedTime: Int?, stayMinutes: Int?, note: String?) -> Unit,
  onDismiss: () -> Unit,
) {
  val isMemo = item == null || !item.isPlace
  var title by rememberSaveable { mutableStateOf(item?.title.orEmpty()) }
  var plannedTime by rememberSaveable { mutableStateOf(item?.plannedTime) }
  var stay by rememberSaveable { mutableStateOf(item?.stayMinutes) }
  var note by rememberSaveable { mutableStateOf(item?.note.orEmpty()) }
  var timeOpen by rememberSaveable { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = when {
          item == null -> "メモの行を足す"
          isMemo -> "メモの行を編集"
          else -> item.label
        },
        maxLines = 2,
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PathlySpacing.sm),
      ) {
        if (isMemo) {
          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("文言（「昼食」「新幹線で移動」など）") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("planMemoTitleField"),
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("予定時刻", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
          OutlinedButton(onClick = { timeOpen = true }, modifier = Modifier.testTag("planTimeButton")) {
            Text(plannedTime?.let { "${formatMinutesOfDay(it)} 着" } ?: "決めない")
          }
          if (plannedTime != null) {
            TextButton(onClick = { plannedTime = null }) { Text("消す") }
          }
        }
        if (!isMemo) {
          Text("滞在の目安", style = MaterialTheme.typography.labelLarge)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(PathlySpacing.xs)) {
            StayChoices.forEach { choice ->
              FilterChip(
                selected = stay == choice,
                onClick = { stay = choice },
                label = { Text(choice?.let { stayLabel(it) } ?: "なし") },
              )
            }
          }
        }
        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text("メモ") },
          minLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onSave(if (isMemo) title else null, plannedTime, if (isMemo) null else stay, note)
          onDismiss()
        },
        enabled = !isMemo || title.isNotBlank(),
        modifier = Modifier.testTag("planItemSave"),
      ) { Text(if (item == null) "足す" else "保存") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  )

  if (timeOpen) {
    PlanTimeDialog(
      initial = plannedTime ?: DEFAULT_TIME,
      onPick = {
        plannedTime = it
        timeOpen = false
      },
      onDismiss = { timeOpen = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanTimeDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
  val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("着く時刻") },
    text = { TimePicker(state = state) },
    confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("決める") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  )
}

/** 別の日へ移す（選んだ日の末尾へ）。日が多くてもスクロールで選べる。 */
@Composable
internal fun PlanMoveDayDialog(plan: Plan, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("別の日へ移す") },
    text = {
      LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
        items((1..plan.dayCount).toList()) { day ->
          ChoiceRow(
            text = dayLabel(plan, day),
            selected = day == current,
            onClick = {
              if (day != current) onPick(day)
              onDismiss()
            },
            tag = "planMoveDay:$day",
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  )
}

/** 1 つ選ぶ行（左に丸）。 */
@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit, tag: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag(tag),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = onClick)
    Text(text, style = MaterialTheme.typography.bodyLarge)
  }
}

private const val DEFAULT_TIME = 10 * 60
private const val MAX_DAY_DIGITS = 3
