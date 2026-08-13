package com.pathly.domain.usecase

import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.repository.WishlistRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 場所の登録・紐付け・編集の手順をまとめる。
 *
 * 記録画面・経路詳細・場所タブの3つの ViewModel がそれぞれ同じ処理を持っていた
 * （`savePlaceEdits` は約35行がほぼそのまま3コピー、登録・紐付けも同様）。さらに
 * 「近くに既存の場所があれば確認する」という判断が Composable 側に書かれていて、
 * 画面ごとに挙動がずれる余地があった。ここに集約して1箇所で直せるようにする。
 *
 * 表示メッセージは画面ごとに出し方が違う（スナックバー／トークン付き通知）ので、
 * ここでは結果だけを返し、文言と表示は呼び出し側の ViewModel に任せる。
 */
@Singleton
class PlaceEditUseCase @Inject constructor(
  private val wishlistRepository: WishlistRepository,
) {

  /** 登録の結果。 */
  sealed interface RegisterResult {
    /** 登録できた（[alreadyExisted] が true なら既存の場所に相乗りした）。 */
    data class Registered(val placeId: Long, val alreadyExisted: Boolean) : RegisterResult

    /**
     * 近く（検出半径）に既存の場所が見つかったので、まだ登録していない。
     * 紐付けるか新規で作るかをユーザーに選ばせ、[linkToExisting] か [registerAsNew] を呼ぶ。
     */
    data class NearbyFound(val nearby: RegisteredPlace) : RegisterResult
  }

  /**
   * 地図タップからの場所登録。**近接確認の要否まで含めて判断する**。
   *
   * - POI（[googlePlaceId] あり）は施設の同一性で同定するので確認しない
   * - [nearbyAlreadyVisible] が true（登録済みの場所を地図に表示中）なら、ユーザーは
   *   近くの既存をその目で見たうえで空き地点をタップしているので確認しない
   * - それ以外は近くの既存を探し、あれば [RegisterResult.NearbyFound] を返して判断を委ねる
   *
   * この分岐はもともと TrackingScreen と TrackDetailScreen の Composable に同じものが
   * 書かれていた（ドメインの判断が UI 層にあり、画面ごとにずれる余地があった）。
   */
  suspend fun registerWithNearbyCheck(
    latitude: Double,
    longitude: Double,
    name: String?,
    wishlist: Boolean,
    priority: Priority,
    memo: String?,
    googlePlaceId: String?,
    nearbyAlreadyVisible: Boolean,
    knownDetails: PlaceSearchResult? = null,
  ): RegisterResult {
    if (googlePlaceId != null) {
      // 施設同定に任せる（隣接する別施設に相乗りしない）。
      return register(latitude, longitude, name, wishlist, priority, memo, googlePlaceId, forceNewPlace = false, knownDetails = knownDetails)
    }
    if (!nearbyAlreadyVisible) {
      wishlistRepository.findNearbyPlace(latitude, longitude)?.let { return RegisterResult.NearbyFound(it) }
    }
    return register(latitude, longitude, name, wishlist, priority, memo, googlePlaceId = null, forceNewPlace = true)
  }

  /**
   * 同定方法を呼び出し側が決めている場合の登録（近接確認はしない）。
   * [forceNewPlace] が true なら座標同定せず必ず新しい場所を作る。
   * [knownDetails] があれば施設情報の取得を省く（キーワード検索は取得済み）。
   */
  suspend fun register(
    latitude: Double,
    longitude: Double,
    name: String?,
    wishlist: Boolean,
    priority: Priority,
    memo: String?,
    googlePlaceId: String?,
    forceNewPlace: Boolean,
    knownDetails: PlaceSearchResult? = null,
  ): RegisterResult.Registered {
    val registration = wishlistRepository.registerPlace(latitude, longitude, name, memo, googlePlaceId, forceNewPlace, knownDetails)
    if (wishlist) wishlistRepository.addToWishlist(registration.placeId, priority)
    return RegisterResult.Registered(registration.placeId, registration.alreadyExisted)
  }

  /** 近接確認で「この場所に紐付け」を選んだとき。既存の場所に行きたい／メモを反映する（新規は作らない）。 */
  suspend fun linkToExisting(placeId: Long, wishlist: Boolean, priority: Priority, memo: String?) {
    if (!memo.isNullOrBlank()) wishlistRepository.updatePlaceNote(placeId, memo)
    if (wishlist) wishlistRepository.addToWishlist(placeId, priority)
  }

  /**
   * 場所の編集内容を差分適用する（名前・メモ・行きたい・優先度・訪問済み）。
   *
   * 「行きたい」を今つけた場合は [WishlistRepository.addToWishlist] が返す id で訪問済みも
   * 設定できるよう、ひと続きで処理する（個別に呼ぶと新規の wishlistId を掴めない）。
   * 訪問済みの手動設定は立ち寄り記録が無いとき（visitCount == 0）だけ意味を持つ。
   *
   * [link] が非 null なら、先に Google 施設への紐付け（座標・カテゴリ等の補完）も行う。
   */
  suspend fun saveEdits(
    item: PlaceListItem,
    name: String,
    note: String,
    wishlist: Boolean,
    priority: Priority,
    visited: Boolean,
    link: PlaceSearchResult? = null,
  ) {
    if (link != null) {
      wishlistRepository.linkPlaceToGoogle(item.place.id, link)
    }
    if (name.trim() != (item.place.name ?: "").trim()) {
      wishlistRepository.renamePlace(item.place.id, name.trim())
    }
    val newNote = note.ifBlank { null }
    if (newNote != item.note) {
      wishlistRepository.updatePlaceNote(item.place.id, newNote)
    }
    val wishlistId = item.wishlistId
    when {
      // 新たに「行きたい」へ。付けた直後の id で訪問済みも反映する。
      wishlist && wishlistId == null -> {
        val newId = wishlistRepository.addToWishlist(item.place.id, priority)
        if (visited && item.visitCount == 0) wishlistRepository.setVisited(newId, true)
      }
      // 既に「行きたい」。優先度・訪問済みの変更分だけ反映する。
      wishlist && wishlistId != null -> {
        if (priority != item.priority) wishlistRepository.updateWishlist(wishlistId, priority)
        if (item.visitCount == 0 && visited != item.isManuallyVisited) {
          wishlistRepository.setVisited(wishlistId, visited)
        }
      }
      // 「行きたい」を外す（場所自体は残す）。
      !wishlist && wishlistId != null -> {
        wishlistRepository.removeFromWishlist(wishlistId)
      }
    }
  }
}
