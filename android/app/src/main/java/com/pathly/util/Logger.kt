package com.pathly.util

import android.util.Log
import com.pathly.BuildConfig

/**
 * タグ付きのロガー。クラスごとに `private val logger = Logger("Xxx")` を持って使う。
 *
 * 以前は object・互換用クラス・object と同名のファクトリ関数の3層構造だったが、
 * 「従来のクラススタイル」との互換を保つべき呼び出し元はもう無かったので1つにまとめた。
 *
 * `d` はデバッグビルドでのみ出力する。位置情報アプリなので、**座標そのものはログに
 * 出さないこと**（リリースビルドでは R8 を有効にしていないため、出力すると logcat に残る）。
 */
class Logger(tag: String) {

  private val tag = "$TAG_PREFIX-$tag"

  /** 詳細ログ。デバッグビルドでのみ出力する。 */
  fun d(message: String) {
    if (BuildConfig.DEBUG) Log.d(tag, message)
  }

  fun i(message: String) {
    Log.i(tag, message)
  }

  fun w(message: String) {
    Log.w(tag, message)
  }

  fun w(message: String, throwable: Throwable) {
    Log.w(tag, message, throwable)
  }

  fun e(message: String) {
    Log.e(tag, message)
  }

  fun e(message: String, throwable: Throwable) {
    Log.e(tag, message, throwable)
  }

  private companion object {
    const val TAG_PREFIX = "Pathly"
  }
}
