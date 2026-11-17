package com.pathly.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Pathly の色のトークン（→ docs/specs/screens.md の「デザイントークン」・adr/0040）。
// 方向性は「藍」: 主役は落ち着いた青、脇役（secondary）は青灰。行った・動いているの緑は Material 3 の役割に無いので
// PathlyStatusColors に分ける。経路の橙は地図の色（PathlyMapColors）が担う。
// Design System（claude.ai の Artifact）と値を揃える。変えるときは両方と screens.md を直す。

/** 明るいテーマ。地は青みに寄せた白、文字は青みの墨。 */
internal val PathlyLightColorScheme = lightColorScheme(
  // 主役（記録の開始・停止を除く主ボタン・選択中のタブ・強調の文字）
  primary = Color(0xFF2F5BA8),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFDCE4F4),
  onPrimaryContainer = Color(0xFF14336B),
  inversePrimary = Color(0xFFAFC6FF),

  // 脇役。Material 3 の部品が「控えめな選択」に使う（下のタブの選択中の印・選択中の行・案内の帯・絞り込みのボタン）
  secondary = Color(0xFF565F71),
  onSecondary = Color(0xFFFFFFFF),
  secondaryContainer = Color(0xFFDAE2F9),
  onSecondaryContainer = Color(0xFF131C2B),

  // 経路に関わる控えめな強調（立ち寄りの操作の地など）。経路の橙を文字が読める濃さにしたもの
  tertiary = Color(0xFFA8501F),
  onTertiary = Color(0xFFFFFFFF),
  tertiaryContainer = Color(0xFFFADFCF),
  onTertiaryContainer = Color(0xFF4A1D05),

  error = Color(0xFFBA1A1A),
  onError = Color(0xFFFFFFFF),
  errorContainer = Color(0xFFFFDAD6),
  onErrorContainer = Color(0xFF410002),

  background = Color(0xFFF7F8FA),
  onBackground = Color(0xFF1C1F26),
  surface = Color(0xFFF7F8FA),
  onSurface = Color(0xFF1C1F26),
  surfaceVariant = Color(0xFFE1E4EB),
  onSurfaceVariant = Color(0xFF5B6170),
  surfaceTint = Color(0xFF2F5BA8),
  surfaceBright = Color(0xFFF7F8FA),
  surfaceDim = Color(0xFFD9DCE2),
  surfaceContainerLowest = Color(0xFFFFFFFF),
  surfaceContainerLow = Color(0xFFF2F4F7),
  surfaceContainer = Color(0xFFEEF0F4),
  surfaceContainerHigh = Color(0xFFE8EBF0),
  surfaceContainerHighest = Color(0xFFE2E5EB),
  inverseSurface = Color(0xFF2E3138),
  inverseOnSurface = Color(0xFFF0F1F4),

  outline = Color(0xFF747A88),
  outlineVariant = Color(0xFFC4C8D2),
  scrim = Color(0xFF000000),
)

/** 暗いテーマ。色相は明るいテーマと同じで、明るさを入れ替える。 */
internal val PathlyDarkColorScheme = darkColorScheme(
  primary = Color(0xFFAFC6FF),
  onPrimary = Color(0xFF0F2F66),
  primaryContainer = Color(0xFF23488F),
  onPrimaryContainer = Color(0xFFDCE4F4),
  inversePrimary = Color(0xFF2F5BA8),

  secondary = Color(0xFFBEC6DC),
  onSecondary = Color(0xFF283141),
  secondaryContainer = Color(0xFF3E4759),
  onSecondaryContainer = Color(0xFFDAE2F9),

  tertiary = Color(0xFFFFB68F),
  onTertiary = Color(0xFF5A2306),
  tertiaryContainer = Color(0xFF7E3A12),
  onTertiaryContainer = Color(0xFFFADFCF),

  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6),

  background = Color(0xFF121418),
  onBackground = Color(0xFFE3E5EA),
  surface = Color(0xFF121418),
  onSurface = Color(0xFFE3E5EA),
  surfaceVariant = Color(0xFF43474F),
  onSurfaceVariant = Color(0xFFC3C7D0),
  surfaceTint = Color(0xFFAFC6FF),
  surfaceBright = Color(0xFF383A40),
  surfaceDim = Color(0xFF121418),
  surfaceContainerLowest = Color(0xFF0D0F12),
  surfaceContainerLow = Color(0xFF1A1C21),
  surfaceContainer = Color(0xFF1E2025),
  surfaceContainerHigh = Color(0xFF282A30),
  surfaceContainerHighest = Color(0xFF33353B),
  inverseSurface = Color(0xFFE3E5EA),
  inverseOnSurface = Color(0xFF2E3138),

  outline = Color(0xFF8D919B),
  outlineVariant = Color(0xFF43474F),
  scrim = Color(0xFF000000),
)

/**
 * Material 3 の役割に無い「状態」の色。明るい・暗いで値を持ち、ダイナミックカラーでも変えない
 * （緑が「行った」を表すので、壁紙の色に置き換えると意味が消える）。`PathlyTheme.statusColors` で取る。
 *
 * @property visited 行った・動いている（訪問済み・記録中）の文字とアイコン。地図の訪問済みの場所と同じ系統の緑。
 */
@Immutable
data class PathlyStatusColors(val visited: Color)

internal val PathlyLightStatusColors = PathlyStatusColors(visited = Color(0xFF2E7D32))
internal val PathlyDarkStatusColors = PathlyStatusColors(visited = Color(0xFF8FD49A))

/**
 * 地図に描くものの色。**テーマ（明暗・ダイナミックカラー）で変えない。** 地図の地は明暗で変わらず、
 * 色が「状態」を表すので、どの端末・どの設定でも同じに見える必要がある。
 *
 * マーカーは形で「確定しているか、これから決めるものか」を表し、色は状態を担当する（→ adr/0018）。
 * 丸バッジ＝確定（開始・終了・現在地・立ち寄り・登録済みの場所）／ピン＝これから決めるもの。
 */
object PathlyMapColors {
  // ---- 経路 ----

  /** 経路（軌跡）の線。 */
  val Route = Color(0xFFFF6B35)

  /**
   * 記録が途切れた区間の破線。**軌跡と同じ濃さの灰色**にする。薄くすると地図に埋もれ、オレンジのままでは
   * 縮尺を小さくしたときに区別が付かない。前後をオレンジの実線が挟むので、同じ経路の一部であることは形で分かる。
   */
  val RouteGap = Color(0xFF666666)

  /** 確定した立ち寄りの滞在区間（濃い青の帯）。軌跡の下に太く敷く。 */
  val StopSegment = Color(0xF00D47A1)

  /** 立ち寄り中（ライブ）の滞在区間。確定分（青）と見分けるティール。 */
  val CurrentStopSegment = Color(0xF000BFA5)

  /**
   * 補正の調整モードで比較用に重ねる生データ。**灰色は使えない**（欠落の破線と同じ色になる → adr/0022）。
   * 半透明の紫にして、補正後（橙）とも欠落（灰）とも分ける。
   */
  val RawTrack = Color(0x665E35B1)

  /** 手動追加で選んだ滞在区間のハイライト。軌跡（橙）・立ち寄り（紫）と見分ける青。 */
  val ManualHighlight = Color(0xFF1E88E5)

  /**
   * 地図に重ねる過去の経路（8 色を順に使う）。記録中の経路（橙）・立ち寄りの帯（濃い青・ティール）と
   * 見分けやすく、地図の地の色に埋もれないもの。
   */
  val Overlay = listOf(
    Color(0xFF1E88E5), // 青
    Color(0xFF43A047), // 緑
    Color(0xFF8E24AA), // 紫
    Color(0xFFD81B60), // 赤紫
    Color(0xFF6D4C41), // 茶
    Color(0xFF00897B), // 青緑
    Color(0xFF9E9D24), // 黄緑
    Color(0xFF37474F), // 墨
  )

  // ---- 丸バッジ（確定したもの） ----

  /** 出発。 */
  val Start = Color(0xFF2E9E5B)

  /** 終了。 */
  val End = Color(0xFFD24B45)

  /** 記録中の現在地。 */
  val Current = Color(0xFF3B82F6)

  /** 立ち寄り（番号）。 */
  val Stop = Color(0xFF6A4BBC)

  /** 立ち寄り中。 */
  val CurrentStop = Color(0xFF00BFA5)

  /** 登録済みの場所（訪問済み）。グリフで業種を示す。 */
  val PlaceVisited = Color(0xFF2E7D32)

  /** 登録済みの場所（未訪問）。 */
  val PlaceUnvisited = Color(0xFF6B6B6B)

  /** 「行きたい」の小さな旗のバッジ。訪問状態（緑／灰）とは別の軸なので色も別に取る。 */
  val Wishlist = Color(0xFFE08A00)

  /** 近くの場所をまとめた数のバッジ。訪問状態の緑・灰と被らない濃い色。 */
  val Cluster = Color(0xFF263238)

  /** 地域の目印。経路（青）・立ち寄り（紫）・登録済みの場所（緑・灰）・手動の地点（青）と見分ける藍。 */
  val Region = Color(0xFF3949AB)

  // ---- ピン（これから決めるもの） ----

  /** 再解析の候補（提案）。 */
  val Candidate = Color(0xFFF09A2B)

  /** 自分で指した地点・登録前に選んだ地点。 */
  val Picked = Color(0xFF2E86DE)

  /** バッジ・ピンの縁取りと、その上のグリフ・数字。 */
  val OnMarker = Color(0xFFFFFFFF)
}
