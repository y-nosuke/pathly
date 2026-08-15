package com.pathly.domain.usecase

import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceCategory
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlaceRegistration
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.Priority
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.repository.WishlistRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

/**
 * 場所の登録・紐付け・編集の手順を検証する。
 *
 * この手順はもともと記録画面・経路詳細・場所タブの3つの ViewModel に重複しており、
 * さらに近接確認の分岐は Composable 側にあったためテストが無かった。ここに集約したので、
 * このテストが3画面分の挙動をまとめて守る。
 */
class PlaceEditUseCaseTest {

  private val wishlistRepository = mockk<WishlistRepository>(relaxed = true)
  private val useCase = PlaceEditUseCase(wishlistRepository)

  @Before
  fun setup() {
    coEvery { wishlistRepository.registerPlace(any(), any(), any(), any(), any(), any(), any(), any()) } returns
      PlaceRegistration(placeId = 100L, alreadyExisted = false)
    coEvery { wishlistRepository.addToWishlist(any(), any()) } returns 200L
  }

  // ---- 近接確認つきの登録（もとは Composable に書かれていた分岐） ----

  @Test
  fun `POIなら近接確認せず施設同定で登録する`() = runTest {
    val result = useCase.registerWithNearbyCheck(
      latitude = 35.0,
      longitude = 139.0,
      name = "カフェ",
      wishlist = false,
      priority = Priority.MEDIUM,
      memo = null,
      googlePlaceId = "gp-1",
      nearbyAlreadyVisible = false,
    )

    assertTrue(result is PlaceEditUseCase.RegisterResult.Registered)
    // 施設の同一性で同定するので forceNewPlace は false。
    coVerify { wishlistRepository.registerPlace(35.0, 139.0, "カフェ", null, "gp-1", false, null) }
    // POI は確認不要なので近くの既存は探さない。
    coVerify(exactly = 0) { wishlistRepository.findNearbyPlace(any(), any()) }
  }

  @Test
  fun `検索結果は取得済みの施設情報をそのまま渡す`() = runTest {
    val known = PlaceSearchResult(
      googlePlaceId = "gp-1",
      name = "カフェ",
      address = "東京都…",
      category = PlaceCategory("cafe", "カフェ"),
      latitude = 35.0,
      longitude = 139.0,
    )

    useCase.registerWithNearbyCheck(
      latitude = 35.0,
      longitude = 139.0,
      name = null,
      wishlist = false,
      priority = Priority.MEDIUM,
      memo = null,
      googlePlaceId = "gp-1",
      nearbyAlreadyVisible = false,
      knownDetails = known,
    )

    // 検索時に取得済みなので、登録でもう一度 Google を引かせない。
    coVerify { wishlistRepository.registerPlace(35.0, 139.0, null, null, "gp-1", false, known) }
  }

  @Test
  fun `登録済みを地図表示中なら近接確認せず新規で登録する`() = runTest {
    val result = useCase.registerWithNearbyCheck(
      latitude = 35.0,
      longitude = 139.0,
      name = null,
      wishlist = false,
      priority = Priority.MEDIUM,
      memo = null,
      googlePlaceId = null,
      nearbyAlreadyVisible = true,
    )

    assertTrue(result is PlaceEditUseCase.RegisterResult.Registered)
    coVerify(exactly = 0) { wishlistRepository.findNearbyPlace(any(), any()) }
    coVerify { wishlistRepository.registerPlace(35.0, 139.0, null, null, null, true, null) }
  }

  @Test
  fun `近くに既存があれば登録せず確認を返す`() = runTest {
    val nearby = RegisteredPlace(placeId = 5L, name = "隣の店", latitude = 35.0, longitude = 139.0)
    coEvery { wishlistRepository.findNearbyPlace(any(), any()) } returns nearby

    val result = useCase.registerWithNearbyCheck(
      latitude = 35.0,
      longitude = 139.0,
      name = null,
      wishlist = false,
      priority = Priority.MEDIUM,
      memo = null,
      googlePlaceId = null,
      nearbyAlreadyVisible = false,
    )

    assertEquals(PlaceEditUseCase.RegisterResult.NearbyFound(nearby), result)
    // ユーザーが選ぶまで登録しない。
    coVerify(exactly = 0) { wishlistRepository.registerPlace(any(), any(), any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `近くに既存が無ければそのまま新規で登録する`() = runTest {
    coEvery { wishlistRepository.findNearbyPlace(any(), any()) } returns null

    val result = useCase.registerWithNearbyCheck(
      latitude = 35.0,
      longitude = 139.0,
      name = null,
      wishlist = false,
      priority = Priority.MEDIUM,
      memo = null,
      googlePlaceId = null,
      nearbyAlreadyVisible = false,
    )

    assertTrue(result is PlaceEditUseCase.RegisterResult.Registered)
    coVerify { wishlistRepository.registerPlace(35.0, 139.0, null, null, null, true, null) }
  }

  @Test
  fun `行きたいONのときだけ wishlist に入れる`() = runTest {
    useCase.register(35.0, 139.0, null, wishlist = false, Priority.HIGH, null, null, forceNewPlace = true)
    coVerify(exactly = 0) { wishlistRepository.addToWishlist(any(), any()) }

    useCase.register(35.0, 139.0, null, wishlist = true, Priority.HIGH, null, null, forceNewPlace = true)
    coVerify { wishlistRepository.addToWishlist(100L, Priority.HIGH) }
  }

  // ---- 既存の場所への紐付け ----

  @Test
  fun `紐付けはメモが空なら書き込まない`() = runTest {
    useCase.linkToExisting(placeId = 5L, wishlist = false, priority = Priority.MEDIUM, memo = "  ")

    coVerify(exactly = 0) { wishlistRepository.updatePlaceNote(any(), any()) }
    coVerify(exactly = 0) { wishlistRepository.addToWishlist(any(), any()) }
  }

  @Test
  fun `紐付けでメモと行きたいを反映する`() = runTest {
    useCase.linkToExisting(placeId = 5L, wishlist = true, priority = Priority.LOW, memo = "また来たい")

    coVerify { wishlistRepository.updatePlaceNote(5L, "また来たい") }
    coVerify { wishlistRepository.addToWishlist(5L, Priority.LOW) }
  }

  // ---- 編集の差分適用 ----

  private fun item(
    name: String? = "元の名前",
    note: String? = null,
    wishlistId: Long? = null,
    priority: Priority? = null,
    visitedAt: Date? = null,
    visitCount: Int = 0,
  ) = PlaceListItem(
    place = Place(
      id = 1L,
      name = name,
      latitude = 35.0,
      longitude = 139.0,
      note = note,
      googleName = null,
      googleAddress = null,
      category = null,
      googlePlaceId = null,
      createdAt = Date(),
      updatedAt = Date(),
    ),
    wishlistId = wishlistId,
    priority = priority,
    visitedAt = visitedAt,
    visitCount = visitCount,
  )

  @Test
  fun `編集_変更が無ければ何も書き込まない`() = runTest {
    useCase.saveEdits(item(), name = "元の名前", note = "", wishlist = false, priority = Priority.MEDIUM, visited = false)

    coVerify(exactly = 0) { wishlistRepository.renamePlace(any(), any()) }
    coVerify(exactly = 0) { wishlistRepository.updatePlaceNote(any(), any()) }
    coVerify(exactly = 0) { wishlistRepository.addToWishlist(any(), any()) }
    coVerify(exactly = 0) { wishlistRepository.removeFromWishlist(any()) }
  }

  @Test
  fun `編集_行きたいを新規に付けたら返ったIDで訪問済みも設定する`() = runTest {
    useCase.saveEdits(item(), name = "元の名前", note = "", wishlist = true, priority = Priority.HIGH, visited = true)

    coVerify { wishlistRepository.addToWishlist(1L, Priority.HIGH) }
    // 個別に呼ぶと新規の wishlistId を掴めないので、ひと続きで処理する必要がある。
    coVerify { wishlistRepository.setVisited(200L, true) }
  }

  @Test
  fun `編集_立ち寄り記録があるときは手動の訪問済みを触らない`() = runTest {
    useCase.saveEdits(
      item(wishlistId = 9L, priority = Priority.MEDIUM, visitCount = 3),
      name = "元の名前",
      note = "",
      wishlist = true,
      priority = Priority.MEDIUM,
      visited = false,
    )

    coVerify(exactly = 0) { wishlistRepository.setVisited(any(), any()) }
  }

  @Test
  fun `編集_優先度の変更分だけ反映する`() = runTest {
    useCase.saveEdits(
      item(wishlistId = 9L, priority = Priority.LOW),
      name = "元の名前",
      note = "",
      wishlist = true,
      priority = Priority.HIGH,
      visited = false,
    )

    coVerify { wishlistRepository.updateWishlist(9L, Priority.HIGH) }
  }

  @Test
  fun `編集_行きたいを外しても場所自体は残す`() = runTest {
    useCase.saveEdits(
      item(wishlistId = 9L, priority = Priority.LOW),
      name = "元の名前",
      note = "",
      wishlist = false,
      priority = Priority.LOW,
      visited = false,
    )

    coVerify { wishlistRepository.removeFromWishlist(9L) }
    coVerify(exactly = 0) { wishlistRepository.deletePlace(any()) }
  }

  @Test
  fun `編集_Google紐付けは名前やメモより先に適用する`() = runTest {
    val link = PlaceSearchResult(
      googlePlaceId = "gp-9",
      name = "スターバックス",
      address = "東京都",
      category = PlaceCategory("cafe", "カフェ"),
      latitude = 35.1,
      longitude = 139.1,
    )

    useCase.saveEdits(
      item(),
      name = "新しい名前",
      note = "",
      wishlist = false,
      priority = Priority.MEDIUM,
      visited = false,
      link = link,
    )

    coVerify { wishlistRepository.linkPlaceToGoogle(1L, link) }
    coVerify { wishlistRepository.renamePlace(1L, "新しい名前") }
  }
}
