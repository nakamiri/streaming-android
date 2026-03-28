package com.reaream.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
    )

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsButtonOpensSettingsScreen() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Streams").assertIsDisplayed()
        composeTestRule.onNodeWithText("Camera").assertIsDisplayed()
        composeTestRule.onNodeWithText("Widgets").assertIsDisplayed()
    }

    @Test
    fun widgetSettingsScreenOpens() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Widgets").performClick()
        composeTestRule.onNodeWithText("時計を表示").assertIsDisplayed()
        composeTestRule.onNodeWithText("位置情報を表示").assertIsDisplayed()
        composeTestRule.onNodeWithText("速度を表示").assertIsDisplayed()
        composeTestRule.onNodeWithText("地図を表示").assertIsDisplayed()
    }

    @Test
    fun mapWidgetSettingsSectionExists() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Widgets").performClick()
        // Verify map section exists
        composeTestRule.onNodeWithText("地図").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("地図を表示").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("配信映像にミニマップを表示（位置情報の権限が必要）")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun backFromSettingsReturnsToCameraScreen() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithContentDescription("Go Live").assertIsDisplayed()
    }
}
