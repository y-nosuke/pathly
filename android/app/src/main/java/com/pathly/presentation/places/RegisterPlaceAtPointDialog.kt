package com.pathly.presentation.places

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pathly.domain.model.Priority

/**
 * 施設(POI)ではない**任意の地点**を場所として登録するダイアログ（立ち寄りは作らない）。
 * 記録画面で何もない場所をタップしたときに使う。本体は [PlaceFormBody] を共有し、
 * POI 版（[RegisterPlaceFromPoiDialog]）と並びを揃える（Google 情報は無いのでカテゴリ/住所は出さない）。
 */
@Composable
internal fun RegisterPlaceAtPointDialog(
  onDismiss: () -> Unit,
  onRegister: (name: String, wishlist: Boolean, priority: Priority, memo: String?) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var memo by remember { mutableStateOf("") }
  var wishlist by remember { mutableStateOf(false) }
  var priority by remember { mutableStateOf(Priority.MEDIUM) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("この地点を場所として登録") },
    text = {
      PlaceFormBody(
        name = name,
        onNameChange = { name = it },
        nameLabel = "名前（任意）",
        category = null,
        address = null,
        onOpenInMaps = null,
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
