package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.pathly.data.local.entity.PlaceVisitRow
import com.pathly.data.local.entity.StopEntity
import com.pathly.data.local.entity.StopWithPlace
import kotlinx.coroutines.flow.Flow

@Dao
interface StopDao {

  @Insert
  suspend fun insert(stop: StopEntity): Long

  @Query("SELECT COUNT(*) FROM stops WHERE trackId = :trackId")
  suspend fun countByTrack(trackId: Long): Int

  @Query("DELETE FROM stops WHERE trackId = :trackId")
  suspend fun deleteByTrack(trackId: Long)

  @Query("DELETE FROM stops WHERE id IN (:stopIds)")
  suspend fun deleteByIds(stopIds: List<Long>)

  /** 指定した立ち寄りの実体（削除前のスナップショット取得・取り消し復元に使う）。 */
  @Query("SELECT * FROM stops WHERE id IN (:stopIds)")
  suspend fun getByIds(stopIds: List<Long>): List<StopEntity>

  /** その場所への訪問の総数（経路を問わない）。0 かつ行きたい登録も無ければ場所ごと回収してよい。 */
  @Query("SELECT COUNT(*) FROM stops WHERE placeId = :placeId")
  suspend fun countByPlace(placeId: Long): Int

  @Transaction
  @Query("SELECT * FROM stops WHERE trackId = :trackId ORDER BY arrivalTime ASC")
  fun getStopsWithPlaceByTrack(trackId: Long): Flow<List<StopWithPlace>>

  /** その場所を含むお出掛け（経路）の一覧。新しい順。「場所→関連経路の一覧」に使う。 */
  @Query(
    "SELECT s.trackId AS trackId, t.startTime AS trackStartTime, " +
      "s.arrivalTime AS arrivalTime, s.departureTime AS departureTime " +
      "FROM stops s INNER JOIN gps_tracks t ON t.id = s.trackId " +
      "WHERE s.placeId = :placeId " +
      "ORDER BY t.startTime DESC",
  )
  fun getVisitsForPlace(placeId: Long): Flow<List<PlaceVisitRow>>
}
