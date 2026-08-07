package com.pathly.domain.model

/**
 * 場所登録の結果。[alreadyExisted] は「同じ施設（googlePlaceId）が既に登録済みだった」ことを表し、
 * UI の「この場所は登録済みです」通知に使う。
 */
data class PlaceRegistration(
  val placeId: Long,
  val alreadyExisted: Boolean,
)
