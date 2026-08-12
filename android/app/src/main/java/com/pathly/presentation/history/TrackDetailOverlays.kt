package com.pathly.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pathly.domain.model.StopCandidate
import com.pathly.presentation.common.StopRangeEditor
import com.pathly.util.DateFormatters
import java.util.Date

// 地図の上に重ねる下部オーバーレイ群（再解析の候補選択・手動追加の案内と入力）。
// いずれも高さを固定し、地図を常に見せたまま操作できるようにしている。

/**
 * 再解析の候補（一覧に無い立ち寄り）を選択して追加する**下部オーバーレイ**。
 * 高さを [height] に固定し、地図の上に重ねる（地図は常に見えたまま）。リストは
 * このカード内で独立スクロールするので、全画面にしなくても下の候補まで届く。
 * 行タップ（[onFocus]）で地図をその候補へ寄せ、名前＋位置で確かめてから選べる。
 */
@Composable
internal fun CandidateOverlay(
  candidates: List<StopCandidate>,
  selectedIndices: List<Int>,
  height: Dp,
  onToggle: (Int) -> Unit,
  onFocus: (StopCandidate) -> Unit,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .height(height),
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 8.dp,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // 固定ヘッダー: 説明と操作。スクロールから外して常に押せるようにする。
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = "一覧に無い立ち寄り ${candidates.size}件",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "オレンジのピンが候補です。行をタップすると地図が寄ります。追加するものを選んでください。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      // 候補リスト（このカード内でスクロール）。
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp),
      ) {
        candidates.forEachIndexed { index, candidate ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onFocus(candidate) }
              .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(checked = index in selectedIndices, onCheckedChange = { onToggle(index) })
            Column(modifier = Modifier.padding(start = 4.dp)) {
              Text(
                text = candidate.name ?: "名称未取得",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
              )
              Text(
                text = "${DateFormatters.time(candidate.detected.arrivalTime)} – " +
                  "${DateFormatters.time(candidate.detected.departureTime)} ・ 滞在${candidate.detected.durationMinutes}分",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
      // 固定フッター: キャンセル／追加。
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("キャンセル") }
        Button(
          onClick = onConfirm,
          enabled = selectedIndices.isNotEmpty(),
          modifier = Modifier.weight(1f),
        ) { Text("追加（${selectedIndices.size}件）") }
      }
    }
  }
}

/** 手動追加モードで地点を指す前に出す案内バー（下部・細め）。 */
@Composable
internal fun ManualPickPrompt(
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(16.dp),
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 8.dp,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "立ち寄った地点を地図でタップ（施設はタップで名前も入ります）",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = onCancel) { Text("キャンセル") }
    }
  }
}

/**
 * 手動追加で指した地点を立ち寄りとして登録する**下部オーバーレイ**。名前と、滞在した区間
 * （到着〜出発）を調整する。区間は地図に青くハイライトされ、経路のどこに対応するかが見える。
 * 調整は**スライダー（粗）＋ ＋/−（1点ずつの微調整）**の2段構え。長い経路でも精密に合わせられる。
 * 高さを [height] に固定し、地図は上に見えたまま。キャンセル/追加は下部に常に見える。
 */
@Composable
internal fun ManualAddOverlay(
  name: String,
  onNameChange: (String) -> Unit,
  arrivalTime: Date,
  departureTime: Date,
  arrivalIdx: Int,
  departureIdx: Int,
  lastIdx: Int,
  height: Dp,
  onRangeChange: (start: Int, end: Int) -> Unit,
  onConfirm: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  // 非nullなら登録済みの場所へ紐付けるモード（名前欄の代わりに場所名を表示・新規placeは作らない）。
  linkedPlaceName: String? = null,
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .height(height),
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    color = MaterialTheme.colorScheme.surface,
    shadowElevation = 8.dp,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // 本文はスクロール（小さい画面でもボタンが隠れないよう、フッターは下に固定する）。
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = if (linkedPlaceName != null) "この場所に立ち寄りを追加" else "手動で立ち寄りを追加",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        if (linkedPlaceName != null) {
          // 登録済みの場所へ紐付けるので名前は編集しない（既存placeを使う）。
          Text(
            text = linkedPlaceName,
            style = MaterialTheme.typography.bodyLarge,
          )
          Text(
            text = "登録済みの場所に紐付けます（新しい場所は作りません）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("名前（任意）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        StopRangeEditor(
          arrivalTime = arrivalTime,
          departureTime = departureTime,
          arrivalIdx = arrivalIdx,
          departureIdx = departureIdx,
          lastIdx = lastIdx,
          onRangeChange = onRangeChange,
        )
      }
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("キャンセル") }
        Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("追加") }
      }
    }
  }
}
