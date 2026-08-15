package com.pathly.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pathly.data.local.dao.GooglePlaceCategoryDao
import com.pathly.data.local.dao.GooglePlaceDao
import com.pathly.data.local.dao.idOf
import com.pathly.data.places.PlacesTextSearcher
import com.pathly.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException

/**
 * TODO(v13-backfill): **移行が済んだら、このファイルごと削除する暫定処理。**
 * 併せて消すもの:
 *  - [PlacesTextSearcher.fetchCategoryOnly]
 *  - [GooglePlaceDao.getWithoutCategory]
 *  - [com.pathly.PathlyApplication] の [enqueue] 呼び出し
 *
 * DB v13 で場所の業種を `google_place_categories` に正規化したが、v13 より前に解決した
 * `google_places` は表示名（「カフェ」）しか持っておらず、そこから機械可読な code（`cafe`）を
 * 復元できない。マイグレーションでは列ごと捨てているので、ここで Google から引き直して埋める。
 *
 * 通常の自動命名は「place 1 件 1 回」（adr/0014）で止まるため、既存の場所はこの経路でしか
 * 埋まらない。逆に言えばこれは**その方針への一度きりの例外**であり、恒久的な仕組みではない。
 * 呼び出しは対象 1 件につき Place Details（Pro）1 回。
 */
@HiltWorker
class PlaceCategoryBackfillWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val googlePlaceDao: GooglePlaceDao,
  private val googlePlaceCategoryDao: GooglePlaceCategoryDao,
  private val placesTextSearcher: PlacesTextSearcher,
) : CoroutineWorker(appContext, params) {

  private val logger = Logger("PlaceCategoryBackfillWorker")

  override suspend fun doWork(): Result = try {
    val targets = googlePlaceDao.getWithoutCategory()
    if (targets.isEmpty()) {
      logger.i("No places need category backfill")
      return Result.success()
    }
    logger.i("Backfilling categories for ${targets.size} places")

    // 引けなかった件があれば、埋まった分は残したまま後で再試行する。既に埋めた place は
    // 次回 getWithoutCategory() に出てこないので、同じ場所を二度課金することはない。
    var failures = 0
    var filled = 0
    for (target in targets) {
      placesTextSearcher.fetchCategoryOnly(target.googlePlaceId)
        .onSuccess { category ->
          // 業種を持たない施設もある。その場合は null のままで、成功として扱う
          // （失敗にすると再試行が永久に止まらない）。
          if (category != null) {
            googlePlaceDao.updateCategory(target.placeId, googlePlaceCategoryDao.idOf(category))
            filled++
          }
        }
        .onFailure { failures++ }
    }

    logger.i("Category backfill: filled=$filled, failed=$failures, total=${targets.size}")
    if (failures > 0) Result.retry() else Result.success()
  } catch (e: CancellationException) {
    // WorkManager によるキャンセル。Kotlin では CancellationException も Exception なので、
    // 下の catch に落とすと「失敗」と誤認して再試行を積んでしまう。素通しする。
    throw e
  } catch (e: Exception) {
    logger.w("Category backfill failed; will retry", e)
    Result.retry()
  }

  companion object {
    internal const val UNIQUE_NAME = "place_category_backfill"

    private val logger = Logger("PlaceCategoryBackfillWorker")

    /**
     * バックフィルを予約する。ネットワークが繋がったときに動く。
     * 同名の予約が既にあれば積み増さない（起動のたびに増殖させない）。
     */
    fun enqueue(context: Context) {
      val request = OneTimeWorkRequestBuilder<PlaceCategoryBackfillWorker>()
        .setConstraints(
          Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build(),
        )
        .build()
      WorkManager.getInstance(context)
        .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
      logger.i("Enqueued place category backfill")
    }
  }
}
