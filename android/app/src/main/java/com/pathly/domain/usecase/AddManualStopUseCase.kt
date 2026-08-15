package com.pathly.domain.usecase

import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.repository.PlaceRepository
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 手動で立ち寄り（訪問）を追加する手順。
 *
 * 「近くに既存の場所があれば紐付けるか確認する」という判断が、記録画面と経路詳細の
 * Composable にそれぞれ書かれていた（保留内容を持つ private data class も別々に定義されていた）。
 * 場所登録側（[PlaceEditUseCase.registerWithNearbyCheck]）と同じ形でここに集約する。
 */
@Singleton
class AddManualStopUseCase @Inject constructor(
  private val placeRepository: PlaceRepository,
) {

  /** 追加の結果。 */
  sealed interface AddResult {
    /** 追加できた。 */
    data class Added(val stopId: Long) : AddResult

    /**
     * 近く（検出半径）に既存の場所が見つかったので、まだ追加していない。
     * 紐付けるか新規で作るかをユーザーに選ばせ、[addForExistingPlace] か [addAsNew] を呼ぶ。
     */
    data class NearbyFound(val nearby: RegisteredPlace) : AddResult
  }

  /**
   * 立ち寄りを追加する。近接確認の要否まで含めて判断する。
   *
   * - POI（[googlePlaceId] あり）は施設の同一性で同定するので確認しない
   * - [nearbyAlreadyVisible] が true（登録済みの場所を地図に表示中）なら、ユーザーは
   *   近くの既存をその目で見たうえで地点を指しているので確認しない
   * - それ以外は近くの既存を探し、あれば [AddResult.NearbyFound] を返して判断を委ねる
   */
  suspend fun addWithNearbyCheck(
    trackId: Long,
    latitude: Double,
    longitude: Double,
    arrivalTime: Date,
    departureTime: Date,
    name: String?,
    googlePlaceId: String?,
    nearbyAlreadyVisible: Boolean,
    googleName: String? = null,
  ): AddResult {
    if (googlePlaceId == null && !nearbyAlreadyVisible) {
      placeRepository.findNearbyPlace(latitude, longitude)?.let { return AddResult.NearbyFound(it) }
    }
    val stopId = placeRepository.addManualStop(
      trackId,
      latitude,
      longitude,
      arrivalTime,
      departureTime,
      name,
      googlePlaceId,
      forceNewPlace = false,
      googleName = googleName,
    )
    return AddResult.Added(stopId)
  }

  /** 近接確認で「新規で追加」を選んだとき。座標同定せず必ず新しい場所を作る。 */
  suspend fun addAsNew(
    trackId: Long,
    latitude: Double,
    longitude: Double,
    arrivalTime: Date,
    departureTime: Date,
    name: String?,
  ): AddResult.Added = AddResult.Added(
    placeRepository.addManualStop(
      trackId,
      latitude,
      longitude,
      arrivalTime,
      departureTime,
      name,
      googlePlaceId = null,
      forceNewPlace = true,
    ),
  )

  /**
   * 既存の場所にこの訪問を紐付ける（新しい場所は作らない）。
   * 近接確認で「紐付け」を選んだときと、地図の登録済みマーカーから追加したときに使う。
   */
  suspend fun addForExistingPlace(
    trackId: Long,
    placeId: Long,
    arrivalTime: Date,
    departureTime: Date,
  ): AddResult.Added = AddResult.Added(placeRepository.addManualStopForPlace(trackId, placeId, arrivalTime, departureTime))
}
