package com.pathly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Pathly のテーマ。色・文字・角丸のトークンを MaterialTheme に入れる（→ adr/0040）。
 *
 * @param dynamicColor 壁紙の色（ダイナミックカラー）を使うか。既定は使わず Pathly の色にする。
 *   設定の「壁紙の色を使う」で切り替える。使っても地図の色（[PathlyMapColors]）は変わらない。
 */
@Composable
fun PathlyAndroidTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    // minSdk 34 なので、ダイナミックカラー（Android 12+）は常に使える。
    dynamicColor -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    darkTheme -> PathlyDarkColorScheme

    else -> PathlyLightColorScheme
  }

  val statusColors = if (darkTheme) PathlyDarkStatusColors else PathlyLightStatusColors

  CompositionLocalProvider(LocalPathlyStatusColors provides statusColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = PathlyMaterialShapes,
      content = content,
    )
  }
}

private val LocalPathlyStatusColors = staticCompositionLocalOf { PathlyLightStatusColors }

/** MaterialTheme に無い Pathly のトークンの入口。 */
object PathlyTheme {
  /** 状態の色（行った・動いている）。 */
  val statusColors: PathlyStatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalPathlyStatusColors.current
}
