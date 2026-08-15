package com.pathly.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Google Places のカテゴリ（業種）のマスタ。`google_places` から参照する（docs/designs/places.md）。
 *
 * 以前は表示名（「カフェ」）を `google_places.category` に直接持っていたため、同じ業種の場所の数だけ
 * 同じ文字列が重複していた。また表示名はロケール依存の**表示用**の値で、これを手掛かりに
 * アイコンなどを出し分けると言語設定で壊れる。**機械可読な [code]** を正とし、表示名はその属性として
 * 1 行に集約する。
 *
 * 行は事前にシードせず、Places の応答で出会った業種だけを都度 upsert して育てる
 * （Google の型は数百あり、その大半はこのアプリでは一生出てこない）。
 *
 * @property code Google の primaryType（`cafe` / `park` / `restaurant` など）。機械可読でロケール非依存。
 * @property displayName Google の primaryTypeDisplayName（「カフェ」）。取れなければ null。表示専用。
 */
@Entity(
  tableName = "google_place_categories",
  // code が業種の同一性。id はサロゲートなので、重複を防ぐのはこちらの索引。
  indices = [Index(value = ["code"], unique = true)],
)
data class GooglePlaceCategoryEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val code: String,
  val displayName: String? = null,
)
