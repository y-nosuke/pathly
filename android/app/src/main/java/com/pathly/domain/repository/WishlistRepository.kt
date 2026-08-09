package com.pathly.domain.repository

import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlaceRegistration
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import kotlinx.coroutines.flow.Flow

/**
 * 「場所」タブのデータを担う。全ての場所（places）を一覧し、各場所の「行きたい」登録
 * （wishlist）を付け外し・編集する。詳細は docs/designs/wishlist.md を参照。
 */
interface WishlistRepository {

  /** 全ての場所を、行きたい登録（あれば）付きでリアクティブに取得する。 */
  fun getPlaces(): Flow<List<PlaceListItem>>

  /** 単一の場所を、行きたい登録付きで一回取得する（記録画面で既存 place をその場で編集するため）。無ければ null。 */
  suspend fun getPlace(placeId: Long): PlaceListItem?

  /** その場所を含むお出掛け（経路）の一覧。新しい順。訪問が無ければ空。 */
  fun getVisits(placeId: Long): Flow<List<PlaceVisit>>

  /**
   * Google の place ID から施設情報（名前・住所・カテゴリ）を取得する（POI 登録ダイアログの
   * プレビュー用）。結果は直近1件がキャッシュされ、続く登録時の取得で使い回される（二度叩かない）。
   * オフライン・失敗時は null。
   */
  suspend fun fetchPlaceDetails(googlePlaceId: String): PlaceSearchResult?

  /**
   * 座標から場所を登録する（地図タップ）。find-or-create（30m）で場所を同定し、
   * [name] が非空で場所が未命名なら名前（ユーザー名）を設定し、[note] があればメモも設定する。
   * [googlePlaceId]（POI タップ由来）があれば Google データ（カテゴリ・住所）を取得して
   * `google_places` に保存する（詳細でカテゴリ表示・Google マップで施設ページを開けるようにする）。
   * 行きたい登録はしない。返り値は place の id。
   */
  suspend fun registerPlace(
    latitude: Double,
    longitude: Double,
    name: String?,
    note: String? = null,
    googlePlaceId: String? = null,
    // true のとき座標30m同定をせず必ず新しい place を作る（近接確認で「新規」/ 表示ONでそのまま登録）。
    forceNewPlace: Boolean = false,
  ): PlaceRegistration

  /** 手動登録の近接確認用: 近く（検出半径 50m）に既存の場所があれば最寄り1件を返す（無ければ null）。 */
  suspend fun findNearbyPlace(latitude: Double, longitude: Double): RegisteredPlace?

  /**
   * キーワード検索の結果から場所を登録する。find-or-create（30m）で場所を同定し、
   * Google 由来の名前・住所・place ID を google_places に記録する（以後 Nearby を叩かない）。
   * 返り値は place の id。行きたい登録はしない（呼び出し側で任意に [addToWishlist]）。
   */
  suspend fun registerSearchedPlace(result: PlaceSearchResult): PlaceRegistration

  /**
   * 既存の場所を、検索で選んだ Google 施設に紐付ける（`googlePlaceId` を持たない場所の情報補完）。
   * `google_places`（名前・住所・カテゴリ・place ID）を上書き保存し、`place_resolutions` に解決記録を残し、
   * 暫定の座標を施設の正確な座標へ置き換える。places.name（ユーザー名）は触らない。
   * オフライン記録の未解決・手動登録で ID の無い場所を、確実に Google と結びつける手段。
   */
  suspend fun linkPlaceToGoogle(placeId: Long, result: PlaceSearchResult)

  /** 座標の近くの POI 候補を返す（場所詳細で「Googleで情報を取得」時の候補提示に使う）。 */
  suspend fun nearbyPois(latitude: Double, longitude: Double): List<PlaceSearchResult>

  /** 場所の名前（ユーザー名）を手動で設定・変更する（空文字なら未命名に戻す）。 */
  suspend fun renamePlace(placeId: Long, name: String)

  /** 場所のメモ（places.note）を更新する（空文字なら null に戻す）。 */
  suspend fun updatePlaceNote(placeId: Long, note: String?)

  /** その場所を「行きたい」に登録する。既に登録済みなら既存の wishlist id を返す。 */
  suspend fun addToWishlist(placeId: Long, priority: Priority): Long

  /** 行きたいの優先度を更新する。 */
  suspend fun updateWishlist(id: Long, priority: Priority)

  /** 訪問済み/未訪問を切り替える。 */
  suspend fun setVisited(id: Long, visited: Boolean)

  /** 「行きたい」から外す（wishlist 行のみ削除。場所 place は残す）。 */
  suspend fun removeFromWishlist(id: Long)

  /**
   * 場所そのものを削除する（行きたい登録・解決ログは CASCADE で一緒に消える）。
   * **立ち寄り（stops）がある場所は消さない**方針のため、呼び出し側で stops のある場所は
   * 削除させないこと（UI で非活性）。stops がある place を渡すと FK 制約で失敗する。
   * 取り消し（[undoLastPlaceDeletion]）用に、直近の削除内容を1件だけ控える。
   */
  suspend fun deletePlace(placeId: Long)

  /**
   * 直近の [deletePlace] を取り消し、消した場所・行きたい登録・解決ログを
   * **元のIDのまま**復元する。控えが無ければ何もしない。復元したら true を返す。
   */
  suspend fun undoLastPlaceDeletion(): Boolean
}
