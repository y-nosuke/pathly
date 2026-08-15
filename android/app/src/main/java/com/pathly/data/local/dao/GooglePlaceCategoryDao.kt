package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pathly.data.local.entity.GooglePlaceCategoryEntity
import com.pathly.domain.model.PlaceCategory

@Dao
interface GooglePlaceCategoryDao {

  @Query("SELECT * FROM google_place_categories WHERE id = :id")
  suspend fun getById(id: Long): GooglePlaceCategoryEntity?

  @Query("SELECT id FROM google_place_categories WHERE code = :code")
  suspend fun getIdByCode(code: String): Long?

  /** 既にある業種は無視する（同一性は code の UNIQUE 索引が担保する）。 */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertIgnore(category: GooglePlaceCategoryEntity): Long

  /** 表示名だけを更新する。null で上書きして消さないよう、値があるときだけ呼ぶこと。 */
  @Query("UPDATE google_place_categories SET displayName = :displayName WHERE id = :id")
  suspend fun updateDisplayName(id: Long, displayName: String)

  /**
   * 業種を確保して id を返す。無ければ作り、あれば表示名だけ最新に追随させる（Google 側の
   * 表記ゆれ・端末のロケール変更で変わりうるため）。表示名が取れなかった場合は既存を残す。
   */
  @Transaction
  suspend fun upsertAndGetId(code: String, displayName: String?): Long {
    val existingId = getIdByCode(code)
    if (existingId != null) {
      if (displayName != null) updateDisplayName(existingId, displayName)
      return existingId
    }
    val inserted = insertIgnore(GooglePlaceCategoryEntity(code = code, displayName = displayName))
    // 競合で無視された場合（-1）は、入れた側の id を引き直す。
    return if (inserted != -1L) inserted else getIdByCode(code)!!
  }
}

/**
 * 業種をマスタに確保して id を返す（無ければ作る）。Google が業種を返さなかった場合は null。
 * `google_places` を書く前に必ず通し、外部キーの参照先を用意する。
 */
suspend fun GooglePlaceCategoryDao.idOf(category: PlaceCategory?): Long? = category?.let { upsertAndGetId(it.code, it.displayName) }
