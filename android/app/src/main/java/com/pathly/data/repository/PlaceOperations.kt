package com.pathly.data.repository

import com.pathly.data.local.dao.GooglePlaceCategoryDao
import com.pathly.data.local.dao.GooglePlaceDao
import com.pathly.data.local.dao.PlaceDao
import com.pathly.data.local.dao.PlaceResolutionDao
import com.pathly.data.local.dao.StopDao
import com.pathly.data.local.dao.VisitedPlaceDao
import com.pathly.data.local.dao.WishlistDao
import com.pathly.data.local.dao.WishlistFulfillmentDao
import com.pathly.data.local.dao.idOf
import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.GooglePlaceWithCategory
import com.pathly.data.local.entity.NamedPlaceRow
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.PlaceResolutionEntity
import com.pathly.data.local.entity.SmoothedPointEntity
import com.pathly.data.local.entity.StopWithPlace
import com.pathly.data.places.PlacesNameResolver
import com.pathly.domain.model.Geo
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceCategory
import com.pathly.domain.model.PlaceSource
import com.pathly.domain.model.Stop
import com.pathly.util.Logger
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 場所と立ち寄りの 3 つの窓口が共有する、場所の確保・名前解決・同じ施設の自動まとめ・後片付けの処理（→ adr/0048）。
 *
 * どれもロックを取らない。記録中の状態（[PlaceStopSession]）に触るものがあるので、呼び出しは
 * [PlaceStopSession.mutex] の下で行う（[findOrCreatePlace]・[findOrCreateByGooglePlaceId] は状態に触らないので例外）。
 */
@Singleton
class PlaceOperations @Inject constructor(
  private val placeDao: PlaceDao,
  private val stopDao: StopDao,
  private val placeResolutionDao: PlaceResolutionDao,
  private val googlePlaceDao: GooglePlaceDao,
  private val googlePlaceCategoryDao: GooglePlaceCategoryDao,
  private val wishlistDao: WishlistDao,
  private val visitedPlaceDao: VisitedPlaceDao,
  private val wishlistFulfillmentDao: WishlistFulfillmentDao,
  private val placesNameResolver: PlacesNameResolver,
  private val session: PlaceStopSession,
) {

  private val logger = Logger("PlaceOperations")

  /**
   * 近く（[DEDUPE_RADIUS_METERS] 以内）に既存の場所があれば再利用、無ければ新規作成する。
   *
   * 記録中は位置バッチごとに（滞在中は毎回）呼ばれるため、以前の「全 place を読んで
   * 線形走査」は場所が増えるほど重くなっていた。矩形で絞ってから距離判定する。
   */
  suspend fun findOrCreatePlace(latitude: Double, longitude: Double, source: PlaceSource = PlaceSource.DETECTED): Long {
    val existing = placesNear(latitude, longitude).firstOrNull {
      Geo.distanceMeters(it.latitude, it.longitude, latitude, longitude) <= DEDUPE_RADIUS_METERS
    }
    if (existing != null) {
      // ユーザーが触った場所は自動回収から守るため、DETECTED を USER に昇格する（降格はしない）。
      if (source == PlaceSource.USER && existing.source != PlaceSource.USER.name) {
        placeDao.updateSource(existing.id, PlaceSource.USER.name)
      }
      return existing.id
    }
    return placeDao.insert(PlaceEntity(latitude = latitude, longitude = longitude, source = source.name))
  }

  suspend fun findOrCreateByGooglePlaceId(
    googlePlaceId: String,
    latitude: Double,
    longitude: Double,
    source: PlaceSource = PlaceSource.USER,
  ): Pair<Long, Boolean> {
    googlePlaceDao.getPlaceIdByGoogleId(googlePlaceId)?.let { existingId ->
      // 施設の同一性で再利用。ユーザーが触ったので USER に昇格して自動回収から守る。
      if (source == PlaceSource.USER) placeDao.updateSource(existingId, PlaceSource.USER.name)
      return existingId to true
    }
    // 座標同定はしない（隣接する別施設に相乗りしない）。POI の Google 座標で新規作成する。
    val id = placeDao.insert(PlaceEntity(latitude = latitude, longitude = longitude, source = source.name))
    return id to false
  }

  /**
   * 手動で足す立ち寄り（手動追加・今ここを立ち寄り）の place を決めて、名前・施設・解決ログを付ける。
   */
  suspend fun prepareManualPlace(
    latitude: Double,
    longitude: Double,
    name: String?,
    googlePlaceId: String?,
    googleName: String?,
    forceNewPlace: Boolean,
    // 解決ログを書いて、以後の自動命名から外すか（今ここを立ち寄りで名前も施設も選ばなかったときは外さない）。
    markResolved: Boolean = true,
  ): Long {
    // 手動追加はユーザーの明示操作なので USER 由来（自動回収から守る）。
    // POI 候補を選んだ（googlePlaceId あり）ときは施設の同一性で同定（隣接店を分離）。座標は Google 座標。
    // forceNewPlace（近接確認で「新規」を選択）なら座標同定せず必ず新規。無いときは座標同定にフォールバック。
    val placeId = when {
      googlePlaceId != null -> findOrCreateByGooglePlaceId(googlePlaceId, latitude, longitude, PlaceSource.USER).first
      forceNewPlace -> placeDao.insert(PlaceEntity(latitude = latitude, longitude = longitude, source = PlaceSource.USER.name))
      else -> findOrCreatePlace(latitude, longitude, PlaceSource.USER)
    }
    // 手入力の名前はユーザー名（places.name）として、未命名の place にだけ焼き込む
    // （他の履歴で既に命名済みの共有 place を上書きしない）。
    val trimmedName = name?.trim()?.ifBlank { null }
    if (trimmedName != null && placeDao.getById(placeId)?.name == null) {
      placeDao.updateName(placeId, trimmedName, Date())
    }
    // POI 由来の googlePlaceId があれば google_places に控える。施設名も Google 由来なので
    // ここに入れる（places.name はユーザーが付けた名前の列。表示は displayName が拾う）。
    if (googlePlaceId != null && googlePlaceDao.getByPlace(placeId)?.name == null) {
      googlePlaceDao.upsert(GooglePlaceEntity(placeId, googlePlaceId, googleName))
    }
    // 手動追加は「ユーザーが名前を決めた」印として解決ログを残し、以後の自動命名（記録中のライブ検出）で
    // 勝手に近くの別施設名に上書きされないようにする。名前なしで追加した場合も未命名のまま保つ。
    if (markResolved && placeResolutionDao.getByPlace(placeId) == null) {
      placeResolutionDao.upsert(PlaceResolutionEntity(placeId, Date()))
    }
    return placeId
  }

  /**
   * 施設の解決の結果。[placeId] は以後その place として扱う id（統合されたら寄せ先の id）。
   * [skippedByConflict] は「同じ施設を既に他の place が持っていたので施設情報を付けなかった」。
   */
  data class ResolveResult(val placeId: Long, val skippedByConflict: Boolean = false)

  /**
   * place を Google で名前解決し、結果を google_places に記録する（place_resolutions は問い合わせlog）。
   * Google 由来の名前・住所は google_places に入れる。places.name（ユーザー名）は触らない。
   */
  suspend fun resolvePlace(place: PlaceEntity): ResolveResult {
    when (val outcome = placesNameResolver.resolve(place.latitude, place.longitude)) {
      is PlacesNameResolver.Outcome.Found -> {
        // 同じ施設の place が既にあれば、そちらへ寄せる（座標で見つけられなくても
        // 施設の同一性でまとまる）。**upsert より先に**引くこと。後だと自分自身が当たる。
        val existing = googlePlaceDao.getPlaceIdByGoogleId(outcome.googlePlaceId)
        if (existing != null && existing != place.id) {
          if (hasNoUserContent(place)) {
            mergeIntoExistingPlace(place, existing)
            return ResolveResult(existing)
          }
          // 名前・メモなどを入れた place は勝手に寄せられない（混ざると分け直せない）。同じ施設を 2 つ持たせるわけにもいかないので、
          // **施設情報を付けずに残す**（→ adr/0025）。相手の施設を記録し、まとめるか別のまま残すかを
          // ユーザーに選んでもらう（場所詳細・経路詳細の「確認待ち」 → adr/0037）。解決記録があるので、
          // 同じ問い合わせは繰り返さない。
          logger.i("Skipped linking place ${place.id}: the same Google place is already held by place $existing")
          placeResolutionDao.upsert(
            PlaceResolutionEntity(place.id, Date(), sameFacilityGooglePlaceId = outcome.googlePlaceId),
          )
          return ResolveResult(place.id, skippedByConflict = true)
        }
        // 施設の座標は google_places に入れる（表示用）。places の座標＝同定のアンカーは
        // 触らない。ここで動かすと自分で作った place を次の確保で見失う（→ adr/0023）。
        googlePlaceDao.upsert(
          GooglePlaceEntity(
            place.id,
            outcome.googlePlaceId,
            outcome.name,
            outcome.address,
            categoryIdOf(outcome.category),
            outcome.latitude,
            outcome.longitude,
          ),
        )
        placeResolutionDao.upsert(PlaceResolutionEntity(place.id, Date()))
      }

      PlacesNameResolver.Outcome.NoMatch ->
        placeResolutionDao.upsert(PlaceResolutionEntity(place.id, Date()))

      PlacesNameResolver.Outcome.NotAttempted -> Unit // 行を作らず後でキャッチアップ
    }
    return ResolveResult(place.id)
  }

  /**
   * 聞かずにまとめてよい place か（**名前・メモ・行きたい・訪問済みの印が何も無い** → adr/0037）。
   *
   * 自動命名は半径 50m の最寄り1件しか見ないので、別の場所が同じ施設に解決されることがある。
   * 中身を入れた place をまとめると、名前・メモが相手に混ざって分け直せないため、それは聞く。
   * 中身が無ければ、まとめても失うものが無い。違っていたら立ち寄りの「場所を選び直す」で直せる。
   *
   * **由来（`USER`／`DETECTED`）は見ない。** 以前は地図で指して作った place を中身が空でも
   * まとめなかった（指したこと自体を「別の場所にしたい」意思表示とみなした → adr/0025）が、
   * 「場所を取得」を押して同じ施設が出たら、そのまま紐付いてほしい、に改めた（→ adr/0037）。
   */
  private suspend fun hasNoUserContent(place: PlaceEntity): Boolean = place.name == null &&
    place.note == null &&
    !hasWishes(place.id) &&
    visitedPlaceDao.countByPlace(place.id) == 0

  /** 行きたいに入っているか、行きたいの履歴があるか（本人が行きたいと思ったことがある場所 → adr/0036）。 */
  suspend fun hasWishes(placeId: Long): Boolean = wishlistDao.countByPlace(placeId) > 0 ||
    wishlistFulfillmentDao.countByPlace(placeId) > 0

  /**
   * 行った行きたいを終わらせる（入れた後の立ち寄りが記録された行きたいを履歴へ移す → adr/0036）。
   * 立ち寄りを足す・付け替える・期間を変える・戻す、のどれの後にも呼ぶ。
   */
  suspend fun fulfillReachedWishes() {
    val count = wishlistFulfillmentDao.fulfillReachedWishes()
    if (count > 0) logger.i("Fulfilled $count wishlist items by recorded stops")
  }

  /**
   * 立ち寄りの参照を移してから、統合される側の place を消す（子は CASCADE で消える）。
   * 統合される側が自分で作った場所（USER）なら、寄せ先も USER にする（自動回収から守る）。
   */
  private suspend fun mergeIntoExistingPlace(from: PlaceEntity, intoPlaceId: Long) {
    val fromPlaceId = from.id
    if (from.source == PlaceSource.USER.name) placeDao.updateSource(intoPlaceId, PlaceSource.USER.name)
    stopDao.repointPlace(fromPlaceId, intoPlaceId)
    placeDao.deleteById(fromPlaceId)
    session.repointLiveStopPlace(fromPlaceId, intoPlaceId)
    logger.i("Merged place $fromPlaceId into $intoPlaceId (same googlePlaceId)")
  }

  /** 参照（訪問・行きたい・行きたいの履歴）が無くなった場所が検出由来（DETECTED）なら回収する。子は CASCADE で消える。 */
  suspend fun recycleIfOrphanedDetected(placeId: Long) {
    val place = placeDao.getById(placeId) ?: return
    val referenced = stopDao.countByPlace(placeId) > 0 || hasWishes(placeId)
    if (!referenced && place.source == PlaceSource.DETECTED.name) {
      placeDao.deleteById(placeId)
    }
  }

  /** 立ち寄り・行きたいのどれからも使われていない場所を消す（子は CASCADE で消える）。 */
  suspend fun removeIfUnused(placeId: Long) {
    if (stopDao.countByPlace(placeId) == 0 && !hasWishes(placeId)) placeDao.deleteById(placeId)
  }

  /** 表示用の [Place]（Google 情報つき）。場所が無ければ null。 */
  suspend fun placeOf(placeId: Long): Place? = placeDao.getById(placeId)?.toPlace(googlePlaceDao.getWithCategoryByPlace(placeId))

  suspend fun categoryIdOf(category: PlaceCategory?): Long? = googlePlaceCategoryDao.idOf(category)

  /** 同一場所とみなす距離の矩形に入る場所（id 順）。正確な距離判定は呼び出し側で行う。 */
  private suspend fun placesNear(latitude: Double, longitude: Double): List<PlaceEntity> {
    val bounds = Geo.boundsAround(latitude, longitude, DEDUPE_RADIUS_METERS)
    return placeDao.getInBounds(bounds.minLatitude, bounds.maxLatitude, bounds.minLongitude, bounds.maxLongitude)
  }

  /** [placesNear] の Google 情報つき版（表示名の解決用）。 */
  suspend fun namedPlacesNear(latitude: Double, longitude: Double): List<NamedPlaceRow> {
    val bounds = Geo.boundsAround(latitude, longitude, DEDUPE_RADIUS_METERS)
    return placeDao.getNamedPlacesInBounds(bounds.minLatitude, bounds.maxLatitude, bounds.minLongitude, bounds.maxLongitude)
  }

  companion object {
    /** 同一場所とみなす距離（重複排除）。 */
    const val DEDUPE_RADIUS_METERS = 30.0
  }
}

internal fun StopWithPlace.toStop(): Stop = Stop(
  id = stop.id,
  place = place.toPlace(google),
  trackId = stop.trackId,
  arrivalTime = stop.arrivalTime,
  departureTime = stop.departureTime,
  note = stop.note,
)

internal fun PlaceEntity.toPlace(google: GooglePlaceWithCategory?): Place = Place(
  id = id,
  name = name,
  // ドメインの Place が持つのは**表示座標**（Google の代表点 → 無ければアンカー）。
  // 同定に使うアンカーは PlaceEntity 側にだけ置く（→ adr/0023）。
  latitude = google?.google?.latitude ?: latitude,
  longitude = google?.google?.longitude ?: longitude,
  note = note,
  googleName = google?.google?.name,
  googleAddress = google?.google?.address,
  category = google?.category?.let { PlaceCategory(it.code, it.displayName) },
  googlePlaceId = google?.google?.googlePlaceId,
  createdAt = createdAt,
  updatedAt = updatedAt,
)

internal fun SmoothedPointEntity.toGpsPoint(): GpsPoint = GpsPoint(
  id = sourcePointId ?: 0L,
  trackId = trackId,
  latitude = latitude,
  longitude = longitude,
  altitude = null,
  accuracy = 0f,
  speed = null,
  bearing = null,
  timestamp = timestamp,
  createdAt = createdAt,
)
