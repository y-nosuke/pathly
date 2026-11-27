package com.pathly.presentation.places

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.maps.model.LatLng
import com.pathly.presentation.common.rememberFloatingSheetState
import com.pathly.ui.theme.PathlyAndroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 計画の詳細から開いた登録のシート（→ docs/specs/plans.md の「登録のシート（計画から開いたとき）」）。 */
@RunWith(AndroidJUnit4::class)
class PlaceRegisterOptionsTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val newPoint = PlaceSheetTarget.NewPoint(LatLng(35.68, 139.76))

  private data class Registered(val wishlist: Boolean, val memo: String?)

  private fun setSheet(options: PlaceRegisterOptions?, onRegisterNew: (Registered) -> Unit = {}) {
    composeTestRule.setContent {
      PathlyAndroidTheme {
        val state = rememberFloatingSheetState(peekFraction = 0.9f)
        Box(modifier = Modifier.fillMaxSize()) {
          PlaceActionSheet(
            target = newPoint,
            onDismiss = {},
            onFetchPoiDetails = { null },
            onRegisterNew = { _, _, _, wishlist, _, _, memo, _, _ -> onRegisterNew(Registered(wishlist, memo)) },
            sheetState = state,
            registerOptions = options,
            modifier = Modifier.align(Alignment.BottomCenter),
          )
        }
      }
    }
  }

  @Test
  fun fromPlan_wishlistOnByDefault_labelAndBelowHeading_andOnRegisterBeforeRegistering() {
    val calls = mutableListOf<String>()
    setSheet(
      options = PlaceRegisterOptions(
        initialWishlist = true,
        registerLabel = "登録して 2日目に足す",
        belowHeading = { Text("足す日の欄", modifier = Modifier.testTag("belowHeading")) },
        onRegister = { calls += "onRegister" },
      ),
      onRegisterNew = { calls += "register:${it.wishlist}" },
    )

    composeTestRule.onNodeWithTag("belowHeading").assertIsDisplayed()
    // 行きたいが入っているときだけ出る優先度が出ている＝行きたいが初めから入っている。
    composeTestRule.onNodeWithText("優先度", substring = true).assertExists()
    composeTestRule.onNodeWithText("登録して 2日目に足す").performClick()

    // 足す日を決めてから登録を頼む。行きたいは入ったまま。
    assertEquals(listOf("onRegister", "register:true"), calls)
  }

  @Test
  fun fromPlan_wishlistCanBeTurnedOff() {
    val registered = mutableListOf<Registered>()
    setSheet(
      options = PlaceRegisterOptions(initialWishlist = true, registerLabel = "登録して足す"),
      onRegisterNew = { registered += it },
    )

    composeTestRule.onNodeWithText("行きたい").performClick()
    composeTestRule.onNodeWithText("登録して足す").performClick()

    assertEquals(listOf(Registered(wishlist = false, memo = null)), registered)
  }

  @Test
  fun withoutOptions_staysAsBefore() {
    val registered = mutableListOf<Registered>()
    setSheet(options = null, onRegisterNew = { registered += it })

    composeTestRule.onNodeWithText("登録").performClick()

    assertEquals(listOf(Registered(wishlist = false, memo = null)), registered)
  }
}
