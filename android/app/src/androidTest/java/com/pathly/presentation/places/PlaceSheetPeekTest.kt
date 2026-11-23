package com.pathly.presentation.places

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.maps.model.LatLng
import com.pathly.presentation.common.FloatingSheetState
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.ui.theme.PathlyAndroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 場所のシートのハーフの高さ。登録のシートだけ、メモの欄まで見える高さより低くしない（#234）。 */
@RunWith(AndroidJUnit4::class)
class PlaceSheetPeekTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val newPoint = PlaceSheetTarget.NewPoint(LatLng(35.68, 139.76))
  private val existing = PlaceSheetTarget.Existing(placeId = 1L)

  @Test
  fun raisePeekTo_keepsWithinBaseAndFull() {
    lateinit var state: FloatingSheetState
    composeTestRule.setContent { state = rememberFloatingSheetState(peekFraction = 0.4f) }
    composeTestRule.waitForIdle()
    val base = state.peekPx

    composeTestRule.runOnIdle { state.raisePeekTo(base + 100f) }
    assertEquals(base + 100f, state.peekPx, 0.5f)
    // 作ったときより低くはしない。
    composeTestRule.runOnIdle { state.raisePeekTo(base / 2) }
    assertEquals(base, state.peekPx, 0.5f)
    // フルより高くはしない。
    composeTestRule.runOnIdle { state.raisePeekTo(state.fullPx * 2) }
    assertEquals(state.fullPx, state.peekPx, 0.5f)
    composeTestRule.runOnIdle { state.raisePeekTo(null) }
    assertEquals(base, state.peekPx, 0.5f)
  }

  @Test
  fun registerSheet_raisesPeek_andRestoresForExistingAndOnClose() {
    lateinit var state: FloatingSheetState
    var target: PlaceSheetTarget? by mutableStateOf(newPoint)
    // 横向き・文字が大きい設定のように 4 割が低い画面でも確かめられるよう、ハーフを小さくした state で試す。
    composeTestRule.setContent {
      PathlyAndroidTheme {
        state = rememberFloatingSheetState(peekFraction = 0.1f)
        Box(modifier = Modifier.fillMaxSize()) {
          target?.let {
            PlaceActionSheet(
              target = it,
              onDismiss = {},
              onFetchPoiDetails = { null },
              onRegisterNew = { _, _, _, _, _, _, _, _, _ -> },
              sheetState = state,
              modifier = Modifier.align(Alignment.BottomCenter),
            )
          }
        }
      }
    }
    composeTestRule.waitForIdle()
    val raised = state.peekPx
    // メモの欄まで入る高さ（300dp ほど）。画面の 1 割よりは高く、フルより高くはない。
    val atLeastPx = 300f * composeTestRule.density.density
    assertTrue(raised >= minOf(atLeastPx, state.fullPx) - 0.5f)
    assertTrue(raised <= state.fullPx + 0.5f)

    // 登録済みの場所のシートに替えたら元の高さ。
    composeTestRule.runOnIdle { target = existing }
    composeTestRule.waitForIdle()
    assertTrue(state.peekPx < raised)
    val basePeek = state.peekPx

    // 登録に戻して閉じたら、元の高さに戻る（同じ state を場所詳細などで使い回すため）。
    composeTestRule.runOnIdle { target = newPoint }
    composeTestRule.waitForIdle()
    assertEquals(raised, state.peekPx, 0.5f)
    composeTestRule.runOnIdle { target = null }
    composeTestRule.waitForIdle()
    assertEquals(basePeek, state.peekPx, 0.5f)
  }
}
