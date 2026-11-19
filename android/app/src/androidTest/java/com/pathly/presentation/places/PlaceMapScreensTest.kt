package com.pathly.presentation.places

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.domain.model.MapPlaceFilter
import com.pathly.domain.model.Place
import com.pathly.domain.model.PlaceListItem
import com.pathly.domain.model.PlaceVisit
import com.pathly.domain.model.Priority
import com.pathly.presentation.common.MapRegisteredPlaces
import com.pathly.ui.theme.PathlyAndroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * 場所詳細と地図で選んで追加の置き方（地図の画面の型 → docs/designs/ui-direction.md）。
 * 地図の中（マーカー・カメラ）は画面のテストでは見にくいので、上のバー・シート・メニューだけを確かめる。
 */
@RunWith(AndroidJUnit4::class)
class PlaceMapScreensTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `場所詳細は上のバーに名前と戻るがあり保存はスクロールせずに見える`() {
    var backed = false
    setDetail(item(visitCount = 0), visits = List(8) { visit(it.toLong()) }, onBack = { backed = true })

    composeTestRule.onNodeWithText("喫茶ひなた").assertIsDisplayed()
    // 訪問履歴が長くても、保存は下端に固定されているので見える（変更が無いうちは押せない）。
    composeTestRule.onNodeWithText("保存").assertIsDisplayed().assertIsNotEnabled()

    composeTestRule.onNodeWithContentDescription("戻る").performClick()
    assertTrue(backed)
  }

  @Test
  fun `メモを直すと保存が押せて保存で中身が渡る`() {
    var savedNote: String? = null
    setDetail(item(visitCount = 0), onSave = { savedNote = it })

    composeTestRule.onNodeWithText("メモ（任意）").performTextInput("窓際の席")
    composeTestRule.onNodeWithText("保存").assertIsEnabled().performClick()

    assertEquals("窓際の席", savedNote)
  }

  @Test
  fun `しまうものは上のバーの⋮の中にあり削除はそこから`() {
    var deleted = false
    setDetail(item(visitCount = 0), onDelete = { deleted = true })

    // シートには出さない。
    composeTestRule.onNodeWithText("この場所を削除").assertDoesNotExist()
    composeTestRule.onNodeWithText("ほかの場所とまとめる").assertDoesNotExist()

    composeTestRule.onNodeWithContentDescription("その他の操作").performClick()
    composeTestRule.onNodeWithTag("mapOverflowMenu").assertIsDisplayed()
    composeTestRule.onNodeWithText("Googleで情報を取得").assertIsDisplayed()
    composeTestRule.onNodeWithText("ほかの場所とまとめる").assertIsDisplayed()
    composeTestRule.onNodeWithText("この場所を削除").performClick()

    assertTrue(deleted)
  }

  @Test
  fun `立ち寄り記録がある場所は⋮の削除を押せず理由を添える`() {
    setDetail(item(visitCount = 2))

    composeTestRule.onNodeWithContentDescription("その他の操作").performClick()
    composeTestRule.onNode(hasText("この場所を削除") and hasAnyAncestor(hasTestTag("mapOverflowMenu"))).assertIsDisplayed()
    composeTestRule.onNodeWithText("立ち寄り記録があるため削除できません").assertIsDisplayed()
  }

  @Test
  fun `場所詳細のレイヤーで登録済みの場所を切り替える`() {
    var shown by mutableStateOf(false)
    composeTestRule.setContent {
      PathlyAndroidTheme {
        PlaceDetailContent(
          item = item(visitCount = 0),
          visits = emptyList(),
          onBack = {},
          onOpenTrack = {},
          onDeleteRequest = {},
          placeEdit = PlaceEditUi.None,
          registeredPlaces = registeredPlaces(shown = shown, onToggle = { shown = !shown }),
        )
      }
    }

    composeTestRule.onNodeWithContentDescription("地図に重ねるもの").assertStateDescription("なし").performClick()
    composeTestRule.onNodeWithText("登録済みの場所").performClick()
    assertTrue(shown)
    // パネルを開いたままでも、ボタンの表示はすぐ今の状態になる。
    composeTestRule.onNodeWithTag("mapLayersPanel").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("地図に重ねるもの").assertStateDescription("表示中")
    composeTestRule.onNodeWithTag("mapLayersFilteredBadge").assertDoesNotExist()
  }

  @Test
  fun `登録済みの場所を絞り込んでいるとレイヤーのボタンに点が付く`() {
    var filter by mutableStateOf(MapPlaceFilter())
    composeTestRule.setContent {
      PathlyAndroidTheme {
        PlaceDetailContent(
          item = item(visitCount = 0),
          visits = emptyList(),
          onBack = {},
          onOpenTrack = {},
          onDeleteRequest = {},
          placeEdit = PlaceEditUi.None,
          registeredPlaces = MapRegisteredPlaces(shown = true, filter = filter, onFilterChange = { filter = it }),
        )
      }
    }

    composeTestRule.onNodeWithTag("mapLayersFilteredBadge").assertDoesNotExist()
    filter = MapPlaceFilter(wishlisted = true)
    composeTestRule.onNodeWithTag("mapLayersFilteredBadge").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("地図に重ねるもの").assertStateDescription("絞り込み中")
  }

  private fun SemanticsNodeInteraction.assertStateDescription(value: String) = assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value))

  @Test
  fun `地図で選ぶは選ぶまで案内の帯を出し戻るでやめる`() {
    var cancelled = false
    composeTestRule.setContent {
      PathlyAndroidTheme {
        AddPlaceContent(
          onCancel = { cancelled = true },
          placeEdit = PlaceEditUi.None,
          registeredPlaces = registeredPlaces(shown = false, onToggle = {}),
        )
      }
    }

    composeTestRule.onNodeWithText("地図で選んで追加").assertIsDisplayed()
    composeTestRule.onNodeWithText("地図をタップして場所を選ぶ").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("地図に重ねるもの").assertIsDisplayed()
    // 旧来の左下の「キャンセル」は戻るにまとめた。
    composeTestRule.onNodeWithText("キャンセル").assertDoesNotExist()

    composeTestRule.onNodeWithContentDescription("戻る").performClick()
    assertTrue(cancelled)
  }

  @Test
  fun `マーカーのシートは見出しの行に名前と⋮があり下端に閉じると保存が並ぶ`() {
    var deleted = false
    var closed = false
    composeTestRule.setContent {
      PathlyAndroidTheme {
        Column(modifier = Modifier.fillMaxSize()) {
          PlaceEditorBody(
            item = item(visitCount = 0),
            visits = List(8) { visit(it.toLong()) },
            onSave = { _, _, _, _, _, _ -> },
            onDelete = { deleted = true },
            onOpenTrack = {},
            onFetchNearbyPois = { _, _ -> emptyList() },
            onSearchPredictions = { emptyList() },
            onFetchPrediction = { null },
            mergeActions = PlaceEditUi.None.mergeActions(),
            onClose = { closed = true },
          )
        }
      }
    }

    composeTestRule.onNodeWithText("喫茶ひなた").assertIsDisplayed()
    composeTestRule.onNodeWithText("保存").assertIsDisplayed()
    composeTestRule.onNodeWithText("閉じる").performClick()
    assertTrue(closed)

    composeTestRule.onNodeWithContentDescription("その他の操作").performClick()
    composeTestRule.onNodeWithTag("placeEditorMenu").assertIsDisplayed()
    composeTestRule.onNodeWithText("ほかの場所とまとめる").assertIsDisplayed()
    composeTestRule.onNodeWithText("この場所を削除").performClick()
    assertTrue(deleted)
  }

  private fun setDetail(
    item: PlaceListItem,
    visits: List<PlaceVisit> = emptyList(),
    onBack: () -> Unit = {},
    onDelete: () -> Unit = {},
    onSave: (note: String) -> Unit = {},
  ) {
    val placeEdit = object : PlaceEditUi by PlaceEditUi.None {
      override fun savePlaceEdits(
        item: PlaceListItem,
        name: String,
        note: String,
        wishlist: Boolean,
        priority: Priority,
        visited: Boolean,
        link: com.pathly.domain.model.PlaceSearchResult?,
      ) = onSave(note)
    }
    composeTestRule.setContent {
      PathlyAndroidTheme {
        PlaceDetailContent(
          item = item,
          visits = visits,
          onBack = onBack,
          onOpenTrack = {},
          onDeleteRequest = onDelete,
          placeEdit = placeEdit,
          registeredPlaces = registeredPlaces(shown = false, onToggle = {}),
        )
      }
    }
  }

  private fun registeredPlaces(shown: Boolean, onToggle: () -> Unit) = MapRegisteredPlaces(
    places = emptyList(),
    shown = shown,
    onToggle = onToggle,
    filter = MapPlaceFilter(),
    onFilterChange = {},
  )

  private fun item(visitCount: Int) = PlaceListItem(
    place = Place(
      id = 1,
      // 自分で付けた名前は空（名前欄が空で、見出しは Google 名）。名前欄と見出しで同じ文字が 2 つ出ないように。
      name = null,
      latitude = 35.32,
      longitude = 139.55,
      note = null,
      googleName = "喫茶ひなた",
      googleAddress = null,
      category = null,
      googlePlaceId = null,
      createdAt = Date(0),
      updatedAt = Date(0),
    ),
    wishlistId = null,
    priority = null,
    markedVisitedAt = null,
    visitCount = visitCount,
  )

  private fun visit(trackId: Long) = PlaceVisit(
    trackId = trackId,
    outingDate = Date(trackId * 86_400_000L),
    arrivalTime = Date(trackId * 86_400_000L),
    departureTime = Date(trackId * 86_400_000L + 3_600_000L),
  )
}
