package com.itu.videoeditor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudioSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun newProjectOpensRealImportScreen() {
        compose.onNodeWithText("Start editing").performClick()
        compose.onNodeWithText("Choose videos to inspect").assertIsDisplayed()
        compose.onNodeWithText("Project name").assertIsDisplayed()
    }
    @Test fun vaultHasAccessibleViewSwitchAndSearch() {
        compose.onAllNodesWithText("Media Vault").onFirst().performClick()
        compose.onNodeWithContentDescription("Grid view").assertIsDisplayed()
        compose.onNodeWithContentDescription("List view").performClick()
        compose.onNodeWithText("Search imported videos").performTextInput("gameplay")
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("All videos").performScrollTo().assertIsDisplayed()
    }
}
