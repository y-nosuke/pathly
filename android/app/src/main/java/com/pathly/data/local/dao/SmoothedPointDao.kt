package com.pathly.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.pathly.data.local.entity.SmoothedPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmoothedPointDao {

  @Insert
  suspend fun insert(point: SmoothedPointEntity): Long

  @Insert
  suspend fun insertAll(points: List<SmoothedPointEntity>)

  @Query("SELECT * FROM smoothed_points WHERE trackId = :trackId ORDER BY seq ASC")
  suspend fun getByTrack(trackId: Long): List<SmoothedPointEntity>

  /**
   * 記録中のインクリメンタル検出用。境界（最後に確定した立ち寄りの departure ミリ秒）より
   * 後の補正点だけを返す。timestamp は epoch millis で保存されている（DateConverter）。
   */
  @Query("SELECT * FROM smoothed_points WHERE trackId = :trackId AND timestamp > :afterMillis ORDER BY seq ASC")
  suspend fun getByTrackAfter(trackId: Long, afterMillis: Long): List<SmoothedPointEntity>

  @Query("SELECT * FROM smoothed_points WHERE trackId = :trackId ORDER BY seq ASC")
  fun getByTrackFlow(trackId: Long): Flow<List<SmoothedPointEntity>>

  @Query("SELECT COUNT(*) FROM smoothed_points WHERE trackId = :trackId")
  suspend fun countByTrack(trackId: Long): Int

  @Query("DELETE FROM smoothed_points WHERE trackId = :trackId")
  suspend fun deleteByTrack(trackId: Long)

  /**
   * 経路の補正点を丸ごと差し替える（→ adr/0022 の作り直し）。
   *
   * **消すのと入れるのを 1 つのトランザクションにするのが要点。**別々に呼ぶと、その間に
   * プロセスが死んだとき 0 行のまま残り、次の起動では「欠落なし」と判定されて作り直しの
   * 対象から外れてしまう（地図はその場の計算で正しく出るが、焼き込んだ距離が古いまま残る）。
   */
  @Transaction
  suspend fun replaceForTrack(trackId: Long, points: List<SmoothedPointEntity>) {
    deleteByTrack(trackId)
    insertAll(points)
  }
}
