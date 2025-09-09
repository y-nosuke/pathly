package com.pathly.util

import android.util.Log
import com.pathly.BuildConfig

object Logger {
  const val TAG_PREFIX = "Pathly"

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

  fun e(tag: String, throwable: Throwable) {
    Log.e("$TAG_PREFIX-$tag", throwable.message ?: "Unknown error", throwable)
  }

  // 詳細デバッグ用（デバッグビルドでのみ表示）
  fun verbose(tag: String, message: String) {
    if (BuildConfig.DEBUG) {
      Log.v("$TAG_PREFIX-$tag", message)
    }
  }
}

// 従来のクラススタイルloggerとの互換性のために追加
class InstanceLogger(private val tag: String) {
  fun d(message: String) {
    Logger.d(tag, message)
  }

  fun i(message: String) {
    Logger.i(tag, message)
  }

  fun w(message: String) {
    Logger.w(tag, message)
  }

  fun w(message: String, throwable: Throwable) {
    Log.w("${Logger.TAG_PREFIX}-$tag", message, throwable)
  }

  fun e(message: String) {
    Logger.e(tag, message)
  }

  fun e(message: String, throwable: Throwable) {
    Logger.e(tag, message, throwable)
  }
}

// 互換性のためのファクトリ関数
fun Logger(tag: String): InstanceLogger = InstanceLogger(tag)
