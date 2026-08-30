package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pathly.data.local.entity.NamedPlaceRow
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
   * 矩形に入る場所を id 順に返す（近傍検索の**前段の絞り込み**）。
   * 矩形は円より広いので、正確な距離判定は呼び出し側で行うこと。
   * 座標索引が効くため、場所が増えても全表走査にならない。
   *
   * 見るのは `places` の**アンカー**（同定用・不変）。表示座標（Google 由来）は見ない（→ adr/0023）。
   * 記録中は位置バッチごとに引かれる**最も熱い経路**なので、索引が効く形を崩さないこと。
   */
  @Query(
    "SELECT * FROM places " +
      "WHERE latitude BETWEEN :minLatitude AND :maxLatitude " +
      "AND longitude BETWEEN :minLongitude AND :maxLongitude " +
      "ORDER BY id",
  )
  suspend fun getInBounds(
    minLatitude: Double,
    maxLatitude: Double,
    minLongitude: Double,
    maxLongitude: Double,
  ): List<PlaceEntity>

  /**
   * 矩形に入る場所を、Google 由来の情報付きで返す（候補の表示名を無料で再利用するため）。
   * place ごとに google_places を引き直す N+1 を避ける。
   *
   * 座標は**アンカー**（[getInBounds] と同じ同定の土俵で距離を測るため）。
   * Google の座標は、候補に焼き込んで表示へ引き継ぐために別列で持ち出す。
   */
  @Query(
    "SELECT p.id AS id, p.name AS name, p.latitude AS latitude, p.longitude AS longitude, " +
      "g.googlePlaceId AS googlePlaceId, g.name AS googleName, g.address AS googleAddress, " +
      "g.latitude AS googleLatitude, g.longitude AS googleLongitude, " +
      "c.code AS categoryCode, c.displayName AS categoryDisplayName " +
      "FROM places p LEFT JOIN google_places g ON g.placeId = p.id " +
      "LEFT JOIN google_place_categories c ON c.id = g.categoryId " +
      "WHERE p.latitude BETWEEN :minLatitude AND :maxLatitude " +
      "AND p.longitude BETWEEN :minLongitude AND :maxLongitude " +
      "ORDER BY p.id",
  )
  suspend fun getNamedPlacesInBounds(
    minLatitude: Double,
    maxLatitude: Double,
    minLongitude: Double,
    maxLongitude: Double,
  ): List<NamedPlaceRow>

  /**
   * 「場所」タブ用: 全ての場所を、Google 由来データ・行きたい登録（あれば）と立ち寄り件数付きで取得する。
   * places / google_places / wishlist / stops を明示的に参照するので、いずれの変更にもリアクティブに追従する。
   *
   * 座標は**表示座標**（Google の代表点 → 無ければアンカー）。同定用のアンカーそのものは返さない
   * （→ adr/0023）。
   */
  @Query(
    "SELECT p.id AS id, p.name AS name, " +
      "COALESCE(g.latitude, p.latitude) AS latitude, COALESCE(g.longitude, p.longitude) AS longitude, " +
      "p.note AS note, p.createdAt AS createdAt, p.updatedAt AS updatedAt, " +
      "g.googlePlaceId AS googlePlaceId, g.name AS googleName, g.address AS googleAddress, " +
      "c.code AS categoryCode, c.displayName AS categoryDisplayName, " +
      "w.id AS wishlistId, w.priority AS priority, v.markedAt AS markedVisitedAt, " +
      "(SELECT COUNT(*) FROM stops s WHERE s.placeId = p.id) AS visitCount, " +
      "(SELECT MAX(s.arrivalTime) FROM stops s WHERE s.placeId = p.id) AS lastStopAt " +
      "FROM places p " +
      "LEFT JOIN google_places g ON g.placeId = p.id " +
      "LEFT JOIN google_place_categories c ON c.id = g.categoryId " +
      "LEFT JOIN wishlist w ON w.placeId = p.id " +
      "LEFT JOIN visited_places v ON v.placeId = p.id " +
      "ORDER BY p.createdAt DESC",
  )
  fun getPlacesWithWishlist(): Flow<List<PlaceWithWishlist>>

  /** [getPlacesWithWishlist] の単一取得版（記録画面などで既存 place の現在値を編集するため）。 */
  @Query(
    "SELECT p.id AS id, p.name AS name, " +
      "COALESCE(g.latitude, p.latitude) AS latitude, COALESCE(g.longitude, p.longitude) AS longitude, " +
      "p.note AS note, p.createdAt AS createdAt, p.updatedAt AS updatedAt, " +
      "g.googlePlaceId AS googlePlaceId, g.name AS googleName, g.address AS googleAddress, " +
      "c.code AS categoryCode, c.displayName AS categoryDisplayName, " +
      "w.id AS wishlistId, w.priority AS priority, v.markedAt AS markedVisitedAt, " +
      "(SELECT COUNT(*) FROM stops s WHERE s.placeId = p.id) AS visitCount, " +
      "(SELECT MAX(s.arrivalTime) FROM stops s WHERE s.placeId = p.id) AS lastStopAt " +
      "FROM places p " +
      "LEFT JOIN google_places g ON g.placeId = p.id " +
      "LEFT JOIN google_place_categories c ON c.id = g.categoryId " +
      "LEFT JOIN wishlist w ON w.placeId = p.id " +
      "LEFT JOIN visited_places v ON v.placeId = p.id " +
      "WHERE p.id = :id",
  )
  suspend fun getPlaceWithWishlist(id: Long): PlaceWithWishlist?

  @Query("SELECT * FROM places WHERE id = :id")
  suspend fun getById(id: Long): PlaceEntity?

  /**
   * 地図に出す「登録済みの場所」全件（USER・DETECTED どちらも）。名前は
   * places.name → google_places.name → google_places.address の順にフォールバック。
   * 状態（行きたい／訪問済み）を描き分けるため、wishlist 件数・立ち寄り件数・手動訪問日時も返す。
   *
   * 座標は**表示座標**（Google の代表点 → 無ければアンカー）。地図に出す位置だから（→ adr/0023）。
   */
  @Query(
    "SELECT p.id AS placeId, COALESCE(p.name, g.name, g.address) AS name, " +
      "COALESCE(g.latitude, p.latitude) AS latitude, COALESCE(g.longitude, p.longitude) AS longitude, " +
      "(SELECT COUNT(*) FROM wishlist w WHERE w.placeId = p.id) AS wishlistCount, " +
      "(SELECT COUNT(*) FROM stops s WHERE s.placeId = p.id) AS visitCount, " +
      "(SELECT v.markedAt FROM visited_places v WHERE v.placeId = p.id LIMIT 1) AS markedVisitedAt, " +
      "c.code AS categoryCode " +
      "FROM places p LEFT JOIN google_places g ON g.placeId = p.id " +
      "LEFT JOIN google_place_categories c ON c.id = g.categoryId",
  )
  fun observeRegisteredPlaces(): Flow<List<RegisteredPlaceRow>>

  /**
   * [observeRegisteredPlaces] の一回取得版を、矩形で絞って返す（近接確認用）。
   * 以前は全場所を件数の副問い合わせ付きで読んでから距離で絞っていた。
   *
   * 「地図で見えているピンの近くに登録済みがあるか」を問う機能なので、**表示座標**で絞る
   * （→ adr/0023）。COALESCE を挟むため `places(latitude, longitude)` の索引は効かないが、
   * これはタップ 1 回につき 1 度の問い合わせで、記録中に毎バッチ引かれる [getInBounds]（同定・
   * アンカー）とは別。熱い方の索引は保たれる。
   */
  @Query(
    "SELECT p.id AS placeId, COALESCE(p.name, g.name, g.address) AS name, " +
      "COALESCE(g.latitude, p.latitude) AS latitude, COALESCE(g.longitude, p.longitude) AS longitude, " +
      "(SELECT COUNT(*) FROM wishlist w WHERE w.placeId = p.id) AS wishlistCount, " +
      "(SELECT COUNT(*) FROM stops s WHERE s.placeId = p.id) AS visitCount, " +
      "(SELECT v.markedAt FROM visited_places v WHERE v.placeId = p.id LIMIT 1) AS markedVisitedAt, " +
      "c.code AS categoryCode " +
      "FROM places p LEFT JOIN google_places g ON g.placeId = p.id " +
      "LEFT JOIN google_place_categories c ON c.id = g.categoryId " +
      "WHERE COALESCE(g.latitude, p.latitude) BETWEEN :minLatitude AND :maxLatitude " +
      "AND COALESCE(g.longitude, p.longitude) BETWEEN :minLongitude AND :maxLongitude",
  )
  suspend fun getRegisteredPlacesInBounds(
    minLatitude: Double,
    maxLatitude: Double,
    minLongitude: Double,
    maxLongitude: Double,
  ): List<RegisteredPlaceRow>

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

  // 座標を更新する DAO は**置かない**。places の座標は同定に使うアンカーで、作成時に決まったら
  // 動かさない（→ adr/0023）。Google の座標は google_places 側に書く（GooglePlaceDao）。

  @Query("DELETE FROM places WHERE id = :id")
  suspend fun deleteById(id: Long)
}
