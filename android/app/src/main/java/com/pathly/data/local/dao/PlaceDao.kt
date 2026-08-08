package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pathly.data.local.entity.PlaceEntity
import com.pathly.data.local.entity.PlaceWithWishlist
import com.pathly.data.local.entity.RegisteredPlaceRow
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
   * 地図に出す「登録済みの場所」全件（USER・DETECTED どちらも）。名前は
   * places.name → google_places.name → google_places.address の順にフォールバック。
   * 状態（行きたい／訪問済み）を描き分けるため、wishlist 件数・立ち寄り件数・手動訪問日時も返す。
   */
  @Query(
    "SELECT p.id AS placeId, COALESCE(p.name, g.name, g.address) AS name, " +
      "p.latitude AS latitude, p.longitude AS longitude, " +
      "(SELECT COUNT(*) FROM wishlist w WHERE w.placeId = p.id) AS wishlistCount, " +
      "(SELECT COUNT(*) FROM stops s WHERE s.placeId = p.id) AS visitCount, " +
      "(SELECT w.visitedAt FROM wishlist w WHERE w.placeId = p.id LIMIT 1) AS visitedAt " +
      "FROM places p LEFT JOIN google_places g ON g.placeId = p.id",
  )
  fun observeRegisteredPlaces(): Flow<List<RegisteredPlaceRow>>

  /** [observeRegisteredPlaces] の一回取得版（手動追加の近接チェック用）。 */
  @Query(
    "SELECT p.id AS placeId, COALESCE(p.name, g.name, g.address) AS name, " +
      "p.latitude AS latitude, p.longitude AS longitude, " +
      "(SELECT COUNT(*) FROM wishlist w WHERE w.placeId = p.id) AS wishlistCount, " +
      "(SELECT COUNT(*) FROM stops s WHERE s.placeId = p.id) AS visitCount, " +
      "(SELECT w.visitedAt FROM wishlist w WHERE w.placeId = p.id LIMIT 1) AS visitedAt " +
      "FROM places p LEFT JOIN google_places g ON g.placeId = p.id",
  )
  suspend fun getRegisteredPlacesOnce(): List<RegisteredPlaceRow>

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
   * 全経路で、まだ一度も Google に問い合わせていない立ち寄り場所（オンライン復帰後の一括キャッチアップ用）。
   * オフライン記録などで未解決のまま残った place を、アプリ起動時にまとめて名前解決する。
   * 「叩いたか」は `place_resolutions` の行の有無で判定する（NoMatch 済みは対象外＝再課金しない）。
   */
  @Query(
    "SELECT DISTINCT p.* FROM places p INNER JOIN stops s ON s.placeId = p.id " +
      "WHERE NOT EXISTS (SELECT 1 FROM place_resolutions r WHERE r.placeId = p.id)",
  )
  suspend fun getUnresolvedPlaces(): List<PlaceEntity>

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

  /** 由来を更新する（例: 検出で作った場所をユーザーが触ったら USER に昇格して自動回収から守る）。 */
  @Query("UPDATE places SET source = :source WHERE id = :id")
  suspend fun updateSource(id: Long, source: String)

  /**
   * 座標を更新する（Google で解決／紐付けたとき、暫定の GPS 座標を施設の正確な座標に置き換える）。
   * 表示・地図ピン・30m 重複判定の精度が上がる。
   */
  @Query("UPDATE places SET latitude = :latitude, longitude = :longitude, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateCoordinates(id: Long, latitude: Double, longitude: Double, updatedAt: Date)

  @Query("DELETE FROM places WHERE id = :id")
  suspend fun deleteById(id: Long)
}
