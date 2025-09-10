package com.pathly.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = PathlyOrange,
  onPrimary = Color.White,
  primaryContainer = PathlyDarkOrange,
  onPrimaryContainer = Color.White,

  secondary = PathlyGreen,
  onSecondary = Color.White,
  secondaryContainer = PathlyDarkGreen,
  onSecondaryContainer = Color.White,

  tertiary = PathlyLightGreen,
  onTertiary = Color.Black,

  background = SurfaceWarmDark,
  onBackground = OnSurfaceWarmDark,
  surface = SurfaceWarmDark,
  onSurface = OnSurfaceWarmDark,
  surfaceVariant = SurfaceVariantWarmDark,
  onSurfaceVariant = OnSurfaceWarmDark,

  error = ErrorRed,
  onError = Color.White,
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColorScheme = lightColorScheme(
  primary = PathlyOrange,
  onPrimary = Color.White,
  primaryContainer = PathlyLightOrange,
  onPrimaryContainer = OnSurfaceWarm,

  secondary = PathlyGreen,
  onSecondary = Color.White,
  secondaryContainer = PathlyLightGreen,
  onSecondaryContainer = OnSurfaceWarm,

  tertiary = PathlyDarkGreen,
  onTertiary = Color.White,

  background = SurfaceWarm,
  onBackground = OnSurfaceWarm,
  surface = SurfaceWarm,
  onSurface = OnSurfaceWarm,
  surfaceVariant = SurfaceVariantWarm,
  onSurfaceVariant = OnSurfaceWarm,

  error = ErrorRed,
  onError = Color.White,
  errorContainer = Color(0xFFFFDAD6),
  onErrorContainer = Color(0xFF410002),
)

@Composable
fun PathlyAndroidTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content,
  )
}
