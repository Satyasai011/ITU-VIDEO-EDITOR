package com.itu.videoeditor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itu.videoeditor.ui.*
import com.itu.videoeditor.ui.components.LocalPrismMotion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrismLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactVaultHas48DpTargetsAndRetainsSearchAcrossViewChange() {
        compose.setContent {
            ObsidianPrism {
                CompositionLocalProvider(LocalPrismMotion provides false) {
                    var grid by remember { mutableStateOf(true) }
                    Box(Modifier.width(320.dp).fillMaxHeight()) {
                        MediaVault(LibraryState(loading = false), grid, { grid = it }, {}) {}
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Grid view").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).assertIsSelected()
        compose.onNodeWithText("Search imported videos").performTextInput("gameplay")
        compose.onNodeWithContentDescription("List view").performClick().assertIsSelected()
        compose.onNodeWithText("gameplay").assertExists()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("gameplay").assertDoesNotExist()
    }
}
