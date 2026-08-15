package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "gps_tracks")
data class GpsTrackEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val startTime: Date,
  val endTime: Date? = null,
  val isActive: Boolean = true,
  /** ユーザーが付けた経路名。null/空なら未命名（一覧では日付を見出しに使う）。 */
  val name: String? = null,
  /** お気に入り登録フラグ。 */
  val isFavorite: Boolean = false,
  /**
   * 補正後の点列で計算した総移動距離（メートル）。null = 未計算。
   *
   * 一覧の表示と距離順の並べ替えに使う。以前は一覧を描くたびに全経路の全点を
   * 読み込んで平滑化し直していた（経路が増えるほど重くなり、UIスレッドで走っていた）。
   * 記録の確定時に一度だけ計算してここへ焼き込み、一覧は点をロードしない。
   */
  val totalDistanceMeters: Double? = null,
  val createdAt: Date = Date(),
  val updatedAt: Date = Date(),
)
