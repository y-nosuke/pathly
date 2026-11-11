package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.Date

/** 地域をまだ引いていない場所（アンカー座標つき）。 */
data class UnresolvedRegionRow(
  val placeId: Long,
  val latitude: Double,
  val longitude: Double,
)

/**
 * 場所の地域（`places.prefecture` / `city` / `regionResolvedAt`）を引いて書き足すための問い合わせ（→ adr/0029）。
 * 地域だけを書き換え、`updatedAt`（自分で編集した日時）は動かさない。
 */
@Dao
interface PlaceRegionDao {
  /** 地域をまだ引いていない場所のうち、id が [afterId] より後のもの（古い順に [limit] 件）。 */
  @Query(
    "SELECT id AS placeId, latitude, longitude FROM places " +
      "WHERE regionResolvedAt IS NULL AND id > :afterId ORDER BY id LIMIT :limit",
  )
  suspend fun getUnresolved(afterId: Long, limit: Int): List<UnresolvedRegionRow>

  /** 地域をまだ引いていない場所の数（増えたらキャッチアップを予約する）。 */
  @Query("SELECT COUNT(*) FROM places WHERE regionResolvedAt IS NULL")
  fun observeUnresolvedCount(): Flow<Int>

  /** 引いた地域を書き足す。分からなかったときも [resolvedAt] を入れて、引き直さないようにする。 */
  @Query("UPDATE places SET prefecture = :prefecture, city = :city, regionResolvedAt = :resolvedAt WHERE id = :placeId")
  suspend fun updateRegion(placeId: Long, prefecture: String?, city: String?, resolvedAt: Date)
}
