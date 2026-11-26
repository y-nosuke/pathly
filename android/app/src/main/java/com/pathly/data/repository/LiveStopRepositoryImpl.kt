package com.pathly.data.repository

import com.pathly.data.local.dao.GpsPointDao
import com.pathly.data.local.dao.PlaceDao
import com.pathly.data.local.dao.PlaceResolutionDao
import com.pathly.data.local.dao.SmoothedPointDao
import com.pathly.data.local.dao.StopDao
import com.pathly.data.local.entity.StopEntity
import com.pathly.data.repository.PlaceStopSession.LiveStopPlace
import com.pathly.data.repository.PlaceStopSession.ManualLiveStop
import com.pathly.domain.model.DetectedStop
import com.pathly.domain.model.Geo
import com.pathly.domain.model.Stop
import com.pathly.domain.model.StopDetector
import com.pathly.domain.repository.LiveStopRepository
import com.pathly.util.Logger
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveStopRepositoryImpl @Inject constructor(
  private val placeDao: PlaceDao,
  private val stopDao: StopDao,
  private val smoothedPointDao: SmoothedPointDao,
  private val gpsPointDao: GpsPointDao,
  private val placeResolutionDao: PlaceResolutionDao,
  private val places: PlaceOperations,
  private val session: PlaceStopSession,
) : LiveStopRepository {

  private val logger = Logger("LiveStopRepositoryImpl")

  private val mutex get() = session.mutex
  private val liveStopPlaces get() = session.liveStopPlaces
  private val manualLiveStops get() = session.manualLiveStops
  private val detectionHighWaterMillis get() = session.detectionHighWaterMillis

  override val currentStop: StateFlow<Stop?> = session.currentStop.asStateFlow()

  override suspend fun updateStopsForTrack(trackId: Long, isFinal: Boolean) {
    try {
      mutex.withLock { detectAndPersist(trackId, isFinal) }
    } catch (e: Exception) {
      logger.e("updateStopsForTrack failed for track $trackId", e)
    }
  }

  override suspend fun startLiveStop(
    trackId: Long,
    anchorLatitude: Double,
    anchorLongitude: Double,
    arrivalTime: Date,
    latitude: Double,
    longitude: Double,
    name: String?,
    googlePlaceId: String?,
    googleName: String?,
  ) = mutex.withLock {
    // GPS の誤差があるので、名前だけのときは 30m 以内の既存の場所に寄せる（自動検出・今までの「今ここ」と同じ）。
    // 名前も施設も選ばなかったときは、検出の立ち寄りと同じく、あとで自動で名前を引けるようにしておく。
    val startedAt = System.currentTimeMillis()
    val placeId = places.prepareManualPlace(
      latitude,
      longitude,
      name,
      googlePlaceId,
      googleName,
      forceNewPlace = false,
      markResolved = googlePlaceId != null || !name.isNullOrBlank(),
    )
    val createdPlace = (placeDao.getById(placeId)?.createdAt?.time ?: 0L) >= startedAt
    // すでに立ち寄り中なら、到着はそのままに場所だけ選び直す（手で始めたもの・検出したもののどちらでも）。
    // やめた立ち寄り中は引き継がない（今から始め直す）。
    val manual = manualLiveStops[trackId]?.takeUnless { it.dismissed }
    val detected = session.currentStop.value?.takeIf { it.trackId == trackId && manual == null }
    val arrival = manual?.arrivalTime ?: detected?.arrivalTime ?: arrivalTime
    // 検出の立ち寄り中に先行確保していた place は使わなくなる。誰も参照していなければ回収する。
    if (detected != null) {
      liveStopPlaces.remove(trackId)
      if (detected.place.id != placeId) places.recycleIfOrphanedDetected(detected.place.id)
    }
    // 手で始めた立ち寄り中の場所を選び直したら、前にこのために作った場所は、誰も使っていなければ片付ける。
    if (manual != null && manual.createdPlace && manual.placeId != placeId) places.removeIfUnused(manual.placeId)
    val lastInside = maxOf(manual?.lastInsideTime ?: arrival, arrivalTime)
    manualLiveStops[trackId] = ManualLiveStop(
      placeId = placeId,
      arrivalTime = arrival,
      anchorLatitude = manual?.anchorLatitude ?: anchorLatitude,
      anchorLongitude = manual?.anchorLongitude ?: anchorLongitude,
      lastInsideTime = lastInside,
      createdPlace = createdPlace,
      note = manual?.note,
    )
    session.currentStop.value = manualLiveStopOf(trackId, manualLiveStops.getValue(trackId))
    logger.i("Started live stop (place $placeId) for track $trackId")
  }

  override suspend fun updateLiveStopNote(trackId: Long, note: String?) = mutex.withLock {
    val live = takeOverLiveStop(trackId) ?: return@withLock
    manualLiveStops[trackId] = live.copy(note = note?.trim()?.ifBlank { null })
    session.currentStop.value = manualLiveStopOf(trackId, manualLiveStops.getValue(trackId))
  }

  override suspend fun updateLiveStopArrival(trackId: Long, arrivalTime: Date) = mutex.withLock {
    val live = takeOverLiveStop(trackId) ?: return@withLock
    manualLiveStops[trackId] = live.copy(
      arrivalTime = arrivalTime,
      lastInsideTime = if (arrivalTime.after(live.lastInsideTime)) arrivalTime else live.lastInsideTime,
    )
    session.currentStop.value = manualLiveStopOf(trackId, manualLiveStops.getValue(trackId))
  }

  override suspend fun dismissLiveStop(trackId: Long) = mutex.withLock {
    val live = takeOverLiveStop(trackId) ?: return@withLock
    manualLiveStops[trackId] = live.copy(dismissed = true)
    // この立ち寄り中のために作った場所は、誰も使っていなければ片付ける。
    if (live.createdPlace) places.removeIfUnused(live.placeId) else places.recycleIfOrphanedDetected(live.placeId)
    session.currentStop.value = null
    logger.i("Dismissed live stop for track $trackId")
  }

  /**
   * いまの立ち寄り中を、手で始めたもの（[PlaceStopSession.manualLiveStops]）として受け取る。検出の立ち寄り中なら、その到着・場所のまま
   * 手で始めたものに切り替える（直した内容を、確定まで持っておくため）。立ち寄り中でなければ null。呼び出しは [mutex] の下。
   */
  private fun takeOverLiveStop(trackId: Long): ManualLiveStop? {
    manualLiveStops[trackId]?.let { return it.takeUnless { live -> live.dismissed } }
    val detected = session.currentStop.value?.takeIf { it.trackId == trackId } ?: return null
    liveStopPlaces.remove(trackId)
    return ManualLiveStop(
      placeId = detected.place.id,
      arrivalTime = detected.arrivalTime,
      anchorLatitude = detected.place.latitude,
      anchorLongitude = detected.place.longitude,
      lastInsideTime = detected.departureTime,
    ).also { manualLiveStops[trackId] = it }
  }

  /** 手で始めた立ち寄り中の表示用の [Stop]（id は 0。出発は最後に半径の中にいた時刻）。 */
  private suspend fun manualLiveStopOf(trackId: Long, live: ManualLiveStop): Stop? {
    if (live.dismissed) return null
    val place = places.placeOf(live.placeId) ?: return null
    return Stop(
      id = 0,
      place = place,
      trackId = trackId,
      arrivalTime = live.arrivalTime,
      departureTime = live.lastInsideTime,
      note = live.note,
    )
  }

  /**
   * 手で始めた立ち寄り中を、最新の生点で進める。押した地点から検出の半径の外に出た（または [isFinal]）なら確定して保存し、
   * 保存した区間（到着〜出発）を返す。まだ滞在中なら出発（最後に半径の中にいた時刻）を進めて null。呼び出しは [mutex] の下。
   */
  private suspend fun advanceManualLiveStop(trackId: Long, isFinal: Boolean): ManualLiveStop? {
    val live = manualLiveStops[trackId] ?: return null
    val latest = gpsPointDao.getLatestPoint(trackId)
    val inside = latest != null &&
      Geo.distanceMeters(live.anchorLatitude, live.anchorLongitude, latest.latitude, latest.longitude) <= StopDetector.RADIUS_METERS
    // 外に出たら、出た時刻を覚える。中に戻れば忘れる（屋内の GPS の飛びで 1 点だけ外れても確定しない）。
    val updated = when {
      latest == null -> live

      inside -> live.copy(
        lastInsideTime = if (latest.timestamp.after(live.lastInsideTime)) latest.timestamp else live.lastInsideTime,
        outsideSince = null,
      )

      else -> live.copy(outsideSince = live.outsideSince ?: latest.timestamp)
    }
    val left = updated.outsideSince?.let { since ->
      latest != null && latest.timestamp.time - since.time >= LIVE_STOP_LEAVE_CONFIRM_MILLIS
    } ?: false
    if (!isFinal && !left) {
      manualLiveStops[trackId] = updated
      return null
    }
    manualLiveStops.remove(trackId)
    // やめた立ち寄り中は保存しない（同じ滞在を検出が保存しないよう、区間は返す）。
    if (updated.dismissed) return updated
    // 場所が消えていたら（まとめ・削除）確定しない。
    if (placeDao.getById(updated.placeId) == null) return null
    stopDao.insert(
      StopEntity(
        placeId = updated.placeId,
        trackId = trackId,
        arrivalTime = updated.arrivalTime,
        departureTime = updated.lastInsideTime,
        note = updated.note,
      ),
    )
    logger.i("Persisted live stop (place ${updated.placeId}) for track $trackId")
    return updated
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
    // 「今ここを立ち寄り」で手で始めた立ち寄り中を先に進める（離れた・記録を止めたなら確定して保存する）。
    // 手で始めたものがある（またはこのティックで確定した）時間帯は、検出した立ち寄りより手の方を採る。
    val manualFinished = advanceManualLiveStop(trackId, isFinal)
    val manualActive = manualLiveStops[trackId]
    val manualStart = (manualActive ?: manualFinished)?.arrivalTime?.time
    val manualEnd = if (manualActive != null) Long.MAX_VALUE else manualFinished?.lastInsideTime?.time
    fun overlapsManual(d: DetectedStop): Boolean = manualStart != null && manualEnd != null &&
      d.arrivalTime.time <= manualEnd && d.departureTime.time >= manualStart

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
    var highWaterMillis = maxOf(boundaryMillis, manualFinished?.lastInsideTime?.time ?: Long.MIN_VALUE)
    var persistedCount = 0
    for (d in finalized) {
      if (overlapsManual(d)) {
        // 手で始めた立ち寄りと同じ滞在。保存せず、先行確保した place が置き去りなら回収する。
        liveStopPlaceId(trackId, d)?.let { pid ->
          liveStopPlaces.remove(trackId)
          places.recycleIfOrphanedDetected(pid)
        }
        if (d.departureTime.time > highWaterMillis) highWaterMillis = d.departureTime.time
        continue
      }
      // 滞在中に先行確定した place があればそれを使う（ひとつの滞在につき確保は1回）。
      // ここで確保し直すと、クラスタが伸びて重心が動いた分だけ別の place ができ、
      // 先行確定した方は誰からも参照されない置き去りになる（→ adr/0023）。
      val placeId = takeLiveStopPlace(trackId, d) ?: places.findOrCreatePlace(d.latitude, d.longitude)
      stopDao.insert(
        StopEntity(
          placeId = placeId,
          trackId = trackId,
          arrivalTime = d.arrivalTime,
          departureTime = d.departureTime,
        ),
      )
      if (d.departureTime.time > highWaterMillis) highWaterMillis = d.departureTime.time
      persistedCount++
    }
    detectionHighWaterMillis[trackId] = highWaterMillis
    if (persistedCount > 0) {
      logger.i("Persisted $persistedCount stops for track $trackId (incremental)")
    }
    // 滞在が終わった（暫定クラスタが無い）なら、覚えていた place を捨てる。
    if (provisional == null) liveStopPlaces.remove(trackId)
    // 記録終了時はハイウォーターの追跡を片付ける（trackId は再利用されない）。
    if (isFinal) {
      detectionHighWaterMillis.remove(trackId)
      liveStopPlaces.remove(trackId)
    }

    // 確定した立ち寄りの未解決 place をオンラインなら命名する（オフラインは行を作らずキャッチアップ）。
    val unresolved = placeDao.getUnresolvedPlacesForTrack(trackId)
    for (place in unresolved) {
      places.resolvePlace(place)
    }
    // 立ち寄りを確定したか、解決で同じ施設の場所へ寄せた（立ち寄りが付け替わった）ときだけ見る。
    // このメソッドは位置のバッチごとに呼ばれるので、何も変わらないティックでは問い合わせない。
    if (persistedCount > 0 || manualFinished != null || unresolved.isNotEmpty()) places.fulfillReachedWishes()

    // 手で始めた立ち寄り中があれば、そちらを出す（検出の立ち寄り中は出さず、place も確保しない）。
    if (manualActive != null) {
      session.currentStop.value = manualLiveStopOf(trackId, manualActive) // やめたものなら null
      return
    }

    // 「立ち寄り中」: place を先行確定＋命名し、メモリで公開する。ただし表示は、生の現在地が
    // その立ち寄りの中心の半径内にある間だけ出す。補正の確定ラグ（末尾の未確定分）や、50m を
    // 抜けきるまでクラスタが末尾を吸収し続ける分を待たず、離脱したら即座に消すため。
    // これは表示（currentStop）だけの判定で、実際に保存する立ち寄りには影響しない。
    val liveProvisional = provisional?.takeIf { d ->
      // 手で始めた立ち寄りがこのティックで確定した滞在は、検出の立ち寄り中として出し直さない。
      if (overlapsManual(d)) return@takeIf false
      val latest = gpsPointDao.getLatestPoint(trackId)
      latest == null ||
        Geo.distanceMeters(d.latitude, d.longitude, latest.latitude, latest.longitude) <= StopDetector.RADIUS_METERS
    }
    session.currentStop.value = liveProvisional?.let { toLiveStop(trackId, it) }
  }

  /**
   * 「立ち寄り中」の place を先行確定して名前解決し、表示用の [Stop] を作る（id は 0）。
   *
   * 確保は**その滞在につき1回**。2回目以降のティックは覚えた placeId を使い回す（→ adr/0023）。
   */
  private suspend fun toLiveStop(trackId: Long, d: DetectedStop): Stop {
    var placeId = liveStopPlaceId(trackId, d)
      ?: places.findOrCreatePlace(d.latitude, d.longitude).also { liveStopPlaces[trackId] = LiveStopPlace(d.arrivalTime.time, it) }
    if (placeResolutionDao.getByPlace(placeId) == null) {
      // 解決の結果、同じ施設の既存 place へ統合されることがある。生き残った方を使う。
      placeDao.getById(placeId)?.let { placeId = places.resolvePlace(it).placeId }
    }
    val place = places.placeOf(placeId)!!
    return Stop(id = 0, place = place, trackId = trackId, arrivalTime = d.arrivalTime, departureTime = d.departureTime)
  }

  /** その滞在（＝同じ到着時刻のクラスタ）に確保済みの place。無ければ null。 */
  private fun liveStopPlaceId(trackId: Long, d: DetectedStop): Long? = liveStopPlaces[trackId]?.takeIf { it.arrivalMillis == d.arrivalTime.time }?.placeId

  /** [liveStopPlaceId] を取り出して忘れる（確定した立ち寄りに引き渡すため）。 */
  private fun takeLiveStopPlace(trackId: Long, d: DetectedStop): Long? = liveStopPlaceId(trackId, d)?.also { liveStopPlaces.remove(trackId) }

  companion object {
    /** 手で始めた立ち寄り中を「離れた」とみなすまで、半径の外にいる時間。屋内の GPS の飛びで早く確定しないため。 */
    private const val LIVE_STOP_LEAVE_CONFIRM_MILLIS = 60_000L
  }
}
