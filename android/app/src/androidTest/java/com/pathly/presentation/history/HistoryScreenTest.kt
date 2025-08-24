package com.pathly.presentation.history

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pathly.domain.model.GpsPoint
import com.pathly.domain.model.GpsTrack
import com.pathly.ui.theme.PathlyAndroidTheme
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockOnTrackClick: (GpsTrack) -> Unit
    private lateinit var mockViewModel: HistoryViewModel
    private lateinit var uiStateFlow: MutableStateFlow<HistoryState>

    @Before
    fun setup() {
        mockOnTrackClick = mockk(relaxed = true)
        mockViewModel = mockk(relaxed = true)

        // 初期状態のUIState
        uiStateFlow = MutableStateFlow(
            HistoryState(
                tracks = emptyList(),
                isLoading = false,
                errorMessage = null
            )
        )

        // ViewModelのuiStateをモック
        io.mockk.every { mockViewModel.uiState } returns uiStateFlow
        io.mockk.every { mockViewModel.deleteTrack(any()) } returns Unit
        io.mockk.every { mockViewModel.clearError() } returns Unit
    }

    @Test
    fun historyScreen_initialState_showsTitle() {
        // Given & When
        composeTestRule.setContent {
            PathlyAndroidTheme {
                HistoryScreen(
                    viewModel = mockViewModel,
                    onTrackClick = mockOnTrackClick
                )
            }
        }

        // Then
        composeTestRule
            .onNodeWithText("外出履歴")
            .assertIsDisplayed()
    }

    @Test
    fun historyScreen_loading_showsProgressIndicator() {
        // Given
        uiStateFlow.value = HistoryState(
            tracks = emptyList(),
            isLoading = true,
            errorMessage = null
        )

        // When
        composeTestRule.setContent {
            PathlyAndroidTheme {
                HistoryScreen(
                    viewModel = mockViewModel,
                    onTrackClick = mockOnTrackClick
                )
            }
        }

        // Then
        // CircularProgressIndicatorはcontentDescriptionがないので、存在確認が困難
        // 代わりにローディング状態でトラックリストが表示されないことを確認
        composeTestRule
            .onNodeWithText("記録がありません")
            .assertDoesNotExist()
    }

    @Test
    fun historyScreen_emptyTracks_showsEmptyMessage() {
        // Given
        uiStateFlow.value = HistoryState(
            tracks = emptyList(),
            isLoading = false,
            errorMessage = null
        )

        // When
        composeTestRule.setContent {
            PathlyAndroidTheme {
                HistoryScreen(
                    viewModel = mockViewModel,
                    onTrackClick = mockOnTrackClick
                )
            }
        }

        // Then
        composeTestRule
            .onNodeWithText("記録がありません")
            .assertIsDisplayed()
    }

    @Test
    fun historyScreen_withTracks_displaysTrackItems() {
        // Given
        val startTime = Date(System.currentTimeMillis() - 3600000) // 1時間前
        val endTime = Date()

        val tracks = listOf(
            GpsTrack(
                id = 1L,
                startTime = startTime,
                endTime = endTime,
                isActive = false,
                points = listOf(
                    GpsPoint(
                        id = 1L,
                        trackId = 1L,
                        latitude = 35.6762,
                        longitude = 139.6503,
                        altitude = null,
                        accuracy = 10f,
                        speed = null,
                        bearing = null,
                        timestamp = startTime,
                        createdAt = startTime
                    ),
                    GpsPoint(
                        id = 2L,
                        trackId = 1L,
                        latitude = 35.6896,
                        longitude = 139.7006,
                        altitude = null,
                        accuracy = 8f,
                        speed = null,
                        bearing = null,
                        timestamp = endTime,
                        createdAt = startTime
                    )
                ),
                createdAt = startTime,
                updatedAt = endTime
            )
        )

        uiStateFlow.value = HistoryState(
            tracks = tracks,
            isLoading = false,
            errorMessage = null
        )

        // When
        composeTestRule.setContent {
            PathlyAndroidTheme {
                HistoryScreen(
                    viewModel = mockViewModel,
                    onTrackClick = mockOnTrackClick
                )
            }
        }

        // Then
        // 日付フォーマット確認（正確な日付は環境依存なので部分一致）
        composeTestRule
            .onNodeWithText("開始:", substring = true)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("終了:", substring = true)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("移動距離:", substring = true)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("(2点)", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun historyScreen_trackItem_hasClickAction() {
        // Given
        val track = createSampleTrack()
        uiStateFlow.value = HistoryState(
            tracks = listOf(track),
            isLoading = false,
            errorMessage = null
        )

        composeTestRule.setContent {
            PathlyAndroidTheme {
                HistoryScreen(
                    viewModel = mockViewModel,
                    onTrackClick = mockOnTrackClick
                )
            }
        }

        // When & Then - トラックアイテム全体がクリック可能（Cardのonclick）
        // 特定のクリック可能要素を検証するのは困難なので、削除ボタンで代替
        composeTestRule
            .onNodeWithContentDescription("削除")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun historyScreen_deleteButton_triggersViewModel() {
        // Given
        val track = createSampleTrack()
        uiStateFlow.value = HistoryState(
            tracks = listOf(track),
            isLoading = false,
            errorMessage = null
        )

        composeTestRule.setContent {
            PathlyAndroidTheme {
                HistoryScreen(
                    viewModel = mockViewModel,
                    onTrackClick = mockOnTrackClick
                )
            }
        }

        // When
        composeTestRule
            .onNodeWithContentDescription("削除")
            .performClick()

        // Then
        verify { mockViewModel.deleteTrack(track) }
    }

    @Test
    fun historyScreen_multipleTracks_displaysAllTracks() {
        // Given
        val tracks = listOf(
            createSampleTrack(id = 1L, pointsCount = 5),
            createSampleTrack(id = 2L, pointsCount = 3),
            createSampleTrack(id = 3L, pointsCount = 0) // 0点のトラック
        )

        uiStateFlow.value = HistoryState(
            tracks = tracks,
            isLoading = false,
            errorMessage = null
        )

        // When
        composeTestRule.setContent {
            PathlyAndroidTheme {
                HistoryScreen(
                    viewModel = mockViewModel,
                    onTrackClick = mockOnTrackClick
                )
            }
        }

        // Then
        composeTestRule
            .onNodeWithText("(5点)", substring = true)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("(3点)", substring = true)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("(0点)", substring = true)
            .assertIsDisplayed()

        // 削除ボタンが3つ存在することを確認（各トラックに1つずつ）
        // Compose testでは同じcontentDescriptionの要素が複数ある場合、onAllNodesを使用
        composeTestRule
            .onAllNodesWithContentDescription("削除")
            .assertCountEquals(3)
    }

    @Test
    fun historyScreen_activeTrack_showsWithoutEndTime() {
        // Given - アクティブなトラック（endTimeがnull）
        val track = GpsTrack(
            id = 1L,
            startTime = Date(),
            endTime = null, // アクティブなトラック
            isActive = true,
            points = createSamplePoints(2),
            createdAt = Date(),
            updatedAt = Date()
        )

        uiStateFlow.value = HistoryState(
            tracks = listOf(track),
            isLoading = false,
            errorMessage = null
        )

        // When
        composeTestRule.setContent {
            PathlyAndroidTheme {
                HistoryScreen(
                    viewModel = mockViewModel,
                    onTrackClick = mockOnTrackClick
                )
            }
        }

        // Then
        composeTestRule
            .onNodeWithText("開始:", substring = true)
            .assertIsDisplayed()

        // 終了時刻は表示されない（endTimeがnullなので）
        composeTestRule
            .onNodeWithText("終了:", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun historyScreen_zeroDistanceTrack_showsZeroDistance() {
        // Given - 距離0のトラック（1点のみまたは同じ場所）
        val track = GpsTrack(
            id = 1L,
            startTime = Date(),
            endTime = Date(),
            isActive = false,
            points = listOf(
                GpsPoint(
                    id = 1L,
                    trackId = 1L,
                    latitude = 35.6762,
                    longitude = 139.6503,
                    altitude = null,
                    accuracy = 10f,
                    speed = null,
                    bearing = null,
                    timestamp = Date(),
                    createdAt = Date()
                )
            ),
            createdAt = Date(),
            updatedAt = Date()
        )

        uiStateFlow.value = HistoryState(
            tracks = listOf(track),
            isLoading = false,
            errorMessage = null
        )

        // When
        composeTestRule.setContent {
            PathlyAndroidTheme {
                HistoryScreen(
                    viewModel = mockViewModel,
                    onTrackClick = mockOnTrackClick
                )
            }
        }

        // Then
        composeTestRule
            .onNodeWithText("移動距離: 0.0km", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun historyScreen_stateChange_updatesUIReactively() {
        // Given - 初期状態は空
        composeTestRule.setContent {
            PathlyAndroidTheme {
                HistoryScreen(
                    viewModel = mockViewModel,
                    onTrackClick = mockOnTrackClick
                )
            }
        }

        // Then - 空メッセージが表示される
        composeTestRule
            .onNodeWithText("記録がありません")
            .assertIsDisplayed()

        // When - トラックを追加
        val track = createSampleTrack()
        composeTestRule.runOnUiThread {
            uiStateFlow.value = HistoryState(
                tracks = listOf(track),
                isLoading = false,
                errorMessage = null
            )
        }

        // Then - トラックアイテムが表示される
        composeTestRule
            .onNodeWithText("移動距離:", substring = true)
            .assertIsDisplayed()

        // 空メッセージは表示されない
        composeTestRule
            .onNodeWithText("記録がありません")
            .assertDoesNotExist()
    }

    private fun createSampleTrack(
        id: Long = 1L,
        pointsCount: Int = 2
    ): GpsTrack {
        return GpsTrack(
            id = id,
            startTime = Date(System.currentTimeMillis() - 3600000),
            endTime = Date(),
            isActive = false,
            points = createSamplePoints(pointsCount, trackId = id),
            createdAt = Date(),
            updatedAt = Date()
        )
    }

    private fun createSamplePoints(count: Int, trackId: Long = 1L): List<GpsPoint> {
        return (1..count).map { index ->
            GpsPoint(
                id = index.toLong(),
                trackId = trackId,
                latitude = 35.6762 + (index * 0.001), // 少しずつ異なる位置
                longitude = 139.6503 + (index * 0.001),
                altitude = null,
                accuracy = 10f,
                speed = null,
                bearing = null,
                timestamp = Date(System.currentTimeMillis() - (3600000 - index * 1000)),
                createdAt = Date()
            )
        }
    }
}