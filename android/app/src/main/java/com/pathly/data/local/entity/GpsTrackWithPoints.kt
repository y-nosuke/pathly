package com.pathly.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class GpsTrackWithPoints(
  @Embedded
  val track: GpsTrackEntity,

  @Relation(
    parentColumn = "id",
    entityColumn = "trackId",
  )
  val points: List<GpsPointEntity>,
)
