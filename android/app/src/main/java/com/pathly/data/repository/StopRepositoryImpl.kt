package com.pathly.data.repository

import com.pathly.data.local.dao.GooglePlaceDao
import com.pathly.data.local.dao.PlaceDao
import com.pathly.data.local.dao.PlaceResolutionDao
import com.pathly.data.local.dao.SmoothedPointDao
import com.pathly.data.local.dao.StopDao
import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.PlaceResolutionEntity
import com.pathly.data.local.entity.StopEntity
import com.pathly.data.places.PlacesNameResolver
import com.pathly.data.repository.PlaceStopSession.StopChangeSnapshot
import com.pathly.domain.model.DetectedStop
import com.pathly.domain.model.Geo
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceSource
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopCandidate
import com.pathly.domain.model.StopDeletionResult
import com.pathly.domain.model.StopDetector
import com.pathly.domain.model.StopMergeResult
import com.pathly.domain.repository.StopRepository
import com.pathly.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StopRepositoryImpl @Inject constructor(
  private val placeDao: PlaceDao,
  private val stopDao: StopDao,
  private val smoothedPointDao: SmoothedPointDao,
  private val placeResolutionDao: PlaceResolutionDao,
  private val googlePlaceDao: GooglePlaceDao,
  private val placesNameResolver: PlacesNameResolver,
  private val places: PlaceOperations,
  private val session: PlaceStopSession,
) : StopRepository {

  private val logger = Logger("StopRepositoryImpl")

  private val mutex get() = session.mutex

  override fun getStopsForTrack(trackId: Long): Flow<List<Stop>> = stopDao.getStopsWithPlaceByTrack(trackId).map { list -> list.map { it.toStop() } }

  override suspend fun detectMissingStops(trackId: Long): List<StopCandidate> = mutex.withLock {
    val smoothed = smoothedPointDao.getByTrack(trackId).map { it.toGpsPoint() }
    // 記録中（この端末プロセスでライブ検出が動作中＝境界エントリがある）は、ライブ検出が
    // 受け持つ末尾（境界より後）を候補から外し、確定済みの「過去」だけを対象にする。これで
    // 「滞在中の立ち寄り」や、あとでライブ検出が確定保存する区間との二重登録を防ぐ。境界（＝最後に
    // 確定した立ち寄りの departure）以前なら、記録中に誤って消した立ち寄りも候補に出せる。
    // 終了済みの経路は境界エントリが無いので全点が対象（従来どおり）。
    val boundaryMillis = session.detectionHighWaterMillis[trackId]
    val detected = StopDetector.detect(smoothed).let { list ->
      if (boundaryMillis != null) list.filter { it.departureTime.time <= boundaryMillis } else list
    }
    val existing = stopDao.getByTrack(trackId)
    // 既存の立ち寄りと時間帯が重なる候補は「一覧に有る」とみなして除外する（非破壊・追加提案）。
    val missing = detected.filter { candidate -> existing.none { timeOverlaps(candidate, it) } }
    // 追加前の判断用に表示名を用意する（永続化はしない）。
    missing.map { resolveCandidate(it) }
  }

  /**
   * 候補に表示名を添える。近く（30m）に命名済み place があれば無料で再利用し、無ければ
   * オンライン時のみ Places で1回引く。オフライン／POI無しは名前 null。永続化はしない。
   */
  private suspend fun resolveCandidate(d: DetectedStop): StopCandidate {
    // 近く（30m）に「名前のある」place（自分の名前 or Google 名）があれば無料で再利用する。
    // 近傍だけを Google 情報つきの1クエリで取る（以前は全件を読み、place ごとに
    // google_places を引き直す N+1 になっていた）。
    for (place in places.namedPlacesNear(d.latitude, d.longitude)) {
      if (Geo.distanceMeters(place.latitude, place.longitude, d.latitude, d.longitude) > PlaceOperations.DEDUPE_RADIUS_METERS) continue
      val name = place.name ?: place.googleName
      if (name != null) {
        return StopCandidate(
          d,
          name,
          place.googleAddress,
          place.category,
          place.googlePlaceId,
          place.googleLatitude,
          place.googleLongitude,
        )
      }
    }
    return when (val outcome = placesNameResolver.resolve(d.latitude, d.longitude)) {
      is PlacesNameResolver.Outcome.Found ->
        StopCandidate(
          d,
          outcome.name,
          outcome.address,
          outcome.category,
          outcome.googlePlaceId,
          outcome.latitude,
          outcome.longitude,
        )

      PlacesNameResolver.Outcome.NoMatch, PlacesNameResolver.Outcome.NotAttempted -> StopCandidate(d)
    }
  }

  override suspend fun addStops(trackId: Long, candidates: List<StopCandidate>): Int = mutex.withLock {
    if (candidates.isEmpty()) return@withLock 0
    for (candidate in candidates) {
      val d = candidate.detected
      // 候補は検出時に施設まで引けていることがある。**分かっているなら施設の同一性で同定する**
      // （→ adr/0025）。座標だけで同定すると、同じ施設の別の位置で作った place が 30m を外れて
      // 2 つ目になり、あとで「同じ施設なのに場所が 2 つ」になってしまう。Places の追加呼び出しは
      // 要らない（候補が既に ID を持っている）。
      val googlePlaceId = candidate.googlePlaceId
      val placeId = if (googlePlaceId != null) {
        // 新規作成になるときのアンカーは**検出した重心**（＝実際にいた場所）。施設の代表点は
        // 表示用として google_places に入れる（→ adr/0023・StopCandidate の注記）。
        places.findOrCreateByGooglePlaceId(googlePlaceId, d.latitude, d.longitude, PlaceSource.DETECTED).first
      } else {
        places.findOrCreatePlace(d.latitude, d.longitude)
      }
      stopDao.insert(
        StopEntity(
          placeId = placeId,
          trackId = trackId,
          arrivalTime = d.arrivalTime,
          departureTime = d.departureTime,
        ),
      )
      // 検出時に引いた Google データを、まだ解決記録の無い place にだけ焼き込む（Places を二度叩かない）。
      // 名前は Google 由来なので google_places に入れる（places.name はユーザー名専用）。
      if (placeResolutionDao.getByPlace(placeId) == null && candidate.googlePlaceId != null) {
        // 施設を既に他の place が持っていたら焼き込まない（同じ施設を 2 つ持たせない → adr/0025）。
        val holder = googlePlaceDao.getPlaceIdByGoogleId(candidate.googlePlaceId)
        if (googlePlaceDao.getByPlace(placeId) == null && (holder == null || holder == placeId)) {
          googlePlaceDao.upsert(
            GooglePlaceEntity(
              placeId,
              candidate.googlePlaceId,
              candidate.name,
              candidate.address,
              places.categoryIdOf(candidate.category),
              candidate.googleLatitude,
              candidate.googleLongitude,
            ),
          )
        }
        placeResolutionDao.upsert(PlaceResolutionEntity(placeId, Date()))
      }
    }
    // 名前が付かなかった place（オフライン等）はオンライン時にキャッチアップする。
    for (place in placeDao.getUnresolvedPlacesForTrack(trackId)) {
      places.resolvePlace(place)
    }
    places.fulfillReachedWishes()
    logger.i("Added ${candidates.size} stops for track $trackId")
    candidates.size
  }

  override suspend fun addManualStop(
    trackId: Long,
    latitude: Double,
    longitude: Double,
    arrivalTime: Date,
    departureTime: Date,
    name: String?,
    googlePlaceId: String?,
    forceNewPlace: Boolean,
    googleName: String?,
  ): Long = mutex.withLock {
    val placeId = places.prepareManualPlace(latitude, longitude, name, googlePlaceId, googleName, forceNewPlace)
    val stopId = stopDao.insert(
      StopEntity(
        placeId = placeId,
        trackId = trackId,
        arrivalTime = arrivalTime,
        departureTime = departureTime,
      ),
    )
    places.fulfillReachedWishes()
    logger.i("Added manual stop $stopId (place $placeId) for track $trackId")
    stopId
  }

  override suspend fun addManualStopForPlace(
    trackId: Long,
    placeId: Long,
    arrivalTime: Date,
    departureTime: Date,
  ): Long = mutex.withLock {
    // 地図の登録済みマーカーを選んで、既存 place にこの訪問を紐付ける（新規 place を作らない）。
    val stopId = stopDao.insert(
      StopEntity(
        placeId = placeId,
        trackId = trackId,
        arrivalTime = arrivalTime,
        departureTime = departureTime,
      ),
    )
    places.fulfillReachedWishes()
    logger.i("Added manual stop $stopId linked to existing place $placeId for track $trackId")
    stopId
  }

  override suspend fun reassignStopPlace(stopId: Long, chosen: PlaceSearchResult?, customName: String?) = mutex.withLock {
    val stop = stopDao.getByIds(listOf(stopId)).firstOrNull() ?: return@withLock
    val oldPlaceId = stop.placeId
    val trimmedName = customName?.trim()?.ifBlank { null }

    val newPlaceId: Long = when {
      // POI 候補を選んだ: 施設の同一性で同定した place へ付け替える（隣接店を分離）。
      chosen != null -> {
        val pid = places.findOrCreateByGooglePlaceId(chosen.googlePlaceId, chosen.latitude, chosen.longitude, PlaceSource.USER).first
        if (googlePlaceDao.getByPlace(pid) == null) {
          googlePlaceDao.upsert(
            GooglePlaceEntity(
              pid,
              chosen.googlePlaceId,
              chosen.name,
              chosen.address,
              places.categoryIdOf(chosen.category),
              chosen.latitude,
              chosen.longitude,
            ),
          )
        }
        if (placeResolutionDao.getByPlace(pid) == null) placeResolutionDao.upsert(PlaceResolutionEntity(pid, Date()))
        pid
      }

      // 名前だけ手入力: 元の場所の座標で、新しい USER 場所を作る（座標同定せず隣接と分離）。
      trimmedName != null -> {
        val old = placeDao.getById(oldPlaceId)
        val pid = placeDao.insert(
          PlaceEntity(
            name = trimmedName,
            latitude = old?.latitude ?: 0.0,
            longitude = old?.longitude ?: 0.0,
            source = PlaceSource.USER.name,
          ),
        )
        placeResolutionDao.upsert(PlaceResolutionEntity(pid, Date()))
        pid
      }

      // 何も選ばれていなければ何もしない。
      else -> return@withLock
    }

    if (newPlaceId == oldPlaceId) return@withLock // 同じ場所なら変更なし
    stopDao.updatePlace(stopId, newPlaceId)
    places.fulfillReachedWishes()
    // 付け替えで参照が無くなった元の場所が検出由来なら回収する（誤検知の後始末）。
    places.recycleIfOrphanedDetected(oldPlaceId)
    logger.i("Reassigned stop $stopId from place $oldPlaceId to $newPlaceId")
  }

  override suspend fun updateStopNote(stopId: Long, note: String?) {
    stopDao.updateNote(stopId, note?.trim()?.ifBlank { null })
  }

  override suspend fun updateStopDuration(stopId: Long, arrivalTime: Date, departureTime: Date) {
    // 到着＝出発の0分滞在や逆転は、押し間違いでしか作れないので書かずに捨てる。
    if (!departureTime.after(arrivalTime)) {
      logger.w("Ignored invalid stop duration for stop=$stopId (arrival >= departure)")
      return
    }
    stopDao.updateDuration(stopId, arrivalTime, departureTime)
    // 到着を後ろへずらすと、行きたいに入れた後の立ち寄りになることがある。
    places.fulfillReachedWishes()
  }

  override suspend fun mergeStops(stopIds: List<Long>): StopMergeResult? = mutex.withLock {
    if (stopIds.size < 2) return@withLock null
    val targets = stopDao.getByIds(stopIds).sortedBy { it.arrivalTime.time }
    // まとめてよいのは「同じ経路の・同じ場所への」訪問だけ。混ざっていたらどれが正か決められない。
    if (targets.size < 2) return@withLock null
    if (targets.distinctBy { it.placeId }.size > 1 || targets.distinctBy { it.trackId }.size > 1) {
      logger.w("Refused to merge ${targets.size} stops of different places/tracks")
      return@withLock null
    }
    // 残すのは到着が最も早い1件。期間は全体を覆い、メモは到着順に連結する。
    val survivor = targets.first()
    val absorbed = targets.drop(1)
    val merged = survivor.copy(
      arrivalTime = Date(targets.minOf { it.arrivalTime.time }),
      departureTime = Date(targets.maxOf { it.departureTime.time }),
      note = targets.mapNotNull { it.note?.trim()?.ifBlank { null } }
        .joinToString("\n")
        .ifBlank { null },
    )
    stopDao.deleteByIds(absorbed.map { it.id })
    stopDao.update(merged)
    // 場所は全員が同じで、残った1件が参照し続けるので回収は起きない（stops だけ控える）。
    session.lastStopChange = StopChangeSnapshot(stops = absorbed, modifiedStops = listOf(survivor))
    logger.i("Merged ${targets.size} stops into stop=${survivor.id}")
    StopMergeResult(survivingStopId = survivor.id, mergedCount = targets.size)
  }

  override suspend fun deleteStops(stopIds: List<Long>): StopDeletionResult = mutex.withLock {
    if (stopIds.isEmpty()) {
      session.lastStopChange = null
      return@withLock StopDeletionResult(0, 0, 0)
    }
    // 取り消し（Undo）で元IDのまま戻せるよう、削除前に実体を控える。
    val deletedStops = stopDao.getByIds(stopIds)
    val affectedPlaceIds = deletedStops.map { it.placeId }.distinct()
    stopDao.deleteByIds(stopIds)
    val deletedPlaces = mutableListOf<PlaceEntity>()
    val deletedResolutions = mutableListOf<PlaceResolutionEntity>()
    val deletedGooglePlaces = mutableListOf<GooglePlaceEntity>()
    var placesDeleted = 0
    var placesKept = 0
    for (placeId in affectedPlaceIds) {
      // 他の訪問が残る（他の履歴など）か、行きたい登録・行きたいの履歴がある場所は保持する。
      // どちらも place を消すと CASCADE で消えるため、ここで巻き込まないよう明示的に守る。
      val place = placeDao.getById(placeId)
      val stillReferenced = stopDao.countByPlace(placeId) > 0 || places.hasWishes(placeId)
      // ユーザーが明示的に作った場所（USER）は、参照ゼロでも自動では消さない（意図的な登録を守る）。
      // 自動回収するのは検出が作った使い捨て（DETECTED）だけ。
      val autoDisposable = place != null && place.source == PlaceSource.DETECTED.name
      if (stillReferenced || !autoDisposable) {
        placesKept++
      } else {
        // 回収する place と Google データ・解決ログを控えてから消す（子は CASCADE で消える）。
        deletedPlaces.add(place)
        placeResolutionDao.getByPlace(placeId)?.let { deletedResolutions.add(it) }
        googlePlaceDao.getByPlace(placeId)?.let { deletedGooglePlaces.add(it) }
        placeDao.deleteById(placeId)
        placesDeleted++
      }
    }
    session.lastStopChange = StopChangeSnapshot(deletedStops, deletedPlaces, deletedResolutions, deletedGooglePlaces)
    logger.i("Batch-deleted ${stopIds.size} stops (places: -$placesDeleted, kept $placesKept)")
    StopDeletionResult(stopsDeleted = stopIds.size, placesDeleted = placesDeleted, placesKept = placesKept)
  }

  override suspend fun undoLastStopChange(): Boolean = mutex.withLock {
    val snap = session.lastStopChange ?: return@withLock false
    // FK 順に復元する: 先に place（子が参照）→ Google データ・解決ログ → stops。
    // 明示的な id を持つ実体を再挿入するので、元の id のまま戻る。
    for (place in snap.places) placeDao.insert(place)
    for (google in snap.googlePlaces) googlePlaceDao.upsert(google)
    for (resolution in snap.resolutions) placeResolutionDao.upsert(resolution)
    for (stop in snap.stops) stopDao.insert(stop)
    // 統合で期間・メモを書き換えた行は、消さずに変更前の内容へ戻す。
    for (stop in snap.modifiedStops) stopDao.update(stop)
    places.fulfillReachedWishes()
    session.lastStopChange = null
    logger.i(
      "Undid stop change: restored ${snap.stops.size} stops, ${snap.places.size} places, " +
        "reverted ${snap.modifiedStops.size} stops",
    )
    true
  }

  /** 検出候補と既存の立ち寄りの滞在時間帯が重なるか（重なれば同じ訪問とみなす）。 */
  private fun timeOverlaps(candidate: DetectedStop, existing: StopEntity): Boolean = candidate.arrivalTime.before(existing.departureTime) && existing.arrivalTime.before(candidate.departureTime)
}
