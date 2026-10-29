package com.mangomusic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mangomusic.ui.MusicViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun navigate(destination: String) {
        compose.onNodeWithContentDescription("Open navigation menu").performClick()
        compose.onNode(hasText(destination) and hasAnyAncestor(hasTestTag("navigation-drawer"))).performClick()
        compose.onNodeWithTag("navigation-drawer").assertIsNotDisplayed()
    }

    @Test fun navigationSearchFavoriteAndNowPlaying() {
        // A previously installed app may already be in Offline Mode.
        compose.runOnIdle { ViewModelProvider(compose.activity)[MusicViewModel::class.java].offline(false) }
        compose.waitUntil(15000) { compose.onAllNodesWithText("Mango Dawn").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Search your library").performTextInput("no\nsuch-song")
        compose.onNodeWithText("No matches yet").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear search").performClick()
        if (compose.onAllNodesWithContentDescription("Unfavorite Mango Dawn").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("Unfavorite Mango Dawn").performClick()
        }
        compose.onNodeWithContentDescription("Favorite Mango Dawn").performClick()
        navigate("Favorites")
        compose.onNodeWithText("Mango Dawn").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Open Now Playing").performClick()
        compose.onNodeWithContentDescription("Mango Music vinyl disc").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close Now Playing").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Light").performClick()
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithContentDescription("Offline Mode").performClick().assertIsOn()
        compose.onNodeWithContentDescription("Offline Mode").performClick().assertIsOff()
        compose.onNodeWithContentDescription("Close Settings").performClick()
        compose.runOnIdle {
            assertFalse("Dark appearance needs light status icons after Settings closes",
                WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars)
        }
    }

    @Test fun drawerBackNavigationStateAndRecreationPreservePlayback() {
        compose.runOnIdle { ViewModelProvider(compose.activity)[MusicViewModel::class.java].offline(false) }
        compose.waitUntil(15000) { compose.onAllNodesWithText("Mango Dawn").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Search your library").performTextInput("Mango Dawn")
        compose.onNode(hasText("Mango Dawn") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.waitUntil(10000) {
            var playing = false
            compose.activityRule.scenario.onActivity { playing = ViewModelProvider(it)[MusicViewModel::class.java].playback.value.playing }
            playing
        }
        compose.onNodeWithContentDescription("Open navigation menu").performClick()
        compose.onNode(hasText("Downloads") and hasAnyAncestor(hasTestTag("navigation-drawer"))).assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("navigation-drawer").assertIsNotDisplayed()
        compose.onNode(hasSetTextAction()).assertTextEquals("Mango Dawn")
        navigate("Up Next")
        compose.onAllNodes(hasText("Up Next") and hasAnyAncestor(hasTestTag("library-content"))).assertCountEquals(1)
        navigate("History")
        navigate("Downloads")
        compose.onNode(hasText("Downloads") and hasAnyAncestor(hasTestTag("library-content"))).assertIsDisplayed()
        compose.runOnIdle {
            val state = ViewModelProvider(compose.activity)[MusicViewModel::class.java].playback.value
            assertTrue("Navigation interrupted playback", state.playing)
            assertEquals("mango-dawn", state.id)
        }
        compose.activityRule.scenario.recreate()
        compose.onNode(hasText("Downloads") and hasAnyAncestor(hasTestTag("library-content"))).assertIsDisplayed()
        navigate("Search")
        compose.onAllNodesWithText("Mango Dawn").onFirst().assertExists()
        compose.onNode(hasSetTextAction()).assertTextEquals("Mango Dawn")
        compose.onNodeWithContentDescription("Open navigation menu").performClick()
        compose.onNode(hasText("Settings") and hasAnyAncestor(hasTestTag("navigation-drawer"))).performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
    }
}
