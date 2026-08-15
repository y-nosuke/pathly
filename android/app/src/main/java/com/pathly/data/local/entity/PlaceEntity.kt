package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 場所そのもの。経路とは独立して管理する（docs/designs/places-and-stops.md）。
 * ユーザー入力（自分で付けた名前・メモ）と座標だけを持つ。Google 由来の名前・住所・カテゴリは
 * google_places に分離する（docs/designs/place-info-enrichment.md / adr/0001）。
 */
@Entity(
  tableName = "places",
  // 近傍検索（同一場所の判定・近接確認）が全表走査にならないよう座標に索引を張る。
  // 記録中は位置バッチごとに引かれるため、場所が増えるほど効いてくる。
  indices = [Index(value = ["latitude", "longitude"])],
)
data class PlaceEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  /** 自分で付けた名前（null=未命名。Google 由来の名前は入れない）。 */
  val name: String? = null,
  val latitude: Double,
  val longitude: Double,
  /** 場所のメモ（「行きたい」登録と独立に持てる。null/空=メモ無し）。 */
  val note: String? = null,
  /**
   * 由来（[com.pathly.domain.model.PlaceSource] の名前）。"DETECTED"=自動検出 / "USER"=ユーザー登録。
   * 自動回収は "DETECTED" のみ対象（"USER" は意図的なので自動では消さない）。既定は安全側の "USER"。
   */
  val source: String = "USER",
  val createdAt: Date = Date(),
  val updatedAt: Date = Date(),
)
