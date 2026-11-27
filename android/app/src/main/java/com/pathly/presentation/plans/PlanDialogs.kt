package com.pathly.presentation.plans

import android.widget.NumberPicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pathly.R
import com.pathly.domain.model.Plan
import com.pathly.domain.model.PlanArrangement
import com.pathly.domain.model.PlanItem
import com.pathly.domain.model.PlanSlot
import com.pathly.ui.theme.PathlySpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

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
      }, enabled = title.isNotBlank()) { Text("決める") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  )
}

/**
 * 日付と日数（⋮ の「日付と日数」・シートの要約）。開始日は「日付なし」を選べる。
 * 日付ありなら、開始・終了をそれぞれのカレンダーで選んでも、「期間で選ぶ」で始めと終わりを一度に選んでも、日数で決めてもよい
 * （どれも連動する。開始を変えると日数はそのまま、終了を変えると日数が変わる。日数の上限なし）。
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
  var pickerOpen by rememberSaveable { mutableStateOf<DatePickerTarget?>(null) }
  val days = daysText.toIntOrNull()?.takeIf { it >= 1 }
  val start = LocalDate.ofEpochDay(startEpochDay)
  val end = days?.let { start.plusDays(it - 1L) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("日付と日数") },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        ChoiceRow(text = "日付なし", selected = !dated, onClick = { dated = false }, tag = "planUndated")
        ChoiceRow(text = "日付あり", selected = dated, onClick = { dated = true }, tag = "planDated")
        if (dated) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = PathlySpacing.xl)) {
            OutlinedButton(onClick = { pickerOpen = DatePickerTarget.START }, modifier = Modifier.testTag("planStartDate")) { Text(formatPlanDate(start)) }
            Text(" 〜 ", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { pickerOpen = DatePickerTarget.END }, enabled = end != null, modifier = Modifier.testTag("planEndDate")) {
              Text(end?.let { formatPlanDate(it) } ?: "―")
            }
          }
          // 始めと終わりを 1 つのカレンダーで選ぶ（日帰りは同じ日を 2 回）。
          TextButton(
            onClick = { pickerOpen = DatePickerTarget.RANGE },
            modifier = Modifier
              .padding(start = PathlySpacing.xl)
              .testTag("planDateRange"),
          ) { Text("期間で選ぶ") }
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
      ) { Text("決める") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  )

  when (pickerOpen) {
    null -> Unit

    DatePickerTarget.START -> PlanDatePickerDialog(
      title = "開始の日",
      initial = start,
      earliest = null,
      onPick = {
        // 日数はそのまま（終了が一緒にずれる）。
        startEpochDay = it.toEpochDay()
        pickerOpen = null
      },
      onDismiss = { pickerOpen = null },
    )

    DatePickerTarget.END -> PlanDatePickerDialog(
      title = "終了の日",
      initial = end ?: start,
      earliest = start,
      onPick = {
        daysText = (ChronoUnit.DAYS.between(start, it) + 1).toString()
        pickerOpen = null
      },
      onDismiss = { pickerOpen = null },
    )

    DatePickerTarget.RANGE -> PlanDateRangeDialog(
      start = start,
      end = end ?: start,
      onPick = { from, to ->
        startEpochDay = from.toEpochDay()
        daysText = (ChronoUnit.DAYS.between(from, to) + 1).toString()
        pickerOpen = null
      },
      onDismiss = { pickerOpen = null },
    )
  }
}

private enum class DatePickerTarget { START, END, RANGE }

// DatePicker は UTC の 0 時のミリ秒で日付を持つ。
private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.utcLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** 1 日を選ぶカレンダー。[earliest] より前は選べない。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDatePickerDialog(title: String, initial: LocalDate, earliest: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
  val pickerState = rememberDatePickerState(
    initialSelectedDateMillis = initial.utcMillis(),
    selectableDates = object : SelectableDates {
      override fun isSelectableDate(utcTimeMillis: Long): Boolean = earliest == null || utcTimeMillis >= earliest.utcMillis()
    },
  )
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = { pickerState.selectedDateMillis?.let { onPick(it.utcLocalDate()) } },
        enabled = pickerState.selectedDateMillis != null,
        modifier = Modifier.testTag("planDatePickerConfirm"),
      ) { Text("決める") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  ) {
    DatePicker(
      state = pickerState,
      title = { Text(title, modifier = Modifier.padding(start = PathlySpacing.xl, top = PathlySpacing.md)) },
    )
  }
}

/** 始めと終わりを 1 つのカレンダーで選ぶ。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDateRangeDialog(start: LocalDate, end: LocalDate, onPick: (LocalDate, LocalDate) -> Unit, onDismiss: () -> Unit) {
  val pickerState = rememberDateRangePickerState(
    initialSelectedStartDateMillis = start.utcMillis(),
    initialSelectedEndDateMillis = end.utcMillis(),
  )
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = {
          val from = pickerState.selectedStartDateMillis?.utcLocalDate() ?: return@TextButton
          onPick(from, pickerState.selectedEndDateMillis?.utcLocalDate() ?: from)
        },
        enabled = pickerState.selectedStartDateMillis != null,
      ) { Text("決める") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  ) {
    DateRangePicker(
      state = pickerState,
      title = { Text("期間を選ぶ（始めと終わりを押す）", modifier = Modifier.padding(start = PathlySpacing.xl, top = PathlySpacing.md)) },
      modifier = Modifier.weight(1f),
    )
  }
}

/**
 * 行の編集・メモの行を足す。[item] が null なら新しいメモの行。場所の行では文言は出さない（名前は場所のもの）。
 * 場所の行は、滞在の目安を時間と分で入れる。着く時刻があれば出発の時刻も出し、出発を選ぶと滞在の目安が変わる。
 */
@Composable
internal fun PlanItemDialog(
  item: PlanItem?,
  onSave: (title: String?, plannedTime: Int?, stayMinutes: Int?, note: String?) -> Unit,
  onDismiss: () -> Unit,
) {
  val isMemo = item == null || !item.isPlace
  var title by rememberSaveable { mutableStateOf(item?.title.orEmpty()) }
  var plannedTime by rememberSaveable { mutableStateOf(item?.plannedTime) }
  var hours by rememberSaveable { mutableIntStateOf(item?.stayMinutes?.let { (it / 60).coerceAtMost(MAX_STAY_HOURS) } ?: 0) }
  var minutes by rememberSaveable { mutableIntStateOf(item?.stayMinutes?.let { it % 60 } ?: 0) }
  var note by rememberSaveable { mutableStateOf(item?.note.orEmpty()) }
  var timeOpen by rememberSaveable { mutableStateOf<TimeTarget?>(null) }
  val stay = (hours * 60 + minutes).takeIf { it > 0 }
  val departure = if (plannedTime != null && stay != null) plannedTime!! + stay else null

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
          Text("着く時刻", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
          OutlinedButton(onClick = { timeOpen = TimeTarget.ARRIVAL }, modifier = Modifier.testTag("planTimeButton")) {
            Text(plannedTime?.let { formatClock(it) } ?: "決めない")
          }
          if (plannedTime != null) {
            TextButton(onClick = { plannedTime = null }) { Text("消す") }
          }
        }
        if (!isMemo) {
          Text("滞在の目安", style = MaterialTheme.typography.labelLarge)
          Row(verticalAlignment = Alignment.CenterVertically) {
            NumberWheel(value = hours, range = 0..MAX_STAY_HOURS, onValueChange = { hours = it }, tag = "planStayHours")
            Text(" 時間 ", style = MaterialTheme.typography.bodyLarge)
            NumberWheel(value = minutes, range = 0..59, onValueChange = { minutes = it }, tag = "planStayMinutes")
            Text(" 分", style = MaterialTheme.typography.bodyLarge)
          }
          // 出発の時刻（着く時刻 ＋ 滞在の目安）。選ぶと滞在の目安を出発に合わせて直す。
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("出発の時刻", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            OutlinedButton(
              onClick = { timeOpen = TimeTarget.DEPARTURE },
              enabled = plannedTime != null,
              modifier = Modifier.testTag("planDepartureButton"),
            ) {
              Text(departure?.let { formatClock(it) } ?: if (plannedTime == null) "着く時刻を先に" else "決めない")
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
      ) { Text(if (item == null) "足す" else "決める") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
  )

  when (timeOpen) {
    null -> Unit

    TimeTarget.ARRIVAL -> PlanTimeDialog(
      title = "着く時刻",
      initial = plannedTime ?: DEFAULT_TIME,
      onPick = {
        plannedTime = it
        timeOpen = null
      },
      onDismiss = { timeOpen = null },
    )

    TimeTarget.DEPARTURE -> PlanTimeDialog(
      title = "出発の時刻",
      initial = (departure ?: plannedTime ?: DEFAULT_TIME) % (24 * 60),
      onPick = { picked ->
        val arrival = plannedTime ?: return@PlanTimeDialog
        // 着く時刻より前を選んだら、翌日の出発とみなす。同じ時刻なら滞在の目安を消す。
        val diff = (picked - arrival).let { if (it < 0) it + 24 * 60 else it }
        hours = (diff / 60).coerceAtMost(MAX_STAY_HOURS)
        minutes = diff % 60
        timeOpen = null
      },
      onDismiss = { timeOpen = null },
    )
  }
}

private enum class TimeTarget { ARRIVAL, DEPARTURE }

/**
 * 数を選ぶ回転式の欄（滞在の目安の時間・分。Android の NumberPicker）。上下にはじいて選ぶほか、真ん中を押せば数字で打てる。
 */
@Composable
private fun NumberWheel(value: Int, range: IntRange, onValueChange: (Int) -> Unit, tag: String) {
  val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
  AndroidView(
    factory = { context ->
      NumberPicker(context).apply {
        minValue = range.first
        maxValue = range.last
        wrapSelectorWheel = true
        setOnValueChangedListener { _, _, new -> onValueChange(new) }
      }
    },
    update = { picker ->
      picker.textColor = textColor
      if (picker.value != value) picker.value = value
    },
    modifier = Modifier
      .width(64.dp)
      .testTag(tag),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanTimeDialog(title: String, initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
  val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
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
private const val MAX_STAY_HOURS = 99
