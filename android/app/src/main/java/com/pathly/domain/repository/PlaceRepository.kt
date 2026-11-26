package com.pathly.domain.repository

import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceSource
import com.pathly.domain.model.PlaceWithDistance
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.SameFacilityPending
import kotlinx.coroutines.flow.Flow

/**
 * 場所（places）の登録・同定・命名・まとめるを担う。
 * 命名は「記録中（自動）」「場所を取得ボタン」「オンライン復帰後のキャッチアップ」で行う。
 * 立ち寄りは [StopRepository]・[LiveStopRepository]。
 * 詳細は docs/designs/places-and-stops.md を参照。分け方は adr/0049。
 */
interface PlaceRepository {

  /** 地図の「登録済みの場所」表示用に、全 place（USER・DETECTED）を最小情報でリアクティブに取得する。 */
  fun observeRegisteredPlaces(): Flow<List<RegisteredPlace>>

  /** その経路の未取得（googlePlaceId 無し）の place 件数（「場所を取得」ボタン表示用）。 */
  fun unresolvedCountForTrack(trackId: Long): Flow<Int>

  /**
   * 手動追加の近接確認用: 座標の近く（検出半径 [com.pathly.domain.model.StopDetector.RADIUS_METERS]）の
   * 既存の場所を、近い順に全部返す（無ければ空）。地図に見えていない（表示 OFF・絞り込みで隠れた）ものが
   * 近接確認の対象になり、「近くに既存あり→紐付け/新規」を出す。
   */
  suspend fun findNearbyPlaces(latitude: Double, longitude: Double): List<RegisteredPlace>

  /**
   * 座標から [radiusMeters] 以内の既存の場所を、距離つきで近い順に返す（無ければ空）。
   * 手でまとめる相手の候補に使う（場所の編集の「ほかの場所とまとめる」）。
   */
  suspend fun findPlacesAround(latitude: Double, longitude: Double, radiusMeters: Double): List<PlaceWithDistance>

  /**
   * 座標の近くの POI 候補を複数返す（距離が近い順）。手動追加の「候補から選ぶ」に使う。
   * 最寄り1件だけの自動命名と違い、隣接する別施設の取り違えを避けてユーザーに選ばせる。
   * オフライン・失敗時は空リスト。
   */
  suspend fun nearbyPois(latitude: Double, longitude: Double): List<PlaceSearchResult>

  /**
   * その経路の未取得（googlePlaceId 無し）の place を Places で取り直す（手動「場所を取得」）。
   *
   * 返すのは「**同じ施設を既に他の場所が持っていたため、施設情報を付けなかった**」件数
   * （→ adr/0025）。押しても何も起きなかったように見えるので、呼び出し側で理由を伝えるために使う。
   */
  suspend fun resolveUnresolvedNames(trackId: Long): Int

  /**
   * ある経路の場所のうち、同じ施設の相手がいて、まとめるか別のまま残すかをまだ決めていないもの
   * （経路詳細の「確認待ち」 → adr/0037）。決めたり相手が消えたりすると外れる。
   */
  fun observeSameFacilityPending(trackId: Long): Flow<List<SameFacilityPending>>

  /**
   * [fromPlaceId] を [intoPlaceId] にまとめる（[fromPlaceId] は消える → adr/0037）。立ち寄りは残る方へ
   * 付け替え、名前・メモは残る方を優先（メモは両方あればつなげる）、行きたい・訪問済みの印はどちらかに
   * あれば付ける（優先度は高い方）。取り消し（[undoLastPlaceMerge]）用に、変更前の行を控える。
   *
   * @return まとめたら true。どちらかが無い・同じ場所なら何もせず false。
   */
  suspend fun mergePlaces(fromPlaceId: Long, intoPlaceId: Long): Boolean

  /** 直近の [mergePlaces] を取り消し、両方の場所を元の id のまま戻す。控えが無ければ false。 */
  suspend fun undoLastPlaceMerge(): Boolean

  /**
   * 同じ施設の相手がいても、施設情報なしで別の場所として残す（→ adr/0037）。
   * 以後は聞かず、「未取得」にも数えず、Google に問い合わせ直さない。
   */
  suspend fun keepAsSeparatePlace(placeId: Long)

  /**
   * 全経路で、まだ一度も解決していない立ち寄り場所をまとめて名前解決する（オンライン復帰後のキャッチアップ）。
   * オフライン記録などで未解決のまま残った place を拾う。オフライン時は各 place で no-op（課金なし）。
   *
   * **失敗時は例外を投げる**（再試行するかは呼び出し元が決める）。
   */
  suspend fun resolveAllUnresolvedNames()

  /** 場所の表示名を手動で更新する（命名。空文字なら未命名に戻す）。 */
  suspend fun updatePlaceName(placeId: Long, name: String)

  /**
   * 近く（30m以内）に既存の場所があれば再利用し、無ければ新規作成して place の id を返す。
   * 立ち寄りと行きたい場所で同じ場所を共有するための同定（重複排除）。
   *
   * [source] は新規作成時の由来（既定は自動検出＝[PlaceSource.DETECTED]）。再利用時、既存が
   * DETECTED でも [source] が USER なら USER に昇格する（ユーザーが触った場所を自動回収から守る）。
   */
  suspend fun findOrCreatePlace(
    latitude: Double,
    longitude: Double,
    source: PlaceSource = PlaceSource.DETECTED,
  ): Long

  /**
   * **施設の同一性（googlePlaceId）で**同定する。同じ googlePlaceId を持つ place があれば再利用し、
   * 無ければ新規作成する（座標同定はしない＝30m以内の隣接する別施設に相乗りしない）。座標は POI の
   * Google 座標を渡す前提。返り値は (placeId, 既に登録済みだったか)。
   * 呼び出し側は必要なら google_places に名前・住所・カテゴリを控える。
   */
  suspend fun findOrCreateByGooglePlaceId(
    googlePlaceId: String,
    latitude: Double,
    longitude: Double,
    source: PlaceSource = PlaceSource.USER,
  ): Pair<Long, Boolean>
}
