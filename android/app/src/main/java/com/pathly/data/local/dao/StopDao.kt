package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.pathly.data.local.entity.PlaceVisitRow
import com.pathly.data.local.entity.StopEntity
import com.pathly.data.local.entity.StopWithPlace
import com.pathly.data.local.entity.TrackStopCount
import kotlinx.coroutines.flow.Flow

@Dao
interface StopDao {

  @Insert
  suspend fun insert(stop: StopEntity): Long

  @Query("SELECT COUNT(*) FROM stops WHERE trackId = :trackId")
  suspend fun countByTrack(trackId: Long): Int

  /** 経路ごとの立ち寄り件数（一覧の件数表示・並べ替え用）。立ち寄り0件の経路は行が出ない。 */
  @Query("SELECT trackId, COUNT(*) AS count FROM stops GROUP BY trackId")
  fun observeStopCountsByTrack(): Flow<List<TrackStopCount>>

  @Query("DELETE FROM stops WHERE trackId = :trackId")
  suspend fun deleteByTrack(trackId: Long)

  @Query("DELETE FROM stops WHERE id IN (:stopIds)")
  suspend fun deleteByIds(stopIds: List<Long>)

  /** 指定した立ち寄りの実体（削除前のスナップショット取得・取り消し復元に使う）。 */
  @Query("SELECT * FROM stops WHERE id IN (:stopIds)")
  suspend fun getByIds(stopIds: List<Long>): List<StopEntity>

  /** その経路の立ち寄り実体（再解析の重複判定で時間帯を参照する）。 */
  @Query("SELECT * FROM stops WHERE trackId = :trackId")
  suspend fun getByTrack(trackId: Long): List<StopEntity>

  /** その場所への訪問の総数（経路を問わない）。0 かつ行きたい登録も無ければ場所ごと回収してよい。 */
  @Query("SELECT COUNT(*) FROM stops WHERE placeId = :placeId")
  suspend fun countByPlace(placeId: Long): Int

  /** 立ち寄り（訪問）のメモを更新する（stop 単位。null/空=メモ無し）。 */
  @Query("UPDATE stops SET note = :note WHERE id = :stopId")
  suspend fun updateNote(stopId: Long, note: String?)

  /** その立ち寄り（訪問）が指す場所を付け替える（誤検知の「この訪問だけ選び直し」）。 */
  @Query("UPDATE stops SET placeId = :placeId WHERE id = :stopId")
  suspend fun updatePlace(stopId: Long, placeId: Long)

  /**
   * ある場所を指す立ち寄りを**まとめて**別の場所へ付け替える（同じ施設と分かった place の統合）。
   * 統合される側の place はこのあと消えるので、参照を先に移しておく（→ adr/0023）。
   */
  @Query("UPDATE stops SET placeId = :newPlaceId WHERE placeId = :oldPlaceId")
  suspend fun repointPlace(oldPlaceId: Long, newPlaceId: Long)

  @Transaction
  @Query("SELECT * FROM stops WHERE trackId = :trackId ORDER BY arrivalTime ASC")
  fun getStopsWithPlaceByTrack(trackId: Long): Flow<List<StopWithPlace>>

  /** その場所を含むお出掛け（経路）の一覧。新しい順。「場所→関連経路の一覧」に使う。 */
  @Query(
    "SELECT s.trackId AS trackId, t.startTime AS trackStartTime, " +
      "s.arrivalTime AS arrivalTime, s.departureTime AS departureTime, s.note AS note " +
      "FROM stops s INNER JOIN gps_tracks t ON t.id = s.trackId " +
      "WHERE s.placeId = :placeId " +
      "ORDER BY t.startTime DESC",
  )
  fun getVisitsForPlace(placeId: Long): Flow<List<PlaceVisitRow>>
}
