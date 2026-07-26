package com.pathly.domain.repository

import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.Priority
import kotlinx.coroutines.flow.Flow

/**
 * 「場所」タブのデータを担う。全ての場所（places）を一覧し、各場所の「行きたい」登録
 * （wishlist）を付け外し・編集する。詳細は docs/designs/wishlist.md を参照。
 */
interface WishlistRepository {

  /** 全ての場所を、行きたい登録（あれば）付きでリアクティブに取得する。 */
  fun getPlaces(): Flow<List<PlaceListItem>>

  /**
   * 座標から場所を登録する（地図タップ）。find-or-create（30m）で場所を同定し、
   * [name] が非空で場所が未命名なら名前も設定する。行きたい登録はしない。返り値は place の id。
   */
  suspend fun registerPlace(latitude: Double, longitude: Double, name: String?): Long

  /** その場所を「行きたい」に登録する。既に登録済みなら既存の wishlist id を返す。 */
  suspend fun addToWishlist(placeId: Long, priority: Priority, memo: String?): Long

  /** 行きたいの優先度・メモを更新する。 */
  suspend fun updateWishlist(id: Long, priority: Priority, memo: String?)

  /** 訪問済み/未訪問を切り替える。 */
  suspend fun setVisited(id: Long, visited: Boolean)

  /** 「行きたい」から外す（wishlist 行のみ削除。場所 place は残す）。 */
  suspend fun removeFromWishlist(id: Long)
}
