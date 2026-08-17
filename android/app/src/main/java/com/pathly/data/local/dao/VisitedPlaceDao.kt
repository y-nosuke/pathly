package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pathly.data.local.entity.VisitedPlaceEntity

/**
 * 手動の「訪問済み」の印。行の存在が訪問済みを表すので、付ける＝挿入・外す＝削除だけを扱う。
 * 既に印がある場所へもう一度付けても日時は上書きしない（IGNORE）。最初に付けた日時を残す。
 */
@Dao
interface VisitedPlaceDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insert(item: VisitedPlaceEntity): Long

  @Query("DELETE FROM visited_places WHERE placeId = :placeId")
  suspend fun deleteByPlaceId(placeId: Long)

  /** その place の印（場所を消すときの控え＝取り消しでの復元に使う）。 */
  @Query("SELECT * FROM visited_places WHERE placeId = :placeId LIMIT 1")
  suspend fun getByPlaceId(placeId: Long): VisitedPlaceEntity?

  /** その place に手動の印があるか（自動回収の可否判定にも使う）。 */
  @Query("SELECT COUNT(*) FROM visited_places WHERE placeId = :placeId")
  suspend fun countByPlace(placeId: Long): Int
}
