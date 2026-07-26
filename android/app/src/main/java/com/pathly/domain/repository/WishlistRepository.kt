package com.pathly.domain.repository

import com.pathly.domain.model.Priority
import com.pathly.domain.model.WishlistItem
import kotlinx.coroutines.flow.Flow

/**
 * 行きたい場所（wishlist）の永続化を担う。既存の場所（places）を再利用する。
 * 詳細は docs/designs/wishlist.md を参照。
 */
interface WishlistRepository {

  /** 行きたい一覧（場所つき）をリアクティブに取得する（優先度順→登録日順）。 */
  fun getWishlist(): Flow<List<WishlistItem>>

  /**
   * 座標から追加する（地図タップ）。find-or-create（30m）で場所を同定し、行きたい行を作る。
   * [name] が非空で場所が未命名なら名前も設定する。既に行きたい済みなら既存の id を返す。
   */
  suspend fun addByCoordinate(
    latitude: Double,
    longitude: Double,
    name: String?,
    priority: Priority,
    memo: String?,
  ): Long

  /** 既存 place（立ち寄り等）を行きたいに追加する。既に済みなら既存の id を返す。 */
  suspend fun addFromPlace(placeId: Long, priority: Priority, memo: String?): Long

  /** 優先度・メモを更新する。 */
  suspend fun updateWishlist(id: Long, priority: Priority, memo: String?)

  /** 訪問済み/未訪問を切り替える。 */
  suspend fun setVisited(id: Long, visited: Boolean)

  /** 行きたい行のみ削除する（場所 place は残す）。 */
  suspend fun remove(id: Long)
}
