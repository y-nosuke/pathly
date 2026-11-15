package com.pathly.domain.usecase

import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.repository.PlaceRepository
import com.pathly.domain.repository.WishlistRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
  private val placeRepository: PlaceRepository,
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
   * - 近くの既存のうち、地図に見えているもの（[isShownOnMap] が true）は、ユーザーがその目で見たうえで
   *   空き地点をタップしているので確認しない
   * - 見えていないもの（表示 OFF、または地図の絞り込みで隠れている）が近くにあれば、最寄りのそれを
   *   [RegisterResult.NearbyFound] で返して判断を委ねる（同じ場所の重複登録を防ぐ）
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
    visited: Boolean,
    memo: String?,
    googlePlaceId: String?,
    isShownOnMap: (RegisteredPlace) -> Boolean,
    knownDetails: PlaceSearchResult? = null,
    googleName: String? = null,
  ): RegisterResult {
    if (googlePlaceId != null) {
      // 施設同定に任せる（隣接する別施設に相乗りしない）。
      return register(
        latitude,
        longitude,
        name,
        wishlist,
        priority,
        visited,
        memo,
        googlePlaceId,
        forceNewPlace = false,
        knownDetails = knownDetails,
        googleName = googleName,
      )
    }
    wishlistRepository.findNearbyPlaces(latitude, longitude)
      .firstOrNull { !isShownOnMap(it) }
      ?.let { return RegisterResult.NearbyFound(it) }
    return register(latitude, longitude, name, wishlist, priority, visited, memo, googlePlaceId = null, forceNewPlace = true)
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
    visited: Boolean,
    memo: String?,
    googlePlaceId: String?,
    forceNewPlace: Boolean,
    knownDetails: PlaceSearchResult? = null,
    googleName: String? = null,
  ): RegisterResult.Registered {
    val registration =
      wishlistRepository.registerPlace(latitude, longitude, name, memo, googlePlaceId, forceNewPlace, knownDetails, googleName)
    if (wishlist) wishlistRepository.addToWishlist(registration.placeId, priority)
    // 行きたいとは独立の軸。「前に行ったことがある場所」を登録と同時に訪問済みにできる。
    if (visited) wishlistRepository.setVisited(registration.placeId, true)
    return RegisterResult.Registered(registration.placeId, registration.alreadyExisted)
  }

  /** 近接確認で「この場所に紐付け」を選んだとき。既存の場所に行きたい／訪問済み／メモを反映する（新規は作らない）。 */
  suspend fun linkToExisting(placeId: Long, wishlist: Boolean, priority: Priority, visited: Boolean, memo: String?) {
    if (!memo.isNullOrBlank()) wishlistRepository.updatePlaceNote(placeId, memo)
    if (wishlist) wishlistRepository.addToWishlist(placeId, priority)
    if (visited) wishlistRepository.setVisited(placeId, true)
  }

  /**
   * 場所の編集内容を差分適用する（名前・メモ・行きたい・優先度・訪問済み）。
   *
   * 訪問済みは**行きたいとは独立**に付け外しする（adr/0020）。行きたいを外しても印は残る。
   * 手動の印は立ち寄り記録が無いとき（visitCount == 0）だけ意味を持つ（記録があればそれ自体で
   * 訪問済みなので、UI でも切替を出さない）。
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
  ): SaveResult {
    // 同じ施設を他の場所が既に持っていると紐付けは断られる（→ adr/0025）。名前・メモなど
    // 他の編集はそのまま保存し、断られたことだけ呼び出し側へ返す。
    var linkRefused = false
    if (link != null) {
      linkRefused = !wishlistRepository.linkPlaceToGoogle(item.place.id, link)
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
      // 新たに「行きたい」へ。
      wishlist && wishlistId == null -> wishlistRepository.addToWishlist(item.place.id, priority)

      // 既に「行きたい」。優先度の変更分だけ反映する。
      wishlist && wishlistId != null -> {
        if (priority != item.priority) wishlistRepository.updateWishlist(wishlistId, priority)
      }

      // 「行きたい」を外す（場所自体も訪問済みの印も残す）。
      !wishlist && wishlistId != null -> wishlistRepository.removeFromWishlist(wishlistId)
    }
    // 訪問済みは行きたいの有無に関わらず、変更分だけ反映する。
    if (item.visitCount == 0 && visited != item.isManuallyVisited) {
      wishlistRepository.setVisited(item.place.id, visited)
    }
    return SaveResult(linkRefused = linkRefused)
  }

  /**
   * 「行きたい」をワンタップで付け外しする（場所タブの一覧の旗・経路詳細の立ち寄りの旗）。
   *
   * 今の状態は**画面が持っている値ではなく DB から読み直して**決める。経路詳細では同じ場所への
   * 立ち寄りが何行もあり、画面の値が古いまま押されると、付けたつもりで外す（逆も）ことになるため。
   * 付けるときの優先度は「中」（場所タブの一覧の旗と同じ）。訪問済みの印には触らない（→ adr/0020）。
   *
   * @return 付け外しのあとに行きたいに入っているか。場所が無ければ null（何もしない）。
   */
  suspend fun toggleWishlist(placeId: Long): Boolean? = wishlistToggleMutex.withLock {
    // 読んでから書くまでを直列にする。旗を素早く 2 回押すと、どちらも「未登録」を読んで
    // 2 回入れようとし、wishlist.placeId の一意制約で 2 回目が失敗するため。
    val item = wishlistRepository.getPlace(placeId) ?: return@withLock null
    val wishlistId = item.wishlistId
    if (wishlistId != null) {
      wishlistRepository.removeFromWishlist(wishlistId)
      false
    } else {
      wishlistRepository.addToWishlist(placeId, Priority.MEDIUM)
      true
    }
  }

  private val wishlistToggleMutex = Mutex()

  /**
   * 同じ施設と分かった場所を、相手（その施設を持っている方）にまとめる（→ adr/0037）。
   * 取り消しは [undoMerge]。まとめられたら true。
   */
  suspend fun mergeIntoSameFacility(placeId: Long, otherPlaceId: Long): Boolean = placeRepository.mergePlaces(placeId, otherPlaceId)

  /** 直近のまとめを取り消す（スナックバーの「取り消す」）。 */
  suspend fun undoMerge(): Boolean = placeRepository.undoLastPlaceMerge()

  /** 同じ施設の相手がいても、施設情報なしで別の場所として残す（二度と聞かない → adr/0037）。 */
  suspend fun keepAsSeparatePlace(placeId: Long) = placeRepository.keepAsSeparatePlace(placeId)

  /** [saveEdits] の結果。保存そのものは常に行い、施設の紐付けだけが断られることがある。 */
  data class SaveResult(
    /** 同じ施設を他の場所が既に持っていたため、Google 施設の紐付けを行わなかった。 */
    val linkRefused: Boolean,
  )
}
