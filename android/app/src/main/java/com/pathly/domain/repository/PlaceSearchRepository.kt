package com.pathly.domain.repository

import com.pathly.domain.model.GeoBounds
import com.pathly.domain.model.MapRegion
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult

/**
 * キーワードから場所（施設・地域）を探す。外部の地図サービスに問い合わせるので、オフライン・失敗時は空や null を返す。
 *
 * 1 回の検索は [startSession] で始め、候補（[predict]）を何度引いても、確定（[fetch]・[fetchRegion]）でまとめて終える。
 */
interface PlaceSearchRepository {
  /** 検索画面を開いたときに呼ぶ。新しい検索のまとまりを始める。 */
  fun startSession()

  /**
   * キーワードの候補を返す。オフライン・失敗時は空リスト。
   * [locationBias] を渡すと、その範囲の中の候補を優先する（範囲の外も出る）。
   * 施設と地域を 1 回で取り、候補ごとの [PlacePrediction.isRegion] で見分ける。[regionsOnly] なら地域だけに絞る。
   */
  suspend fun predict(
    query: String,
    locationBias: GeoBounds? = null,
    regionsOnly: Boolean = false,
  ): List<PlacePrediction>

  /** 候補を確定して座標つきの結果を得る。検索のまとまりはここで終わる。オフライン・失敗時は null。 */
  suspend fun fetch(placeId: String): PlaceSearchResult?

  /** 地域の候補を確定して、その地域の範囲と中心を得る。検索のまとまりはここで終わる。オフライン・失敗時は null。 */
  suspend fun fetchRegion(placeId: String): MapRegion?

  /**
   * 地名から地域の範囲を引く（場所一覧の地域の見出しから地図で見るとき）。[topLevel] なら都道府県・国、
   * そうでなければ市区町村・都市の段の候補を選ぶ。[near] の中の候補を優先する。段の合う候補が無い・失敗時は null。
   */
  suspend fun findRegion(query: String, topLevel: Boolean, near: GeoBounds?): MapRegion?
}
