package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
  tableName = "gps_points",
  foreignKeys = [
    ForeignKey(
      entity = GpsTrackEntity::class,
      parentColumns = ["id"],
      childColumns = ["trackId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("trackId")],
)
data class GpsPointEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val trackId: Long,
  val latitude: Double,
  val longitude: Double,
  val altitude: Double? = null,
  /** 水平精度（メートル・68%信頼半径）。Location.accuracy。 */
  val accuracy: Float,
  val speed: Float? = null,
  val bearing: Float? = null,
  // --- 以下は Location が提供するが取り逃すと後から取れない付随情報（v9 で追加） ---
  /** 位置の供給元（gps / network / fused など）。Location.provider。 */
  val provider: String? = null,
  /** 鉛直（高度）精度（メートル）。hasVerticalAccuracy() のときのみ。 */
  val verticalAccuracyMeters: Float? = null,
  /** 速度精度（m/s）。hasSpeedAccuracy() のときのみ。 */
  val speedAccuracyMetersPerSecond: Float? = null,
  /** 方位精度（度）。hasBearingAccuracy() のときのみ。 */
  val bearingAccuracyDegrees: Float? = null,
  /** 平均海面（MSL）高度（メートル・API34+）。hasMslAltitude() のときのみ。 */
  val mslAltitudeMeters: Double? = null,
  /** MSL 高度精度（メートル・API34+）。hasMslAltitudeAccuracy() のときのみ。 */
  val mslAltitudeAccuracyMeters: Float? = null,
  /** 端末起動からの単調時刻（ナノ秒）。壁時計のズレに影響されない並べ替え・間隔計算用。 */
  val elapsedRealtimeNanos: Long = 0L,
  /** モック位置か（テスト・偽装位置の識別用）。Location.isMock。 */
  val isMock: Boolean = false,
  val timestamp: Date,
  val createdAt: Date = Date(),
)
