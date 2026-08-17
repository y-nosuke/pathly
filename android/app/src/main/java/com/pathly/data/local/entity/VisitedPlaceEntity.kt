package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 手動で「訪問済み」にした印。**行の存在＝訪問済み**で、1 place につき最大1件（placeId は UNIQUE）。
 *
 * 行きたい（wishlist）とは独立した別の軸なので、テーブルも分ける（adr/0020）。places に持たせない
 * のは、places を静的に保つため（adr/0013）。
 */
@Entity(
  tableName = "visited_places",
  foreignKeys = [
    ForeignKey(
      entity = PlaceEntity::class,
      parentColumns = ["id"],
      childColumns = ["placeId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["placeId"], unique = true)],
)
data class VisitedPlaceEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val placeId: Long,
  /**
   * 「訪問済み」にした日時。**実際に訪れた日時ではない**（実訪問の記録は stops で、
   * その到着時刻が実際の日時）。行は作るか消すかだけで、書き換えない。
   */
  val markedAt: Date = Date(),
)
