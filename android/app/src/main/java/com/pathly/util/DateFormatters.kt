package com.pathly.util

import java.text.SimpleDateFormat
import java.util.Locale

object DateFormatters {
  val TIME_FORMAT: SimpleDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
  val SHORT_TIME_FORMAT: SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
  val DATE_FORMAT: SimpleDateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
  val SHORT_DATE_FORMAT: SimpleDateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
  val DATETIME_FORMAT: SimpleDateFormat =
    SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
}