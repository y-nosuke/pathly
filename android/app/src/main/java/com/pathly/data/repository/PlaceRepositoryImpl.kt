package com.pathly.data.repository

import com.pathly.data.local.dao.GooglePlaceDao
import com.pathly.data.local.dao.GpsPointDao
import com.pathly.data.local.dao.PlaceDao
import com.pathly.data.local.dao.PlaceResolutionDao
import com.pathly.data.local.dao.SmoothedPointDao
import com.pathly.data.local.dao.StopDao
import com.pathly.data.local.dao.WishlistDao
import com.pathly.data.local.entity.GooglePlaceEntity
import com.pathly.data.local.entity.NamedPlaceRow
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.PlaceResolutionEntity
import com.pathly.data.local.entity.RegisteredPlaceRow
import com.pathly.data.local.entity.SmoothedPointEntity
import com.pathly.data.local.entity.StopEntity
import com.pathly.data.local.entity.StopWithPlace
import com.pathly.data.places.PlacesNameResolver
import com.pathly.domain.model.DetectedStop
import com.pathly.domain.model.Geo
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceSource
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopCandidate
import com.pathly.domain.model.StopDeletionResult
import com.pathly.domain.model.StopDetector
import com.pathly.domain.repository.PlaceRepository
import com.pathly.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepositoryImpl @Inject constructor(
  private val placeDao: PlaceDao,
  private val stopDao: StopDao,
  private val smoothedPointDao: SmoothedPointDao,
  private val gpsPointDao: GpsPointDao,
  private val placeResolutionDao: PlaceResolutionDao,
  private val googlePlaceDao: GooglePlaceDao,
  private val wishlistDao: WishlistDao,
  private val placesNameResolver: PlacesNameResolver,
) : PlaceRepository {

  private val logger = Logger("PlaceRepositoryImpl")

  // 記録中の検出・保存・命名を直列化する。
  private val mutex = Mutex()

  private val _currentStop = MutableStateFlow<Stop?>(null)
  override val currentStop: StateFlow<Stop?> = _currentStop.asStateFlow()

  // 直近の削除の取り消し用スナップショット（1件だけ保持。次の削除で置き換わる）。
  // 画面のスナックバーは常に最新の1件しか出さないため、単一スロットで十分。
  private var lastDeletion: DeletedSnapshot? = null

  // 記録中に確定・追記した立ち寄りの「最後の departure（ミリ秒）」をトラック単位で覚える。
  // これを削除では下げずセッション中は単調増加させることで、ユーザーが記録中に削除した
  // 立ち寄りを、検出（GPSは変わらないので同じ結果になる）が再挿入して復活させないようにする。
  // アクセスは常に [mutex] 下（[detectAndPersist]）。プロセス終了で自然にクリアされる。
  private val detectionHighWaterMillis = mutableMapOf<Long, Long>()

  override fun getStopsForTrack(trackId: Long): Flow<List<Stop>> = stopDao.getStopsWithPlaceByTrack(trackId).map { list -> list.map { it.toStop() } }

  override fun observeRegisteredPlaces(): Flow<List<RegisteredPlace>> = placeDao.observeRegisteredPlaces().map { rows ->
    rows.map { it.toRegisteredPlace() }
  }

  override suspend fun findNearbyPlace(latitude: Double, longitude: Double): RegisteredPlace? {
    // まず矩形（座標索引が効く）で絞り、そのうえで正確な距離で判定する。
    val bounds = Geo.boundsAround(latitude, longitude, StopDetector.RADIUS_METERS)
    return placeDao.getRegisteredPlacesInBounds(
      bounds.minLatitude,
      bounds.maxLatitude,
      bounds.minLongitude,
      bounds.maxLongitude,
    )
      .map { it.toRegisteredPlace() to distanceMeters(it.latitude, it.longitude, latitude, longitude) }
      .filter { it.second <= StopDetector.RADIUS_METERS }
      .minByOrNull { it.second }
      ?.first
  }

  private fun RegisteredPlaceRow.toRegisteredPlace(): RegisteredPlace = RegisteredPlace(
    placeId = placeId,
    name = name,
    latitude = latitude,
    longitude = longitude,
    isWishlisted = wishlistCount > 0,
    // 「場所」タブと同じ判定: 立ち寄り記録があるか、手動で訪問済みにしたら訪問済み。
    isVisited = visitCount > 0 || visitedAt != null,
  )

  override fun unresolvedCountForTrack(trackId: Long): Flow<Int> = placeDao.countPlacesWithoutGoogleIdForTrack(trackId)

  override suspend fun updateStopsForTrack(trackId: Long, isFinal: Boolean) {
    try {
      mutex.withLock { detectAndPersist(trackId, isFinal) }
    } catch (e: Exception) {
      logger.e("updateStopsForTrack failed for track $trackId", e)
    }
  }

  override suspend fun detectMissingStops(trackId: Long): List<StopCandidate> = mutex.withLock {
    val smoothed = smoothedPointDao.getByTrack(trackId).map { it.toGpsPoint() }
    // 記録中（この端末プロセスでライブ検出が動作中＝境界エントリがある）は、ライブ検出が
    // 受け持つ末尾（境界より後）を候補から外し、確定済みの「過去」だけを対象にする。これで
    // 「滞在中の立ち寄り」や、あとでライブ検出が確定保存する区間との二重登録を防ぐ。境界（＝最後に
    // 確定した立ち寄りの departure）以前なら、記録中に誤って消した立ち寄りも候補に出せる。
    // 終了済みの経路は境界エントリが無いので全点が対象（従来どおり）。
    val boundaryMillis = detectionHighWaterMillis[trackId]
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
    for (place in namedPlacesNear(d.latitude, d.longitude)) {
      if (distanceMeters(place.latitude, place.longitude, d.latitude, d.longitude) > DEDUPE_RADIUS_METERS) continue
      val name = place.name ?: place.googleName
      if (name != null) {
        return StopCandidate(d, name, place.googleAddress, place.category, place.googlePlaceId)
      }
    }
    return when (val outcome = placesNameResolver.resolve(d.latitude, d.longitude)) {
      is PlacesNameResolver.Outcome.Found ->
        StopCandidate(d, outcome.name, outcome.address, outcome.category, outcome.googlePlaceId)
      PlacesNameResolver.Outcome.NoMatch, PlacesNameResolver.Outcome.NotAttempted -> StopCandidate(d)
    }
  }

  override suspend fun addStops(trackId: Long, candidates: List<StopCandidate>): Int = mutex.withLock {
    if (candidates.isEmpty()) return@withLock 0
    for (candidate in candidates) {
      val d = candidate.detected
      val placeId = findOrCreatePlace(d.latitude, d.longitude)
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
        if (googlePlaceDao.getByPlace(placeId) == null) {
          googlePlaceDao.upsert(
            GooglePlaceEntity(placeId, candidate.googlePlaceId, candidate.name, candidate.address, candidate.category),
          )
        }
        placeResolutionDao.upsert(PlaceResolutionEntity(placeId, Date()))
      }
    }
    // 名前が付かなかった place（オフライン等）はオンライン時にキャッチアップする。
    for (place in placeDao.getUnresolvedPlacesForTrack(trackId)) {
      resolvePlace(place)
    }
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
  ): Long = mutex.withLock {
    // 手動追加はユーザーの明示操作なので USER 由来（自動回収から守る）。
    // POI 候補を選んだ（googlePlaceId あり）ときは施設の同一性で同定（隣接店を分離）。座標は Google 座標。
    // forceNewPlace（近接確認で「新規」を選択）なら座標同定せず必ず新規。無いときは座標同定にフォールバック。
    val placeId = when {
      googlePlaceId != null -> findOrCreateByGooglePlaceId(googlePlaceId, latitude, longitude, PlaceSource.USER).first
      forceNewPlace -> placeDao.insert(PlaceEntity(latitude = latitude, longitude = longitude, source = PlaceSource.USER.name))
      else -> findOrCreatePlace(latitude, longitude, PlaceSource.USER)
    }
    val stopId = stopDao.insert(
      StopEntity(
        placeId = placeId,
        trackId = trackId,
        arrivalTime = arrivalTime,
        departureTime = departureTime,
      ),
    )
    // 手入力の名前はユーザー名（places.name）として、未命名の place にだけ焼き込む
    // （他の履歴で既に命名済みの共有 place を上書きしない）。
    val trimmedName = name?.trim()?.ifBlank { null }
    if (trimmedName != null && placeDao.getById(placeId)?.name == null) {
      placeDao.updateName(placeId, trimmedName, Date())
    }
    // POI 由来の googlePlaceId があれば google_places に控える。
    if (googlePlaceId != null && googlePlaceDao.getByPlace(placeId) == null) {
      googlePlaceDao.upsert(GooglePlaceEntity(placeId, googlePlaceId))
    }
    // 手動追加は「ユーザーが名前を決めた」印として解決ログを残し、以後の自動命名（記録中のライブ検出）で
    // 勝手に近くの別施設名に上書きされないようにする。名前なしで追加した場合も未命名のまま保つ。
    if (placeResolutionDao.getByPlace(placeId) == null) {
      placeResolutionDao.upsert(PlaceResolutionEntity(placeId, Date()))
    }
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
    logger.i("Added manual stop $stopId linked to existing place $placeId for track $trackId")
    stopId
  }

  override suspend fun nearbyPois(latitude: Double, longitude: Double): List<PlaceSearchResult> = placesNameResolver.searchNearbyCandidates(latitude, longitude)

  override suspend fun reassignStopPlace(stopId: Long, chosen: PlaceSearchResult?, customName: String?) = mutex.withLock {
    val stop = stopDao.getByIds(listOf(stopId)).firstOrNull() ?: return@withLock
    val oldPlaceId = stop.placeId
    val trimmedName = customName?.trim()?.ifBlank { null }

    val newPlaceId: Long = when {
      // POI 候補を選んだ: 施設の同一性で同定した place へ付け替える（隣接店を分離）。
      chosen != null -> {
        val pid = findOrCreateByGooglePlaceId(chosen.googlePlaceId, chosen.latitude, chosen.longitude, PlaceSource.USER).first
        if (googlePlaceDao.getByPlace(pid) == null) {
          googlePlaceDao.upsert(
            GooglePlaceEntity(pid, chosen.googlePlaceId, chosen.name, chosen.address, chosen.category),
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
    // 付け替えで参照が無くなった元の場所が検出由来なら回収する（誤検知の後始末）。
    recycleIfOrphanedDetected(oldPlaceId)
    logger.i("Reassigned stop $stopId from place $oldPlaceId to $newPlaceId")
  }

  /** 参照（訪問・行きたい）が無くなった場所が検出由来（DETECTED）なら回収する。子は CASCADE で消える。 */
  private suspend fun recycleIfOrphanedDetected(placeId: Long) {
    val place = placeDao.getById(placeId) ?: return
    val referenced = stopDao.countByPlace(placeId) > 0 || wishlistDao.countByPlace(placeId) > 0
    if (!referenced && place.source == PlaceSource.DETECTED.name) {
      placeDao.deleteById(placeId)
    }
  }

  override suspend fun resolveUnresolvedNames(trackId: Long) {
    try {
      mutex.withLock {
        // 手動再取得: googlePlaceId が無い place を対象に（NoMatch・過去失敗も）叩き直す。
        for (place in placeDao.getPlacesWithoutGoogleIdForTrack(trackId)) {
          resolvePlace(place)
        }
      }
    } catch (e: Exception) {
      logger.e("resolveUnresolvedNames failed for track $trackId", e)
    }
  }

  override suspend fun resolveAllUnresolvedNames() {
    try {
      // 未解決（place_resolutions 行が無い）立ち寄り場所を全経路から拾い、オンライン時のみ解決する。
      // resolvePlace はオフラインで NotAttempted（行を残さない）＝再訪時にまた拾えるので安全。
      val unresolved = mutex.withLock { placeDao.getUnresolvedPlaces() }
      if (unresolved.isEmpty()) return
      logger.i("Catching up ${unresolved.size} unresolved places")
      for (place in unresolved) {
        mutex.withLock { resolvePlace(place) }
      }
    } catch (e: Exception) {
      logger.e("resolveAllUnresolvedNames failed", e)
    }
  }

  override suspend fun updatePlaceName(placeId: Long, name: String) {
    placeDao.updateName(placeId, name.trim().ifBlank { null }, Date())
  }

  override suspend fun updateStopNote(stopId: Long, note: String?) {
    stopDao.updateNote(stopId, note?.trim()?.ifBlank { null })
  }

  override suspend fun deleteStops(stopIds: List<Long>): StopDeletionResult = mutex.withLock {
    if (stopIds.isEmpty()) {
      lastDeletion = null
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
      // 他の訪問が残る（他の履歴など）か、行きたい登録がある場所は保持する。
      // wishlist は place を消すと CASCADE で消えるため、ここで巻き込まないよう明示的に守る。
      val place = placeDao.getById(placeId)
      val stillReferenced = stopDao.countByPlace(placeId) > 0 || wishlistDao.countByPlace(placeId) > 0
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
    lastDeletion = DeletedSnapshot(deletedStops, deletedPlaces, deletedResolutions, deletedGooglePlaces)
    logger.i("Batch-deleted ${stopIds.size} stops (places: -$placesDeleted, kept $placesKept)")
    StopDeletionResult(stopsDeleted = stopIds.size, placesDeleted = placesDeleted, placesKept = placesKept)
  }

  override suspend fun undoLastDeletion(): Boolean = mutex.withLock {
    val snap = lastDeletion ?: return@withLock false
    // FK 順に復元する: 先に place（子が参照）→ Google データ・解決ログ → stops。
    // 明示的な id を持つ実体を再挿入するので、元の id のまま戻る。
    for (place in snap.places) placeDao.insert(place)
    for (google in snap.googlePlaces) googlePlaceDao.upsert(google)
    for (resolution in snap.resolutions) placeResolutionDao.upsert(resolution)
    for (stop in snap.stops) stopDao.insert(stop)
    lastDeletion = null
    logger.i("Undid deletion: restored ${snap.stops.size} stops, ${snap.places.size} places")
    true
  }

  /**
   * 補正後の点列から立ち寄りを検出し、確定分だけを差分保存する。末尾の滞在中クラスタが
   * 3分を超えたら place を先行確定して [currentStop] に流す（案B・メモリ保持）。
   * 呼び出しは [mutex] で直列化されている前提。
   *
   * インクリメンタル検出: 毎回全点を検出し直さず、「境界（最後に確定した立ち寄りの
   * departure）」より後の点だけを [StopDetector] にかける。貪欲スキャンは境界より手前の点に
   * 依存しないため、末尾のクラスタ分割は全点で検出した場合と一致する（等価）。これにより
   * (1) 過去の立ち寄り区間を毎ティック舐め直さず、(2) 境界を削除では下げないことで、
   * ユーザーが記録中に削除した立ち寄りを検出が再挿入して復活させない。
   */
  private suspend fun detectAndPersist(trackId: Long, isFinal: Boolean) {
    // 境界（ミリ秒）。セッション中はメモリで単調増加させ、削除では下げない。未設定なら
    // 保存済み立ち寄りの最終 departure で種をまく（プロセス再起動後の再開に対応）。
    val boundaryMillis = detectionHighWaterMillis[trackId]
      ?: stopDao.getByTrack(trackId).maxOfOrNull { it.departureTime.time }
      ?: Long.MIN_VALUE

    // 境界より後の補正点だけを検出対象にする（過去の確定区間はロードしない）。
    val tail = smoothedPointDao.getByTrackAfter(trackId, boundaryMillis).map { it.toGpsPoint() }
    val detected = StopDetector.detect(tail)

    // 末尾点を含む最後のクラスタは「滞在中」＝暫定。isFinal なら末尾も確定。
    val lastTimestamp = tail.lastOrNull()?.timestamp
    val provisional = if (!isFinal) {
      detected.lastOrNull()?.takeIf { it.departureTime == lastTimestamp }
    } else {
      null
    }
    val finalized = if (provisional != null) detected.dropLast(1) else detected

    // スライスは境界より後なので、確定クラスタはすべて未保存＝新規。順に追記して境界を進める。
    var highWaterMillis = boundaryMillis
    for (d in finalized) {
      val placeId = findOrCreatePlace(d.latitude, d.longitude)
      stopDao.insert(
        StopEntity(
          placeId = placeId,
          trackId = trackId,
          arrivalTime = d.arrivalTime,
          departureTime = d.departureTime,
        ),
      )
      if (d.departureTime.time > highWaterMillis) highWaterMillis = d.departureTime.time
    }
    detectionHighWaterMillis[trackId] = highWaterMillis
    if (finalized.isNotEmpty()) {
      logger.i("Persisted ${finalized.size} stops for track $trackId (incremental)")
    }
    // 記録終了時はハイウォーターの追跡を片付ける（trackId は再利用されない）。
    if (isFinal) detectionHighWaterMillis.remove(trackId)

    // 確定した立ち寄りの未解決 place をオンラインなら命名する（オフラインは行を作らずキャッチアップ）。
    for (place in placeDao.getUnresolvedPlacesForTrack(trackId)) {
      resolvePlace(place)
    }

    // 「立ち寄り中」: place を先行確定＋命名し、メモリで公開する。ただし表示は、生の現在地が
    // その立ち寄りの中心の半径内にある間だけ出す。補正の確定ラグ（末尾の未確定分）や、50m を
    // 抜けきるまでクラスタが末尾を吸収し続ける分を待たず、離脱したら即座に消すため。
    // これは表示（currentStop）だけの判定で、実際に保存する立ち寄りには影響しない。
    val liveProvisional = provisional?.takeIf { d ->
      val latest = gpsPointDao.getLatestPoint(trackId)
      latest == null ||
        distanceMeters(d.latitude, d.longitude, latest.latitude, latest.longitude) <= StopDetector.RADIUS_METERS
    }
    _currentStop.value = liveProvisional?.let { toLiveStop(trackId, it) }
  }

  /** 「立ち寄り中」の place を先行確定して名前解決し、表示用の [Stop] を作る（id は 0）。 */
  private suspend fun toLiveStop(trackId: Long, d: DetectedStop): Stop {
    val placeId = findOrCreatePlace(d.latitude, d.longitude)
    if (placeResolutionDao.getByPlace(placeId) == null) {
      placeDao.getById(placeId)?.let { resolvePlace(it) }
    }
    val place = placeDao.getById(placeId)!!.toPlace(googlePlaceDao.getByPlace(placeId))
    return Stop(id = 0, place = place, trackId = trackId, arrivalTime = d.arrivalTime, departureTime = d.departureTime)
  }

  /**
   * place を Google で名前解決し、結果を google_places に記録する（place_resolutions は問い合わせlog）。
   * Google 由来の名前・住所は google_places に入れる。places.name（ユーザー名）は触らない。
   */
  private suspend fun resolvePlace(place: PlaceEntity) {
    when (val outcome = placesNameResolver.resolve(place.latitude, place.longitude)) {
      is PlacesNameResolver.Outcome.Found -> {
        googlePlaceDao.upsert(
          GooglePlaceEntity(place.id, outcome.googlePlaceId, outcome.name, outcome.address, outcome.category),
        )
        placeResolutionDao.upsert(PlaceResolutionEntity(place.id, Date()))
        // 暫定の GPS 座標を、解決した施設の正確な座標へ置き換える（取れたときだけ）。
        if (outcome.latitude != null && outcome.longitude != null) {
          placeDao.updateCoordinates(place.id, outcome.latitude, outcome.longitude, Date())
        }
      }

      PlacesNameResolver.Outcome.NoMatch ->
        placeResolutionDao.upsert(PlaceResolutionEntity(place.id, Date()))

      PlacesNameResolver.Outcome.NotAttempted -> Unit // 行を作らず後でキャッチアップ
    }
  }

  /**
   * 近く（[DEDUPE_RADIUS_METERS] 以内）に既存の場所があれば再利用、無ければ新規作成する。
   *
   * 記録中は位置バッチごとに（滞在中は毎回）呼ばれるため、以前の「全 place を読んで
   * 線形走査」は場所が増えるほど重くなっていた。矩形で絞ってから距離判定する。
   */
  override suspend fun findOrCreatePlace(latitude: Double, longitude: Double, source: PlaceSource): Long {
    val existing = placesNear(latitude, longitude).firstOrNull {
      distanceMeters(it.latitude, it.longitude, latitude, longitude) <= DEDUPE_RADIUS_METERS
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

  override suspend fun findOrCreateByGooglePlaceId(
    googlePlaceId: String,
    latitude: Double,
    longitude: Double,
    source: PlaceSource,
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

  private fun StopWithPlace.toStop(): Stop = Stop(
    id = stop.id,
    place = place.toPlace(google),
    trackId = stop.trackId,
    arrivalTime = stop.arrivalTime,
    departureTime = stop.departureTime,
    note = stop.note,
  )

  private fun PlaceEntity.toPlace(google: GooglePlaceEntity?): Place = Place(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    note = note,
    googleName = google?.name,
    googleAddress = google?.address,
    category = google?.category,
    googlePlaceId = google?.googlePlaceId,
    createdAt = createdAt,
    updatedAt = updatedAt,
  )

  private fun SmoothedPointEntity.toGpsPoint(): GpsPoint = GpsPoint(
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

  /** 削除の取り消しに必要な実体一式（元IDのまま再挿入して復元する）。 */
  private class DeletedSnapshot(
    val stops: List<StopEntity>,
    val places: List<PlaceEntity>,
    val resolutions: List<PlaceResolutionEntity>,
    val googlePlaces: List<GooglePlaceEntity>,
  )

  /** 検出候補と既存の立ち寄りの滞在時間帯が重なるか（重なれば同じ訪問とみなす）。 */
  private fun timeOverlaps(candidate: DetectedStop, existing: StopEntity): Boolean = candidate.arrivalTime.before(existing.departureTime) && existing.arrivalTime.before(candidate.departureTime)

  private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double = Geo.distanceMeters(lat1, lon1, lat2, lon2)

  /** 同一場所とみなす距離の矩形に入る場所（id 順）。正確な距離判定は呼び出し側で行う。 */
  private suspend fun placesNear(latitude: Double, longitude: Double): List<PlaceEntity> {
    val bounds = Geo.boundsAround(latitude, longitude, DEDUPE_RADIUS_METERS)
    return placeDao.getInBounds(bounds.minLatitude, bounds.maxLatitude, bounds.minLongitude, bounds.maxLongitude)
  }

  /** [placesNear] の Google 情報つき版（表示名の解決用）。 */
  private suspend fun namedPlacesNear(latitude: Double, longitude: Double): List<NamedPlaceRow> {
    val bounds = Geo.boundsAround(latitude, longitude, DEDUPE_RADIUS_METERS)
    return placeDao.getNamedPlacesInBounds(bounds.minLatitude, bounds.maxLatitude, bounds.minLongitude, bounds.maxLongitude)
  }

  companion object {
    /** 同一場所とみなす距離（重複排除）。 */
    private const val DEDUPE_RADIUS_METERS = 30.0
  }
}
