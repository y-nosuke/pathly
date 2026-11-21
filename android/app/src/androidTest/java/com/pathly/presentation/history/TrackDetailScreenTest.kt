package com.pathly.presentation.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.Place
import com.pathly.domain.model.PlacePrediction
import com.pathly.domain.model.PlaceSearchResult
import com.pathly.domain.model.RegisteredPlace
import com.pathly.domain.model.SameFacilityPending
import com.pathly.domain.model.SameFacilityPlace
import com.pathly.domain.model.Stop
import com.pathly.presentation.common.MapRegisteredPlaces
import com.pathly.presentation.places.PlaceEditUi
import com.pathly.presentation.stops.ManualStopOrigin
import com.pathly.presentation.stops.ManualStopTarget
import com.pathly.ui.theme.PathlyAndroidTheme
import com.pathly.util.DateFormatters
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class TrackDetailScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var mockOnBackClick: () -> Unit

  @Before
  fun setup() {
    mockOnBackClick = mockk(relaxed = true)
  }

  @Test
  fun trackDetailScreen_layers_registeredPlacesToggleAndFilterOnlyWhenShown() {
    val track = createSampleTrack()
    var show by mutableStateOf(false)

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapPlaces = MapRegisteredPlaces(shown = show, onToggle = { show = !show }),
          mapContent = {},
        )
      }
    }

    // 登録済みの場所の表示・絞り込みは、上のバーのレイヤー［≋］の中にある。
    composeTestRule.onNodeWithContentDescription("地図に重ねるもの").performClick()
    composeTestRule.onNodeWithTag("mapLayersPanel").assertIsDisplayed()
    // 表示 OFF のときは絞り込みを出さない。
    composeTestRule.onNodeWithText("行きたい").assertDoesNotExist()
    composeTestRule.onNodeWithText("登録済みの場所").performClick()
    composeTestRule.onNodeWithText("行きたい").assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_mapPlaceFilter_chipsChangeFilterAndActiveStateIsShown() {
    val track = createSampleTrack()
    var filter by mutableStateOf(MapPlaceFilter())

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapPlaces = MapRegisteredPlaces(shown = true, filter = filter, onFilterChange = { filter = it }),
          mapContent = {},
        )
      }
    }

    composeTestRule.onNodeWithContentDescription("地図に重ねるもの").performClick()
    // 行きたいのチップ: 指定なし → 行きたい → 行きたい以外。
    composeTestRule.onNodeWithText("行きたい").performClick()
    assertEquals(MapPlaceFilter(wishlisted = true), filter)
    composeTestRule.onNodeWithText("行きたい").performClick()
    assertEquals(MapPlaceFilter(wishlisted = false), filter)
    composeTestRule.onNodeWithText("行きたい以外").assertIsDisplayed()

    composeTestRule.onNodeWithText("すべて外す").performClick()
    assertEquals(MapPlaceFilter(), filter)
  }

  @Test
  fun trackDetailScreen_backButton_triggersCallback() {
    val track = createSampleTrack()

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
        )
      }
    }

    composeTestRule
      .onNodeWithContentDescription("戻る")
      .performClick()

    verify { mockOnBackClick() }
  }

  @Test
  fun trackDetailScreen_fitWholeTrackButton_isShownAndClickable() {
    val track = createSampleTrack()

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
        )
      }
    }

    // 押しても画面は抜けない（地図を合わせ直すだけ）。
    composeTestRule
      .onNodeWithContentDescription("経路の全体を見る")
      .assertIsDisplayed()
      .assertHasClickAction()
      .performClick()

    verify(exactly = 0) { mockOnBackClick() }
  }

  @Test
  fun trackDetailScreen_fitWholeTrackButton_hiddenWithoutPoints() {
    val track = GpsTrack(
      id = 1L,
      startTime = Date(),
      endTime = Date(),
      isActive = false,
      points = emptyList(),
      createdAt = Date(),
      updatedAt = Date(),
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
        )
      }
    }

    composeTestRule
      .onNodeWithContentDescription("経路の全体を見る")
      .assertDoesNotExist()
  }

  @Test
  fun trackDetailScreen_backButton_hasClickAction() {
    val track = createSampleTrack()

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
        )
      }
    }

    composeTestRule
      .onNodeWithContentDescription("戻る")
      .assertIsDisplayed()
      .assertHasClickAction()
  }

  @Test
  fun trackDetailScreen_bottomSheet_showsStatLabels() {
    val track = createSampleTrack()

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
        )
      }
    }

    // 1行スタッツに距離（km）と地点数（点）が出る
    composeTestRule
      .onNodeWithText("km", substring = true)
      .assertIsDisplayed()

    composeTestRule
      .onNodeWithText("点", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_bottomSheet_showsPointCountAndDistance() {
    val track = GpsTrack(
      id = 1L,
      startTime = Date(),
      endTime = Date(),
      isActive = false,
      points = createSamplePoints(8),
      createdAt = Date(),
      updatedAt = Date(),
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
        )
      }
    }

    // 1行スタッツの地点数
    composeTestRule
      .onNodeWithText("8点", substring = true)
      .assertIsDisplayed()

    // 距離は実際の計算結果に依存するので km 表示があることだけ確認
    composeTestRule
      .onNodeWithText("km", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_activeTrack_showsRecordingBadge() {
    val track = GpsTrack(
      id = 456L,
      startTime = Date(),
      endTime = null,
      isActive = true,
      points = createSamplePoints(3),
      createdAt = Date(),
      updatedAt = Date(),
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
        )
      }
    }

    // 記録中は上のバーの下に記録中のピル（経過時間つき）と、シートの時刻の欄（「– 記録中」）の2箇所に出る。
    // 部分一致だと両方に当たってしまうので、ピルの文字そのものを指す。
    composeTestRule
      .onNodeWithText("記録中")
      .assertIsDisplayed()
    composeTestRule
      .onNodeWithText("– 記録中", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_activeTrack_showsAddStopHereLikeRecordingScreen() {
    val track = GpsTrack(
      id = 456L,
      startTime = Date(),
      endTime = null,
      isActive = true,
      points = createSamplePoints(3),
      createdAt = Date(),
      updatedAt = Date(),
    )
    var target by mutableStateOf<ManualStopTarget?>(null)

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          addStopHereTarget = target,
          mapContent = {},
        )
      }
    }

    // 記録中は記録画面と同じ名前・同じ並び。現在地が取れるまでは「今ここ」を押せない。
    composeTestRule.onNodeWithText("今ここを立ち寄り").assertIsDisplayed().assertIsNotEnabled()
    composeTestRule.onNodeWithText("地図で指して追加").assertIsDisplayed()

    target = ManualStopTarget(35.0, 139.0, ManualStopOrigin.CurrentLocation)
    composeTestRule.onNodeWithText("今ここを立ち寄り").assertIsEnabled()
  }

  @Test
  fun trackDetailScreen_completedTrack_hasNoAddStopHere() {
    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = createSampleTrack(isActive = false),
          onBackClick = mockOnBackClick,
          // 現在地があっても、記録が終わった経路には出さない。
          addStopHereTarget = ManualStopTarget(35.0, 139.0, ManualStopOrigin.CurrentLocation),
          mapContent = {},
        )
      }
    }

    composeTestRule.onNodeWithText("今ここを立ち寄り").assertDoesNotExist()
    composeTestRule.onNodeWithText("地図で指して追加").assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_completedTrack_doesNotShowRecordingBadge() {
    val track = createSampleTrack(isActive = false)

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
        )
      }
    }

    composeTestRule
      .onNodeWithText("記録中", substring = true)
      .assertDoesNotExist()
  }

  @Test
  fun trackDetailScreen_emptyTrack_showsNoDataMessage() {
    val track = GpsTrack(
      id = 1L,
      startTime = Date(),
      endTime = Date(),
      isActive = false,
      points = emptyList(),
      createdAt = Date(),
      updatedAt = Date(),
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
        )
      }
    }

    composeTestRule
      .onNodeWithText("GPSデータがありません")
      .assertIsDisplayed()

    // 1行スタッツの距離は 0.0km
    composeTestRule
      .onNodeWithText("0.0km", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_duration_showsHoursAndMinutes() {
    val startTime = Date(1640995200000L) // 2022-01-01 00:00:00 UTC
    val endTime = Date(1640995200000L + 3661000L) // 1時間1分後

    val track = GpsTrack(
      id = 1L,
      startTime = startTime,
      endTime = endTime,
      isActive = false,
      points = createSamplePoints(2),
      createdAt = startTime,
      updatedAt = endTime,
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
        )
      }
    }

    // 所要時間は差分のためタイムゾーン非依存。サブタイトル内に含まれる
    composeTestRule
      .onNodeWithText("1時間1分", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_shortDuration_showsMinutesOnly() {
    val startTime = Date(1640995200000L) // 2022-01-01 00:00:00 UTC
    val endTime = Date(1640995200000L + 1800000L) // 30分後

    val track = GpsTrack(
      id = 1L,
      startTime = startTime,
      endTime = endTime,
      isActive = false,
      points = createSamplePoints(2),
      createdAt = startTime,
      updatedAt = endTime,
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
        )
      }
    }

    composeTestRule
      .onNodeWithText("30分", substring = true)
      .assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_showsStopWithPlaceName() {
    val track = createSampleTrack()
    val stops = listOf(sampleStop(name = "テストカフェ"))

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
          stops = stops,
        )
      }
    }

    composeTestRule.onNodeWithText("立ち寄り1件", substring = true).assertIsDisplayed()
    // 立ち寄り行はボトムシートのピーク（部分展開）より下にあり画面外になり得るため、
    // 表示（displayed）ではなく存在（exists）で検証する。
    composeTestRule.onNodeWithText("テストカフェ").assertExists()
  }

  @Test
  fun trackDetailScreen_editButton_opensDialogAndSaveTriggersCallback() {
    val onEdit = mockk<(Long, String) -> Unit>(relaxed = true)
    val track = createSampleTrack()
    val stops = listOf(sampleStop(placeId = 9L, name = "旧名"))

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
          stops = stops,
          stopActions = TrackStopActions(onEditPlaceName = onEdit),
        )
      }
    }

    // 行タップは地図フォーカス。名前編集は「操作」メニュー→「名前を編集」から。
    composeTestRule.onNodeWithContentDescription("操作").performClick()
    composeTestRule.onNodeWithText("名前を編集").performClick()
    composeTestRule.onNodeWithText("場所の名前").assertIsDisplayed()
    composeTestRule.onNodeWithText("保存").performClick()

    verify { onEdit(9L, "旧名") }
  }

  @Test
  fun trackDetailScreen_deleteButton_immediatelyTriggersDeleteStops() {
    val onDeleteStops = mockk<(List<Long>) -> Unit>(relaxed = true)
    val track = createSampleTrack()
    val stops = listOf(sampleStop(placeId = 9L, name = "テストカフェ"))

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
          stops = stops,
          stopActions = TrackStopActions(onDelete = onDeleteStops),
        )
      }
    }

    // 確認ダイアログは出さず、「操作」メニュー→「削除」で即座に削除（取り消しはスナックバーから）。
    composeTestRule.onNodeWithContentDescription("操作").performClick()
    composeTestRule.onNodeWithText("削除").performClick()

    verify { onDeleteStops(listOf(1L)) }
  }

  @Test
  fun trackDetailScreen_unresolvedPlaces_showsResolveButtonAndTriggersCallback() {
    val onResolve = mockk<() -> Unit>(relaxed = true)
    val track = createSampleTrack()
    val stops = listOf(sampleStop(name = null))

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
          stops = stops,
          unresolvedCount = 1,
          stopActions = TrackStopActions(onResolveNames = onResolve),
        )
      }
    }

    composeTestRule.onNodeWithText("場所を取得", substring = true).performClick()
    verify { onResolve() }
  }

  @Test
  fun trackDetailScreen_longPressStop_entersSelectionAndBatchDeleteTriggersCallback() {
    val onDeleteStops = mockk<(List<Long>) -> Unit>(relaxed = true)
    val track = createSampleTrack()
    val stops = listOf(sampleStop(placeId = 9L, name = "テストカフェ"))

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
          stops = stops,
          stopActions = TrackStopActions(onDelete = onDeleteStops),
        )
      }
    }

    // 長押しで選択モードに入り、その行が選択される。
    composeTestRule.onNodeWithText("テストカフェ").performTouchInput { longClick() }
    composeTestRule.onNodeWithText("1件選択中").assertIsDisplayed()

    // 選択バーの「削除」で即座に一括削除コールバック（確認ダイアログなし）。
    composeTestRule.onNodeWithText("削除").performClick()

    verify { onDeleteStops(listOf(1L)) }
  }

  @Test
  fun trackDetailScreen_stopWishlistFlag_showsStateByPlaceAndTogglesByPlaceId() {
    val onToggle = mockk<(Long) -> Unit>(relaxed = true)
    val track = createSampleTrack()
    // 動物園（place 9）に 2 回、売店（place 10）に 1 回。行きたいに入っているのは動物園だけ。
    val stops = listOf(
      sampleStop(id = 1L, placeId = 9L, name = "動物園"),
      sampleStop(id = 2L, placeId = 10L, name = "売店"),
      sampleStop(id = 3L, placeId = 9L, name = "動物園"),
    )
    var registered by mutableStateOf(
      listOf(
        RegisteredPlace(placeId = 9L, name = "動物園", latitude = 35.0, longitude = 139.0, isWishlisted = true),
        RegisteredPlace(placeId = 10L, name = "売店", latitude = 35.0, longitude = 139.0),
      ),
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
          stops = stops,
          mapPlaces = MapRegisteredPlaces(places = registered),
          stopActions = TrackStopActions(onToggleWishlist = onToggle),
        )
      }
    }

    // 旗は場所単位: 動物園の 2 行は塗り（外す）、売店は輪郭（追加）。
    composeTestRule.onAllNodesWithContentDescription("行きたいから外す").assertCountEquals(2)
    composeTestRule.onAllNodesWithContentDescription("行きたいに追加").assertCountEquals(1)

    // ワンタップで、その立ち寄りの場所の id を渡す。
    composeTestRule.onNodeWithContentDescription("行きたいに追加").performClick()
    verify { onToggle(10L) }

    // DB の変化（登録済みの場所の一覧が流れ直す）で、旗の見た目が変わる。
    registered = registered.map { it.copy(isWishlisted = true) }
    composeTestRule.onAllNodesWithContentDescription("行きたいから外す").assertCountEquals(3)
    composeTestRule.onAllNodesWithContentDescription("行きたいに追加").assertCountEquals(0)
  }

  @Test
  fun trackDetailScreen_stopWishlistFlag_hiddenInSelectionMode() {
    val track = createSampleTrack()
    val stops = listOf(sampleStop(id = 1L, placeId = 9L, name = "動物園"))

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(track = track, onBackClick = mockOnBackClick, mapContent = {}, stops = stops)
      }
    }

    composeTestRule.onNodeWithContentDescription("行きたいに追加").assertIsDisplayed()
    composeTestRule.onNodeWithText("動物園").performTouchInput { longClick() }
    composeTestRule.onAllNodesWithContentDescription("行きたいに追加").assertCountEquals(0)
  }

  @Test
  fun trackDetailScreen_selectSamePlaceStops_mergeTriggersCallback() {
    val onMergeStops = mockk<(List<Long>) -> Unit>(relaxed = true)
    val track = createSampleTrack()
    // 同じ場所（place 9）への2件。広い敷地で1回の外出に複数付いたのを想定。
    val stops = listOf(
      sampleStop(id = 1L, placeId = 9L, name = "動物園"),
      sampleStop(id = 2L, placeId = 9L, name = "動物園"),
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
          stops = stops,
          stopActions = TrackStopActions(onMerge = onMergeStops),
        )
      }
    }

    // 長押しで選択モードに入り、全選択で2件そろえてから「まとめる」。
    composeTestRule.onAllNodesWithText("動物園")[0].performTouchInput { longClick() }
    composeTestRule.onNodeWithText("全選択").performClick()
    composeTestRule.onNodeWithText("2件選択中").assertIsDisplayed()
    composeTestRule.onNodeWithText("まとめる").performClick()

    verify { onMergeStops(listOf(1L, 2L)) }
  }

  @Test
  fun trackDetailScreen_selectDifferentPlaceStops_mergeIsDisabled() {
    val track = createSampleTrack()
    // 場所が違う2件は、どれが正か決められないのでまとめさせない（→ adr/0024）。
    val stops = listOf(
      sampleStop(id = 1L, placeId = 9L, name = "動物園"),
      sampleStop(id = 2L, placeId = 10L, name = "売店"),
    )

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
          stops = stops,
        )
      }
    }

    composeTestRule.onNodeWithText("動物園").performTouchInput { longClick() }
    composeTestRule.onNodeWithText("全選択").performClick()
    composeTestRule.onNodeWithText("まとめる").assertIsNotEnabled()
  }

  @Test
  fun trackDetailScreen_editStopDuration_savesRangeFromTrackPoints() {
    val onUpdateStopDuration = mockk<(Long, Date, Date) -> Unit>(relaxed = true)
    val track = createSampleTrack()
    val stops = listOf(sampleStop(placeId = 9L, name = "テストカフェ"))

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
          stops = stops,
          stopActions = TrackStopActions(onUpdateDuration = onUpdateStopDuration),
        )
      }
    }

    composeTestRule.onNodeWithContentDescription("操作").performClick()
    composeTestRule.onNodeWithText("滞在時間を編集").performClick()
    composeTestRule.onNodeWithText("保存").performClick()

    // 触らずに保存すれば、保存済みの時刻に最も近い軌跡点の時刻がそのまま渡る。
    val points = track.smoothedPoints
    verify { onUpdateStopDuration(1L, points.first().timestamp, points.last().timestamp) }
  }

  @Test
  fun trackDetailScreen_manualAdd_searchByNameOpensSheetForThatFacility() {
    val track = createSampleTrack()

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
          stops = emptyList(),
          // 名前での検索は場所の編集から引く（ほかは何もしない）。
          placeEdit = object : PlaceEditUi by PlaceEditUi.None {
            override suspend fun predictPlaces(query: String) = listOf(PlacePrediction(placeId = "gp-zoo", primaryText = "テスト動物園", secondaryText = query))

            override suspend fun fetchPlaceResult(placeId: String) = PlaceSearchResult(
              googlePlaceId = "gp-zoo",
              name = "テスト動物園",
              address = "東京都",
              category = null,
              latitude = 35.0,
              longitude = 139.0,
            )
          },
        )
      }
    }

    // 地図をタップできない場所（地図に POI が出ていない施設）は、名前から選んで足せる。
    composeTestRule.onNodeWithText("地図で指して追加").performClick()
    composeTestRule.onNodeWithText("店名・場所名で検索").performTextInput("どうぶつ")
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("テスト動物園").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onAllNodesWithText("テスト動物園")[0].performClick()

    // 施設をタップしたときと同じ入力シートに進む（滞在区間の調整つき）。
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("追加").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("スライダーで大まかに、＋/− で1点ずつ微調整できます。").assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_unnamedTrack_renameFromMenuSavesName() {
    val onRenameTrack = mockk<(String) -> Unit>(relaxed = true)
    val track = createSampleTrack()

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
          onRenameTrack = onRenameTrack,
        )
      }
    }

    // 名前の編集は ⋮ の中（見出しを押しても開ける）。
    composeTestRule.onNodeWithContentDescription("その他の操作").performClick()
    composeTestRule.onNodeWithText("名前を付ける").performClick()
    composeTestRule.onNodeWithText("経路の名前").assertIsDisplayed()
    // 何も入れていないうちは保存を押せない。
    composeTestRule.onNodeWithText("保存").assertIsNotEnabled()
    // 欄の名札は、名前が無いときに見出しになる日付。
    composeTestRule.onNode(hasSetTextAction()).assert(hasText(DateFormatters.date(track.startTime))).performTextInput("鎌倉さんぽ")
    composeTestRule.onNodeWithText("保存").performClick()

    verify { onRenameTrack("鎌倉さんぽ") }
  }

  @Test
  fun trackDetailScreen_namedTrack_showsNameAsHeadingWithDateBelow() {
    val track = createSampleTrack().copy(name = "鎌倉さんぽ")

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
        )
      }
    }

    // 一覧のカードと同じ見せ方: 上のバーの見出しが名前、日付は副題。⋮ の中は「名前を編集」に変わる。
    composeTestRule.onNodeWithText("鎌倉さんぽ").assertIsDisplayed()
    composeTestRule.onNodeWithText("2022年01月01日(土)").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("その他の操作").performClick()
    composeTestRule.onNodeWithText("名前を編集").assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_titleClick_opensRenameDialog() {
    val track = createSampleTrack().copy(name = "鎌倉さんぽ")

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(track = track, onBackClick = mockOnBackClick, mapContent = {})
      }
    }

    composeTestRule.onNodeWithText("鎌倉さんぽ").performClick()
    composeTestRule.onNodeWithText("経路の名前").assertIsDisplayed()
  }

  @Test
  fun trackDetailScreen_menu_favoriteTogglesAndLabelFollowsState() {
    val onToggleFavorite = mockk<() -> Unit>(relaxed = true)
    var track by mutableStateOf(createSampleTrack())

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
          onToggleFavorite = onToggleFavorite,
        )
      }
    }

    composeTestRule.onNodeWithContentDescription("その他の操作").performClick()
    composeTestRule.onNodeWithTag("mapOverflowMenu").assertIsDisplayed()
    composeTestRule.onNodeWithText("お気に入りに入れる").performClick()
    verify { onToggleFavorite() }

    // お気に入りの経路では「外す」になる。
    track = track.copy(isFavorite = true)
    composeTestRule.onNodeWithContentDescription("その他の操作").performClick()
    composeTestRule.onNodeWithText("お気に入りから外す").assertIsDisplayed()
  }

  // 経路の削除は ⋮ の中。確かめずに消す予定にし（取り消しはアプリ全体のスナックバー）、記録中の経路には出さない。
  @Test
  fun trackDetailScreen_menu_deleteTrack_onlyWhenNotRecording() {
    val onDeleteTrack = mockk<() -> Unit>(relaxed = true)
    var track by mutableStateOf(createSampleTrack())

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
          onDeleteTrack = onDeleteTrack,
        )
      }
    }

    composeTestRule.onNodeWithContentDescription("その他の操作").performClick()
    composeTestRule.onNodeWithText("この記録を削除").performClick()
    verify { onDeleteTrack() }
    // 確認のダイアログは出さない。
    composeTestRule.onNodeWithText("削除しますか", substring = true).assertDoesNotExist()

    track = track.copy(isActive = true, endTime = null)
    composeTestRule.onNodeWithContentDescription("その他の操作").performClick()
    composeTestRule.onNodeWithText("この記録を削除").assertDoesNotExist()
  }

  @Test
  fun trackDetailScreen_menu_reanalyzeStartsReanalysis() {
    val onStart = mockk<() -> Unit>(relaxed = true)
    val track = createSampleTrack()

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
          reanalysis = StopReanalysis(onStart = onStart),
        )
      }
    }

    // 再解析はたまにしか使わないので ⋮ の中（シートには出さない）。
    composeTestRule.onNodeWithText("再解析").assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription("その他の操作").performClick()
    composeTestRule.onNodeWithText("再解析").performClick()
    verify { onStart() }
  }

  @Test
  fun trackDetailScreen_sameFacilityPending_showsTodoBannerAndOpensReview() {
    val onOpen = mockk<() -> Unit>(relaxed = true)
    val track = createSampleTrack()

    composeTestRule.setContent {
      PathlyAndroidTheme {
        TrackDetailScreen(
          track = track,
          onBackClick = mockOnBackClick,
          mapContent = {},
          sameFacility = SameFacilityReview(pending = listOf(SameFacilityPending(placeId = 9L, placeName = "動物園", other = SameFacilityPlace(placeId = 10L, name = "動物園"))), onOpen = onOpen),
        )
      }
    }

    // 出ているときだけの「やること」は、シートの先頭の帯で目立たせる。
    composeTestRule.onNodeWithText("同じ施設の確認待ち 1件").performClick()
    verify { onOpen() }
  }

  private fun sampleStop(
    placeId: Long = 1L,
    name: String? = null,
    id: Long = 1L,
  ): Stop {
    val place = Place(
      id = placeId,
      name = name,
      latitude = 35.0,
      longitude = 139.0,
      note = null,
      googleName = null,
      googleAddress = null,
      category = null,
      googlePlaceId = null,
      createdAt = Date(0L),
      updatedAt = Date(0L),
    )
    return Stop(
      id = id,
      place = place,
      trackId = 123L,
      arrivalTime = Date(1640995200000L),
      departureTime = Date(1640995200000L + 300000L),
    )
  }

  private fun createSampleTrack(
    id: Long = 123L,
    isActive: Boolean = false,
  ): GpsTrack {
    val startTime = Date(1640995200000L)
    return GpsTrack(
      id = id,
      startTime = startTime,
      endTime = if (isActive) null else Date(1640998800000L),
      isActive = isActive,
      points = createSamplePoints(5),
      createdAt = startTime,
      updatedAt = Date(1640998800000L),
    )
  }

  private fun createSamplePoints(count: Int): List<GpsPoint> = (1..count).map { index ->
    GpsPoint(
      id = index.toLong(),
      trackId = 1L,
      latitude = 35.6762 + (index * 0.001),
      longitude = 139.6503 + (index * 0.001),
      altitude = null,
      accuracy = 10f,
      speed = null,
      bearing = null,
      timestamp = Date(1640995200000L + index * 60000), // 1分間隔
      createdAt = Date(),
    )
  }
}
