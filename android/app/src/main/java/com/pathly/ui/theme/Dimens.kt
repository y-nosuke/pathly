package com.pathly.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// 余白・角丸・影のトークン（→ docs/specs/screens.md の「デザイントークン」）。
// 新しく作る部品はこれを使う。今の画面の余白の数値は、画面を型に移す Issue（#193〜#197）で置き換える。

/** 余白。4dp の段。部品の中は xs〜md、部品どうし・画面の縁は lg、節の区切りは xl。 */
object PathlySpacing {
  val xxs = 2.dp
  val xs = 4.dp
  val sm = 8.dp
  val md = 12.dp
  val lg = 16.dp
  val xl = 24.dp
  val xxl = 32.dp

  /** 一覧・シートの左右の余白。 */
  val screenEdge = lg

  /** 地図に浮かべる部品（検索窓・地図の操作）と画面の縁の間。 */
  val mapEdge = md
}

/** シートの角丸。`shapes.large` とシートの上端・下端で同じ値にする。 */
private val SheetRadius = 20.dp

/**
 * 角丸。MaterialTheme.shapes に入れ、部品は `MaterialTheme.shapes.medium` のように参照する。
 * 丸いもの（地図の丸ボタン・検索窓・ピル・マーカー）は CircleShape（[PathlyShapes.pill]）。
 */
val PathlyMaterialShapes = Shapes(
  // 小さな印・目盛り
  extraSmall = RoundedCornerShape(4.dp),
  // チップ・1 行の帯・小さなバッジ
  small = RoundedCornerShape(8.dp),
  // カード・案内・入力欄
  medium = RoundedCornerShape(12.dp),
  // 複数行になり得る浮かぶ案内・FAB（M3 の FAB はこれを使う）
  large = RoundedCornerShape(SheetRadius),
  // ダイアログ・モーダルのシート（M3 の AlertDialog・ModalBottomSheet はこれを使う）
  extraLarge = RoundedCornerShape(28.dp),
)

object PathlyShapes {
  /** 下から出るシートの上端。左右の上だけ丸める。 */
  val sheetTop = RoundedCornerShape(topStart = SheetRadius, topEnd = SheetRadius)

  /** 地図に重ねる上の見出し（一覧のタブの地図表示）の下端。シートの上端と対にする。 */
  val sheetBottom = RoundedCornerShape(bottomStart = SheetRadius, bottomEnd = SheetRadius)

  /** 地図の丸ボタン・検索窓・ピル。 */
  val pill = CircleShape
}

/** 影（shadowElevation）。地図の上に浮かぶものの高さを 3 段に決める。 */
object PathlyElevation {
  /** 地図に重ねる見出し（一覧のタブの地図表示）・小さな印。 */
  val low = 2.dp

  /** 地図に浮かぶ部品（検索窓・地図の操作・ピル・チップ）。 */
  val floating = 4.dp

  /** 下から出るシート。 */
  val sheet = 8.dp
}
