package com.pathly.presentation.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.PointOfInterest
import com.pathly.R
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Priority

/**
 * 地図上の施設（POI）をタップしたときに出す共通の登録ダイアログ。
 * 記録画面・経路詳細・場所詳細のどのマップからでも使う（シートや入力欄と干渉しないようモーダル）。
 * 「追加」フローと項目を揃える（名前・メモ・行きたい・優先度）。メモは常時、優先度は行きたい ON のとき。
 */
@Composable
internal fun RegisterPlaceFromPoiDialog(
  poi: PointOfInterest,
  onDismiss: () -> Unit,
  onRegister: (name: String, wishlist: Boolean, priority: Priority, memo: String?) -> Unit,
  onFetchDetails: suspend (googlePlaceId: String) -> PlaceSearchResult? = { null },
) {
  var name by remember(poi) { mutableStateOf(poi.name) }
  var memo by remember(poi) { mutableStateOf("") }
  var wishlist by remember(poi) { mutableStateOf(false) }
  var priority by remember(poi) { mutableStateOf(Priority.MEDIUM) }
  val context = LocalContext.current

  // 開いたら Google から施設情報（カテゴリ・住所）を取得してプレビューする。
  // 結果は直近1件がキャッシュされ、登録を確定したときの取得で使い回される（二度叩かない）。
  var details by remember(poi) { mutableStateOf<PlaceSearchResult?>(null) }
  LaunchedEffect(poi) {
    poi.placeId?.let { details = onFetchDetails(it) }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("この場所を登録") },
    text = {
      Column {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("名前") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        // Google 由来のカテゴリ・住所（取得できたら）。「どんな場所か」の手がかり。
        details?.category?.takeIf { it.isNotBlank() }?.let { category ->
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = category,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        details?.address?.takeIf { it.isNotBlank() }?.let { address ->
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = address,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 登録前でも Google マップで施設ページ（写真・口コミ・営業時間）を確認できる。
        OutlinedButton(
          onClick = {
            openPlaceInGoogleMaps(
              context,
              poi.placeId,
              poi.latLng.latitude,
              poi.latLng.longitude,
              poi.name,
            )
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(painter = painterResource(R.drawable.ic_place), contentDescription = null)
          Text(text = " Google マップで開く")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = memo,
          onValueChange = { memo = it },
          label = { Text("メモ（任意）") },
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("行きたいに登録")
          Switch(checked = wishlist, onCheckedChange = { wishlist = it })
        }
        if (wishlist) {
          Spacer(modifier = Modifier.height(8.dp))
          PrioritySelector(selected = priority, onSelect = { priority = it })
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onRegister(name, wishlist, priority, memo.ifBlank { null }) }) { Text("登録") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}
