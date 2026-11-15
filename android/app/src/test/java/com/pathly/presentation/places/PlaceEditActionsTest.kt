package com.pathly.presentation.places

import com.pathly.data.places.PlacesTextSearcher
import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.SameFacilityPlace
import com.pathly.domain.repository.WishlistRepository
import com.pathly.domain.usecase.PlaceEditUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/** 場所の編集の部品（場所タブ・記録画面・経路詳細で共有）。画面ごとの違いは引数で受ける。 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaceEditActionsTest {

  private val useCase = mockk<PlaceEditUseCase>(relaxed = true)
  private val wishlistRepository = mockk<WishlistRepository>(relaxed = true)
  private val placesTextSearcher = mockk<PlacesTextSearcher>(relaxed = true)

  private val messages = mutableListOf<String>()
  private val errors = mutableListOf<String>()

  private val nearby = RegisteredPlace(placeId = 5L, name = "隣の店", latitude = 35.0, longitude = 139.0)

  private fun TestScope.actions(
    shownOnMap: (Long?) -> (RegisteredPlace) -> Boolean = { { false } },
    placeNameOf: (Long) -> String? = { null },
    notifySaved: Boolean = true,
  ) = PlaceEditActions(
    scope = this,
    placeEditUseCase = useCase,
    wishlistRepository = wishlistRepository,
    placesTextSearcher = placesTextSearcher,
    shownOnMap = shownOnMap,
    placeNameOf = placeNameOf,
    onMessage = { messages += it },
    onError = { errors += it },
    notifySaved = notifySaved,
  )

  private fun registerReturns(result: PlaceEditUseCase.RegisterResult) {
    coEvery {
      useCase.registerWithNearbyCheck(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    } returns result
  }

  private fun PlaceEditActions.registerAtPoint(name: String? = "新しい場所", mainPlaceId: Long? = null) = registerPlaceWithNearbyCheck(35.0, 139.0, name, true, Priority.HIGH, false, "メモ", null, mainPlaceId = mainPlaceId)

  // ---- 登録 ----

  @Test
  fun `登録_近くに既存があれば登録せず確認待ちにする`() = runTest {
    registerReturns(PlaceEditUseCase.RegisterResult.NearbyFound(nearby))
    val actions = actions()

    actions.registerAtPoint()
    advanceUntilIdle()

    val prompt = actions.nearbyRegisterPrompt.value
    assertEquals(nearby, prompt?.nearby)
    assertEquals("新しい場所", prompt?.name)
    assertEquals("メモ", prompt?.memo)
    assertTrue("まだ何も知らせない", messages.isEmpty())
  }

  @Test
  fun `登録_見えているかの判断は押した時点の画面の条件を使う`() = runTest {
    val isShown = slot<(RegisteredPlace) -> Boolean>()
    coEvery {
      useCase.registerWithNearbyCheck(any(), any(), any(), any(), any(), any(), any(), any(), capture(isShown), any(), any())
    } returns PlaceEditUseCase.RegisterResult.Registered(9L, alreadyExisted = false)
    var askedMain: Long? = null
    val actions = actions(shownOnMap = { main ->
      askedMain = main;
      { it.placeId == main }
    })

    actions.registerAtPoint(mainPlaceId = 5L)
    advanceUntilIdle()

    assertEquals("場所詳細の主マーカーを画面に渡す", 5L, askedMain)
    assertTrue(isShown.captured(nearby))
  }

  @Test
  fun `登録_確認が要らなければ名前つきで知らせる`() = runTest {
    registerReturns(PlaceEditUseCase.RegisterResult.Registered(9L, alreadyExisted = false))
    val actions = actions()

    actions.registerAtPoint(name = "カフェ")
    actions.registerAtPoint(name = " ")
    advanceUntilIdle()

    assertNull(actions.nearbyRegisterPrompt.value)
    assertEquals(listOf("「カフェ」を登録しました", "「場所」を登録しました"), messages)
  }

  @Test
  fun `登録_既存の場所に相乗りしたら登録済みと知らせる`() = runTest {
    registerReturns(PlaceEditUseCase.RegisterResult.Registered(9L, alreadyExisted = true))
    val actions = actions()

    actions.registerAtPoint()
    advanceUntilIdle()

    assertEquals(listOf("この場所は登録済みです"), messages)
  }

  @Test
  fun `登録_失敗の文言は3画面で同じ`() = runTest {
    coEvery {
      useCase.registerWithNearbyCheck(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    } throws IllegalStateException("boom")
    val actions = actions()

    actions.registerAtPoint()
    advanceUntilIdle()

    assertEquals(listOf("場所の登録に失敗しました: boom"), errors)
  }

  @Test
  fun `近接確認_新規を選べば確認を閉じて必ず新しい場所を作る`() = runTest {
    registerReturns(PlaceEditUseCase.RegisterResult.NearbyFound(nearby))
    coEvery {
      useCase.register(any(), any(), any(), any(), any(), any(), any(), any(), any())
    } returns PlaceEditUseCase.RegisterResult.Registered(9L, alreadyExisted = false)
    val actions = actions()
    actions.registerAtPoint()
    advanceUntilIdle()

    actions.confirmNearbyNew()
    advanceUntilIdle()

    assertNull("確認は閉じる", actions.nearbyRegisterPrompt.value)
    coVerify { useCase.register(35.0, 139.0, "新しい場所", true, Priority.HIGH, false, "メモ", null, true) }
    assertEquals(listOf("「新しい場所」を登録しました"), messages)
  }

  @Test
  fun `近接確認_紐付けを選べば既存の場所へ反映する`() = runTest {
    registerReturns(PlaceEditUseCase.RegisterResult.NearbyFound(nearby))
    val actions = actions()
    actions.registerAtPoint()
    advanceUntilIdle()

    actions.confirmNearbyLink()
    advanceUntilIdle()

    assertNull("確認は閉じる", actions.nearbyRegisterPrompt.value)
    coVerify { useCase.linkToExisting(5L, true, Priority.HIGH, false, "メモ") }
    assertEquals(listOf("この場所に紐付けました"), messages)
  }

  @Test
  fun `近接確認_閉じれば何もしない`() = runTest {
    registerReturns(PlaceEditUseCase.RegisterResult.NearbyFound(nearby))
    val actions = actions()
    actions.registerAtPoint()
    advanceUntilIdle()

    actions.dismissNearbyPrompt()
    actions.confirmNearbyLink()
    advanceUntilIdle()

    assertNull(actions.nearbyRegisterPrompt.value)
    coVerify(exactly = 0) { useCase.linkToExisting(any(), any(), any(), any(), any()) }
  }

  // ---- 編集 ----

  @Test
  fun `保存_知らせるかは画面が決める`() = runTest {
    val item = item(1L)

    actions().savePlaceEdits(item, "名前", "", false, Priority.MEDIUM, false)
    actions(notifySaved = false).savePlaceEdits(item, "名前", "", false, Priority.MEDIUM, false)
    advanceUntilIdle()

    coVerify(exactly = 2) { useCase.saveEdits(item, "名前", "", false, Priority.MEDIUM, false, null) }
    assertEquals(listOf("保存しました"), messages)
  }

  @Test
  fun `別の場所として残す`() = runTest {
    actions().keepAsSeparatePlace(3L)
    advanceUntilIdle()

    coVerify { useCase.keepAsSeparatePlace(3L) }
    assertEquals(listOf("別の場所として残しました"), messages)
  }

  // ---- 削除・まとめる ----

  @Test
  fun `削除_名前つきで取り消しを待ち、出し終えたら消化する`() = runTest {
    val actions = actions(placeNameOf = { if (it == 1L) "カフェ" else null })

    actions.deletePlace(1L)
    advanceUntilIdle()

    coVerify { wishlistRepository.deletePlace(1L) }
    val undo = actions.deleteUndo.value
    assertEquals(1, undo.token)
    assertEquals("カフェ", undo.name)
    assertFalse(undo.isMerge)

    actions.consumeDeleteUndo()
    assertEquals("消化すると出すものが無い状態に戻る", 0, actions.deleteUndo.value.token)
  }

  @Test
  fun `削除_失敗したら取り消しは出さない`() = runTest {
    coEvery { wishlistRepository.deletePlace(1L) } throws IllegalStateException("boom")
    val actions = actions()

    actions.deletePlace(1L)
    advanceUntilIdle()

    assertEquals(0, actions.deleteUndo.value.token)
    assertEquals(listOf("削除に失敗しました: boom"), errors)
  }

  @Test
  fun `まとめる_まとめられたら削除と同じく取り消しを待つ`() = runTest {
    coEvery { useCase.mergeIntoSameFacility(1L, 2L) } returns true
    val actions = actions()

    actions.mergeIntoSameFacility(item(1L, name = "カフェ"), SameFacilityPlace(2L, "本店"))
    advanceUntilIdle()

    val undo = actions.deleteUndo.value
    assertEquals(1, undo.token)
    assertEquals("カフェ", undo.name)
    assertEquals("本店", undo.mergedInto)
  }

  @Test
  fun `まとめる_まとめなかったら何も出さない`() = runTest {
    coEvery { useCase.mergeIntoSameFacility(any(), any()) } returns false
    val actions = actions()

    actions.mergeIntoSameFacility(1L, "カフェ", SameFacilityPlace(2L, "本店"))
    advanceUntilIdle()

    assertEquals(0, actions.deleteUndo.value.token)
  }

  @Test
  fun `取り消し_まとめならまとめを、削除なら削除を戻す`() = runTest {
    val actions = actions()

    actions.undoDelete(isMerge = true)
    advanceUntilIdle()
    coVerify(exactly = 1) { useCase.undoMerge() }
    coVerify(exactly = 0) { wishlistRepository.undoLastPlaceDeletion() }

    actions.undoDelete()
    advanceUntilIdle()
    coVerify(exactly = 1) { wishlistRepository.undoLastPlaceDeletion() }
  }

  private fun item(id: Long, name: String? = null) = PlaceListItem(
    place = Place(
      id = id,
      name = name,
      latitude = 35.0,
      longitude = 139.0,
      note = null,
      googleName = null,
      googleAddress = null,
      category = null,
      googlePlaceId = null,
      createdAt = Date(0),
      updatedAt = Date(0),
    ),
    wishlistId = null,
    priority = null,
    markedVisitedAt = null,
    visitCount = 0,
  )
}
