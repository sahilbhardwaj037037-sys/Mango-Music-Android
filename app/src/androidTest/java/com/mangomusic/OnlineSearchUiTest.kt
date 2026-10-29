package com.mangomusic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mangomusic.ui.MusicViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineSearchUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun model()=ViewModelProvider(compose.activity)[MusicViewModel::class.java]
    private fun screenshot(name:String) {
        File(compose.activity.filesDir,name).outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun realResultsSelectNativePlayerAndNewSearchDoesNotInterrupt() {
        compose.runOnIdle { model().offline(false) }
        compose.onAllNodesWithText("Online").onFirst().performClick()
        compose.onNodeWithText("Search online music").performTextInput("Kevin MacLeod Carefree")
        compose.runOnIdle { compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0) }
        compose.waitUntil(60000) { model().onlineSearch.value.tracks.isNotEmpty() }
        val selected=model().onlineSearch.value.tracks.first()
        compose.onAllNodesWithText(selected.title).onFirst().performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithTag("online-thumbnail", useUnmergedTree=true).onFirst().assertExists()
        screenshot("online-results.png")
        compose.onAllNodesWithText(selected.title).onFirst().performClick()
        compose.waitUntil(60000) { model().playback.value.playing }
        compose.onNodeWithContentDescription("Open Now Playing").performClick()
        compose.onNodeWithContentDescription("Mango Music vinyl disc").assertIsDisplayed()
        screenshot("online-now-playing.png")
        compose.onNodeWithContentDescription("Close Now Playing").performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("Blender Foundation Big Buck Bunny")
        compose.waitUntil(60000) { model().onlineSearch.value.query=="Blender Foundation Big Buck Bunny" && model().onlineSearch.value.tracks.isNotEmpty() }
        compose.runOnIdle { assertTrue(model().playback.value.playing);assertEquals(selected.id,model().playback.value.id);model().stop() }
    }
}
