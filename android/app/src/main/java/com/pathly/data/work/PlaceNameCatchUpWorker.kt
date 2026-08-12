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
import com.pathly.domain.repository.PlaceRepository
import com.pathly.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * オフラインで記録した立ち寄り場所の名前を、オンラインになってから解決するキャッチアップ。
 *
 * 以前は Application.onCreate で `appScope.launch { resolveAllUnresolvedNames() }` していた。
 * これだと起動時にオフラインだった場合、次にアプリを開くまで何も起きない（このアプリは
 * 「オフラインで記録 → オンラインで命名」という前提なので、その待ちがそのまま体験に出る）。
 * また、処理中にプロセスが死ねばやり直しの機会も無い。
 *
 * ネットワーク接続を制約にした WorkManager へ移すことで、接続が戻った時点で実行され、
 * 失敗しても指数バックオフで再試行される。
 */
@HiltWorker
class PlaceNameCatchUpWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val placeRepository: PlaceRepository,
) : CoroutineWorker(appContext, params) {

  private val logger = Logger("PlaceNameCatchUpWorker")

  override suspend fun doWork(): Result = try {
    placeRepository.resolveAllUnresolvedNames()
    Result.success()
  } catch (e: Exception) {
    // 一時的な失敗（通信断など）は再試行に任せる。
    logger.w("Place name catch-up failed; will retry", e)
    Result.retry()
  }

  companion object {
    internal const val UNIQUE_NAME = "place_name_catch_up"

    private val logger = Logger("PlaceNameCatchUpWorker")

    /**
     * キャッチアップを予約する。ネットワークが繋がったときに動く。
     * 同名の予約が既にあれば積み増さない（起動のたびに増殖させない）。
     */
    fun enqueue(context: Context) {
      val request = OneTimeWorkRequestBuilder<PlaceNameCatchUpWorker>()
        .setConstraints(
          Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build(),
        )
        .build()
      WorkManager.getInstance(context)
        .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
      logger.i("Enqueued place name catch-up")
    }
  }
}
