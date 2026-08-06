package com.pathly.presentation.places

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.model.PointOfInterest
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Priority

/**
 * 地図上の施設（POI）をタップしたときに出す共通の登録ダイアログ。
 * 記録画面・経路詳細・場所詳細のどのマップからでも使う（シートや入力欄と干渉しないようモーダル）。
 * 本体は [PlaceFormBody] を共有し、追加・検索・編集と同じ並び（名前・カテゴリ/住所・メモ・行きたい・優先度）。
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
      PlaceFormBody(
        name = name,
        onNameChange = { name = it },
        nameLabel = "名前",
        category = details?.category,
        address = details?.address,
        onOpenInMaps = {
          openPlaceInGoogleMaps(context, poi.placeId, poi.latLng.latitude, poi.latLng.longitude, poi.name)
        },
        memo = memo,
        onMemoChange = { memo = it },
        wishlist = wishlist,
        onWishlistChange = { wishlist = it },
        priority = priority,
        onPriorityChange = { priority = it },
      )
    },
    confirmButton = {
      TextButton(onClick = { onRegister(name, wishlist, priority, memo.ifBlank { null }) }) { Text("登録") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}
