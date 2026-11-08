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
        compose.onNodeWithText("＋ New project").performClick()
        compose.onNodeWithText("Choose video & create").assertIsDisplayed()
        compose.onNodeWithText("Project name").assertIsDisplayed()
    }
}
