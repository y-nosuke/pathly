package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 行きたいの履歴。行きたいに入れて、実際に行った（入れた後に立ち寄りが記録された）1 回ぶん。
 *
 * 行ったら wishlist の行を消してここへ移す。1 place に何行あってもよく、件数が「行きたいで行った回数」になる。
 * 行は作るだけで書き換えない。元の立ち寄りは参照しない（立ち寄りを消しても履歴は残す → adr/0036）。
 */
@Entity(
  tableName = "wishlist_fulfillments",
  foreignKeys = [
    ForeignKey(
      entity = PlaceEntity::class,
      parentColumns = ["id"],
      childColumns = ["placeId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["placeId"])],
)
data class WishlistFulfillmentEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val placeId: Long,
  /** 行きたいだったときの優先度（0=低 / 1=中 / 2=高）。 */
  val priority: Int,
  /** 行きたいに入れた日時（wishlist.createdAt）。 */
  val wishedAt: Date,
  /** 行った日時（終わらせた立ち寄りの到着時刻）。 */
  val fulfilledAt: Date,
)
