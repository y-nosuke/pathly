package com.pathly.presentation.places

import com.pathly.data.places.PlacesTextSearcher
import com.pathly.domain.model.NearbyRegisterPrompt
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.PlaceWithDistance
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.SameFacilityPlace
import com.pathly.domain.repository.WishlistRepository
import com.pathly.domain.usecase.PlaceEditUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 場所の登録・編集・削除・まとめる（場所の編集シートと地図からの登録の裏側）。
 * 場所タブ・記録画面・経路詳細で共有する（入口が違っても、できること・出る文言が変わらないように）。
 *
 * 近くに既存の場所があるときの確認待ち（[nearbyRegisterPrompt]）と削除の取り消し（[deleteUndo]）はこの部品が持つ。
 * 画面ごとに違うところは持ち主の ViewModel から受ける:
 * - [shownOnMap]: 近接確認で「地図に見えている」とみなす場所（押した時点の条件で判断する）。引数は場所詳細の地図から
 *   登録するときの、その場所（主マーカーで見えている）。
 * - [placeNameOf]: 削除の取り消しのスナックバーに出す名前。
 * - [onMessage]・[onError]: 結果の一言と失敗を出す先（画面ごとに出し方が違う）。
 * - [notifySaved]: 編集を保存したとき「保存しました」を出すか（場所タブは場所詳細を閉じて一覧に戻るので出さない）。
 */
class PlaceEditActions internal constructor(
  private val scope: CoroutineScope,
  private val placeEditUseCase: PlaceEditUseCase,
  private val wishlistRepository: WishlistRepository,
  private val placesTextSearcher: PlacesTextSearcher,
  private val shownOnMap: (mainPlaceId: Long?) -> (RegisteredPlace) -> Boolean,
  private val placeNameOf: (placeId: Long) -> String?,
  private val onMessage: (String) -> Unit,
  private val onError: (String) -> Unit,
  private val notifySaved: Boolean = true,
) {
  private val _nearbyRegisterPrompt = MutableStateFlow<NearbyRegisterPrompt?>(null)

  /** 空き地点の登録で近くに既存の場所が見つかったときの確認待ち（紐付け/新規をユーザーが選ぶ）。 */
  val nearbyRegisterPrompt: StateFlow<NearbyRegisterPrompt?> = _nearbyRegisterPrompt.asStateFlow()

  private val _deleteUndo = MutableStateFlow(PlaceDeleteUndo())

  /** 場所を削除した（まとめた）直後の取り消し待ち（スナックバーで「取り消す」を出す）。 */
  val deleteUndo: StateFlow<PlaceDeleteUndo> = _deleteUndo.asStateFlow()

  /** 失敗を [onError] に出す。成功したかどうかは呼び出し側で [onMessage] などに出す。 */
  private fun launchCatching(failure: String, block: suspend () -> Unit) {
    scope.launch {
      try {
        block()
      } catch (e: Exception) {
        onError("$failure: ${e.message}")
      }
    }
  }

  // ---- 登録 ----

  /**
   * 地図タップ・検索からの場所登録。近くに既存の場所があれば登録せず確認待ちにする。
   * 「登録済みの場所」を地図に表示中なら、ユーザーは既存を見たうえでの操作なので確認しない。
   * [knownDetails] は検索結果のように施設情報を取得済みのとき（Google を引き直さない）。
   */
  fun registerPlaceWithNearbyCheck(
    latitude: Double,
    longitude: Double,
    name: String?,
    wishlist: Boolean,
    priority: Priority,
    visited: Boolean,
    memo: String?,
    googlePlaceId: String?,
    googleName: String? = null,
    knownDetails: PlaceSearchResult? = null,
    // 場所詳細の地図から登録するときの、その場所（主マーカーで見えているので近接確認の対象にしない）。
    mainPlaceId: Long? = null,
  ) {
    // 押した時点の条件で判断する。
    val isShownOnMap = shownOnMap(mainPlaceId)
    launchCatching(REGISTER_FAILED) {
      val result = placeEditUseCase.registerWithNearbyCheck(
        latitude,
        longitude,
        name,
        wishlist,
        priority,
        visited,
        memo,
        googlePlaceId,
        isShownOnMap = isShownOnMap,
        knownDetails = knownDetails,
        googleName = googleName,
      )
      when (result) {
        is PlaceEditUseCase.RegisterResult.NearbyFound ->
          _nearbyRegisterPrompt.value = NearbyRegisterPrompt(result.nearby, latitude, longitude, name, wishlist, priority, visited, memo)

        is PlaceEditUseCase.RegisterResult.Registered -> onMessage(registeredMessage(result.alreadyExisted, name))
      }
    }
  }

  /** 近接確認で「この場所に紐付け」を選んだとき: 既存 place に行きたい/メモを反映する（新規は作らない）。 */
  fun confirmNearbyLink() {
    val prompt = _nearbyRegisterPrompt.value ?: return
    _nearbyRegisterPrompt.value = null
    launchCatching("紐付けに失敗しました") {
      placeEditUseCase.linkToExisting(prompt.nearby.placeId, prompt.wishlist, prompt.priority, prompt.visited, prompt.memo)
      onMessage("この場所に紐付けました")
    }
  }

  /** 近接確認で「新規で登録」を選んだとき。座標同定せず必ず新しい場所を作る。 */
  fun confirmNearbyNew() {
    val prompt = _nearbyRegisterPrompt.value ?: return
    _nearbyRegisterPrompt.value = null
    launchCatching(REGISTER_FAILED) {
      val reg = placeEditUseCase.register(
        prompt.latitude,
        prompt.longitude,
        prompt.name,
        prompt.wishlist,
        prompt.priority,
        prompt.visited,
        prompt.memo,
        googlePlaceId = null,
        forceNewPlace = true,
      )
      onMessage(registeredMessage(reg.alreadyExisted, prompt.name))
    }
  }

  fun dismissNearbyPrompt() {
    _nearbyRegisterPrompt.value = null
  }

  // ---- 編集 ----

  /** 編集シートで既存 place を購読する（保存しても値が古くならない）。 */
  fun observePlace(placeId: Long): Flow<PlaceListItem?> = wishlistRepository.observePlace(placeId)

  /** その場所を含むお出掛けの一覧（編集シートの訪問履歴）。 */
  fun visitsFor(placeId: Long): Flow<List<PlaceVisit>> = wishlistRepository.getVisits(placeId)

  /**
   * 編集を一括で保存する（名前・メモ・行きたい・優先度・訪問済み・Google 施設の紐付けを差分適用）。
   * 同じ施設の場所が既にあって紐付けを断られたときは、編集部が選択肢のダイアログを出す（→ adr/0037）。
   */
  fun savePlaceEdits(
    item: PlaceListItem,
    name: String,
    note: String,
    wishlist: Boolean,
    priority: Priority,
    visited: Boolean,
    link: PlaceSearchResult? = null,
  ) {
    launchCatching(SAVE_FAILED) {
      placeEditUseCase.saveEdits(item, name, note, wishlist, priority, visited, link)
      if (notifySaved) onMessage("保存しました")
    }
  }

  /** 場所シートのプレビュー用: placeId から施設情報（カテゴリ等）を取得する。 */
  suspend fun fetchPoiDetails(googlePlaceId: String): PlaceSearchResult? = wishlistRepository.fetchPlaceDetails(googlePlaceId)

  /** 「Googleで情報を取得」・選び直し: 座標の近くの POI 候補を取得する。 */
  suspend fun nearbyPois(latitude: Double, longitude: Double): List<PlaceSearchResult> = wishlistRepository.nearbyPois(latitude, longitude)

  /** 「Googleで情報を取得」の名前検索フォールバック: キーワード候補を返す（座標がずれて周辺に出ない施設用）。 */
  suspend fun predictPlaces(query: String): List<PlacePrediction> = placesTextSearcher.predict(query)

  /** 名前検索で選んだ候補を、座標つきの施設情報に確定する。 */
  suspend fun fetchPlaceResult(placeId: String): PlaceSearchResult? = placesTextSearcher.fetch(placeId)

  // ---- 削除・まとめる ----

  /**
   * 場所そのものを削除する（行きたい登録・解決ログも一緒に消える）。確認ダイアログは出さず即時削除し、
   * スナックバーの「取り消す」（[undoDelete]）で戻せる。
   */
  fun deletePlace(placeId: Long) {
    val name = placeNameOf(placeId)
    launchCatching("削除に失敗しました") {
      wishlistRepository.deletePlace(placeId)
      _deleteUndo.update { it.deleted(name) }
    }
  }

  /** 削除の通知を出し終えた（画面に戻るたび再表示しないよう消化する）。 */
  fun consumeDeleteUndo() {
    _deleteUndo.update { it.shown() }
  }

  /** 直近の削除（[isMerge] ならまとめ）を取り消して元に戻す（スナックバーの「取り消す」）。 */
  fun undoDelete(isMerge: Boolean = false) {
    launchCatching("取り消しに失敗しました") {
      if (isMerge) placeEditUseCase.undoMerge() else wishlistRepository.undoLastPlaceDeletion()
    }
  }

  /** 同じ施設と分かった場所を相手にまとめる（→ adr/0037）。削除と同じスナックバーの「取り消す」で戻せる。 */
  fun mergeIntoSameFacility(item: PlaceListItem, other: SameFacilityPlace) {
    mergeIntoSameFacility(item.place.id, item.displayName, other)
  }

  /** [placeId] を相手 [other] にまとめる（場所シートの編集部・同じ施設の確認待ちのダイアログの両方から）。 */
  fun mergeIntoSameFacility(placeId: Long, placeName: String?, other: SameFacilityPlace) {
    launchCatching("まとめられませんでした") {
      if (placeEditUseCase.mergeIntoSameFacility(placeId, other.placeId)) {
        _deleteUndo.update { it.merged(placeName, other.name) }
      }
    }
  }

  /** 手でまとめる相手の候補（場所の編集の「ほかの場所とまとめる」）。近くのほかの場所を近い順に返す。 */
  suspend fun mergeCandidates(item: PlaceListItem): List<PlaceWithDistance> = placeEditUseCase.findMergeCandidates(item)

  /** 同じ施設の相手がいても、施設情報なしで別の場所として残す（→ adr/0037）。二度と聞かない。 */
  fun keepAsSeparatePlace(placeId: Long) {
    launchCatching(SAVE_FAILED) {
      placeEditUseCase.keepAsSeparatePlace(placeId)
      onMessage("別の場所として残しました")
    }
  }

  private fun registeredMessage(alreadyExisted: Boolean, name: String?): String = if (alreadyExisted) {
    "この場所は登録済みです"
  } else {
    "「${name?.ifBlank { null } ?: "場所"}」を登録しました"
  }

  private companion object {
    const val REGISTER_FAILED = "場所の登録に失敗しました"
    const val SAVE_FAILED = "保存に失敗しました"
  }
}
