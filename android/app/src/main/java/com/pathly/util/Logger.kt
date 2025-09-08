package com.pathly.util

import android.util.Log
import com.pathly.BuildConfig

object Logger {
  private const val TAG_PREFIX = "Pathly"

  fun d(tag: String, message: String) {
    if (BuildConfig.DEBUG) {
      Log.d("$TAG_PREFIX-$tag", message)
    }
  }

  fun i(tag: String, message: String) {
    Log.i("$TAG_PREFIX-$tag", message)
  }

  fun w(tag: String, message: String) {
    Log.w("$TAG_PREFIX-$tag", message)
  }

  fun e(tag: String, message: String) {
    Log.e("$TAG_PREFIX-$tag", message)
  }

  fun e(tag: String, message: String, throwable: Throwable) {
    Log.e("$TAG_PREFIX-$tag", message, throwable)
  }

  // 詳細デバッグ用（デバッグビルドでのみ表示）
  fun verbose(tag: String, message: String) {
    if (BuildConfig.DEBUG) {
      Log.v("$TAG_PREFIX-$tag", message)
    }
  }
}