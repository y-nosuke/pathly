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
import com.pathly.data.places.PlaceRegionResolver
import com.pathly.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException

/**
 * 地域（都道府県・市区町村）をまだ引いていない場所を、ネットワークに繋がっているときに引くキャッチアップ
 * （→ adr/0029）。名前のキャッチアップ（[PlaceNameCatchUpWorker]）と同じ作り。
 *
 * 地域の無い場所が出てきたら予約する（[com.pathly.PathlyApplication] が数を見ている）。
 * 走っている間に増えた場所も、引くものが無くなるまで続けて引く。
 */
@HiltWorker
class PlaceRegionCatchUpWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val resolver: PlaceRegionResolver,
) : CoroutineWorker(appContext, params) {

  private val logger = Logger("PlaceRegionCatchUpWorker")

  override suspend fun doWork(): Result = try {
    resolver.resolveAll()
    Result.success()
  } catch (e: CancellationException) {
    // WorkManager によるキャンセル。失敗と取り違えて再試行を積まないよう素通しする。
    throw e
  } catch (e: Exception) {
    // 一時的な失敗（通信断など）は再試行に任せる。引き終えた分は残っている。
    logger.w("Place region catch-up failed; will retry", e)
    Result.retry()
  }

  companion object {
    private const val UNIQUE_NAME = "place_region_catch_up"

    private val logger = Logger("PlaceRegionCatchUpWorker")

    /**
     * キャッチアップを予約する。走っている最中なら、その後ろに 1 回つなぐ（走っている分が引き終えた直後に
     * 増えた場所を取りこぼさないため）。走っていなければ置き換える。
     */
    fun enqueue(context: Context) {
      val request = OneTimeWorkRequestBuilder<PlaceRegionCatchUpWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
      WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
      logger.i("Enqueued place region catch-up")
    }
  }
}
