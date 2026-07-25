package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pathly.data.local.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface PlaceDao {

  @Insert
  suspend fun insert(place: PlaceEntity): Long

  @Query("SELECT * FROM places")
  suspend fun getAll(): List<PlaceEntity>

  @Query("SELECT * FROM places WHERE id = :id")
  suspend fun getById(id: Long): PlaceEntity?

  /**
   * ある経路の場所のうち、まだ一度も Google に問い合わせていないもの（自動命名の対象）。
   * 「叩いたか」は `place_resolutions` の行の有無で判定する（name の有無では判定しない）。
   */
  @Query(
    "SELECT DISTINCT p.* FROM places p INNER JOIN stops s ON s.placeId = p.id " +
      "WHERE s.trackId = :trackId " +
      "AND NOT EXISTS (SELECT 1 FROM place_resolutions r WHERE r.placeId = p.id)",
  )
  suspend fun getUnresolvedPlacesForTrack(trackId: Long): List<PlaceEntity>

  /**
   * ある経路の場所のうち、Google の place ID が付いていないもの（手動「場所を取得」の対象）。
   * POI 無し（null 行）・過去失敗・未実施を、ユーザー操作で取り直すため。
   */
  @Query(
    "SELECT DISTINCT p.* FROM places p INNER JOIN stops s ON s.placeId = p.id " +
      "WHERE s.trackId = :trackId " +
      "AND NOT EXISTS (SELECT 1 FROM place_resolutions r WHERE r.placeId = p.id AND r.googlePlaceId IS NOT NULL)",
  )
  suspend fun getPlacesWithoutGoogleIdForTrack(trackId: Long): List<PlaceEntity>

  /** 手動「場所を取得」ボタンの表示用: googlePlaceId が無い place の件数（リアクティブ）。 */
  @Query(
    "SELECT COUNT(DISTINCT p.id) FROM places p INNER JOIN stops s ON s.placeId = p.id " +
      "WHERE s.trackId = :trackId " +
      "AND NOT EXISTS (SELECT 1 FROM place_resolutions r WHERE r.placeId = p.id AND r.googlePlaceId IS NOT NULL)",
  )
  fun countPlacesWithoutGoogleIdForTrack(trackId: Long): Flow<Int>

  @Query("UPDATE places SET name = :name, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateName(id: Long, name: String?, updatedAt: Date)

  @Query("UPDATE places SET name = :name, address = :address, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateNameAndAddress(id: Long, name: String?, address: String?, updatedAt: Date)

  @Query("DELETE FROM places WHERE id = :id")
  suspend fun deleteById(id: Long)
}
