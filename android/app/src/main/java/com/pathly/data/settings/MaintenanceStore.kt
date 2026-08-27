package com.pathly.data.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一度きりのデータ修復が済んだかどうかの覚え書き（SharedPreferences）。
 *
 * ユーザー設定（[SettingsRepository]）とはファイルを分ける。見せる設定ではなく、
 * 「保存済みのデータがどの世代の計算で作られたか」というアプリの内部事情だから。
 */
@Singleton
class MaintenanceStore @Inject constructor(
  @param:ApplicationContext context: Context,
) {
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  /**
   * 保存済みの補正点（smoothed_points）を作った平滑化の世代
   * （`TrackSmoother.GENERATION`）。0 は「記録していない＝欠落をまたいで平均していた頃」。
   */
  var smoothingGeneration: Int
    get() = prefs.getInt(KEY_SMOOTHING_GENERATION, 0)
    set(value) = prefs.edit { putInt(KEY_SMOOTHING_GENERATION, value) }

  private companion object {
    const val PREFS_NAME = "pathly_maintenance"
    const val KEY_SMOOTHING_GENERATION = "smoothing_generation"
  }
}
