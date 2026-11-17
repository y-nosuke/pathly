package com.pathly.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 文字のトークン（→ docs/specs/screens.md の「デザイントークン」）。
// 書体は端末の標準（日本語は Noto Sans CJK）。フォントを同梱しないのは、APK を軽く保ち、
// 端末の文字の大きさの設定にそのまま従うため。大きさは Material 3 の段に合わせ、日本語が詰まって
// 見えないよう本文の行の高さを少し広げる。太さは 3 つ（標準・中・太）だけを使う。

private val Base = TextStyle(fontFamily = FontFamily.Default)

val Typography = Typography(
  // 画面の見出し（一覧の画面の「場所」「履歴」など）
  headlineMedium = Base.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
  headlineSmall = Base.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
  // シート・詳細の見出し（施設名・経路名）
  titleLarge = Base.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
  // 節の見出し・行の主な文字
  titleMedium = Base.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
  titleSmall = Base.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
  // 本文
  bodyLarge = Base.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.3.sp),
  bodyMedium = Base.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp),
  // 補足（住所・件数・時刻）
  bodySmall = Base.copy(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp),
  // ボタン・チップ・タブの文字
  labelLarge = Base.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
  labelMedium = Base.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
  labelSmall = Base.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
)
