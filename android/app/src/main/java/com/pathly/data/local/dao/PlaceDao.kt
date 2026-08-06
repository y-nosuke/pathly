package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.PlaceWithWishlist
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface PlaceDao {

  @Insert
  suspend fun insert(place: PlaceEntity): Long

  @Query("SELECT * FROM places")
  suspend fun getAll(): List<PlaceEntity>

  /**
   * 「場所」タブ用: 全ての場所を、Google 由来データ・行きたい登録（あれば）と立ち寄り件数付きで取得する。
   * places / google_places / wishlist / stops を明示的に参照するので、いずれの変更にもリアクティブに追従する。
   */
  @Query(
    "SELECT p.id AS id, p.name AS name, p.latitude AS latitude, p.longitude AS longitude, " +
      "p.note AS note, p.createdAt AS createdAt, p.updatedAt AS updatedAt, " +
      "g.googlePlaceId AS googlePlaceId, g.name AS googleName, g.address AS googleAddress, g.category AS category, " +
      "w.id AS wishlistId, w.priority AS priority, w.visitedAt AS visitedAt, " +
      "(SELECT COUNT(*) FROM stops s WHERE s.placeId = p.id) AS visitCount, " +
      "(SELECT MAX(s.arrivalTime) FROM stops s WHERE s.placeId = p.id) AS lastStopAt " +
      "FROM places p " +
      "LEFT JOIN google_places g ON g.placeId = p.id " +
      "LEFT JOIN wishlist w ON w.placeId = p.id " +
      "ORDER BY p.createdAt DESC",
  )
  fun getPlacesWithWishlist(): Flow<List<PlaceWithWishlist>>

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
   * POI 無し・過去失敗・未実施を、ユーザー操作で取り直すため。
   * 「取得済み」は google_places の行の有無で判定する（行がある＝googlePlaceId あり）。
   */
  @Query(
    "SELECT DISTINCT p.* FROM places p INNER JOIN stops s ON s.placeId = p.id " +
      "WHERE s.trackId = :trackId " +
      "AND NOT EXISTS (SELECT 1 FROM google_places g WHERE g.placeId = p.id)",
  )
  suspend fun getPlacesWithoutGoogleIdForTrack(trackId: Long): List<PlaceEntity>

  /** 手動「場所を取得」ボタンの表示用: googlePlaceId が無い place の件数（リアクティブ）。 */
  @Query(
    "SELECT COUNT(DISTINCT p.id) FROM places p INNER JOIN stops s ON s.placeId = p.id " +
      "WHERE s.trackId = :trackId " +
      "AND NOT EXISTS (SELECT 1 FROM google_places g WHERE g.placeId = p.id)",
  )
  fun countPlacesWithoutGoogleIdForTrack(trackId: Long): Flow<Int>

  @Query("UPDATE places SET name = :name, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateName(id: Long, name: String?, updatedAt: Date)

  @Query("UPDATE places SET note = :note, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateNote(id: Long, note: String?, updatedAt: Date)

  @Query("DELETE FROM places WHERE id = :id")
  suspend fun deleteById(id: Long)
}
