package com.mangomusic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.mangomusic.domain.Track
import com.mangomusic.ui.MusicViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DownloadUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun model()=ViewModelProvider(compose.activity)[MusicViewModel::class.java]
    @Test fun permissionDialogCanBeDismissedWithoutStartingDownload() {
        val track=Track("youtube:aqz-KE-bpKQ","Big Buck Bunny","Blender Foundation","https://www.youtube.com/watch?v=aqz-KE-bpKQ")
        compose.runOnIdle { model().offline(false);model().download(track) }
        compose.onNodeWithText("Save audio offline?").assertIsDisplayed()
        compose.onNodeWithText("Download").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Save audio offline?").assertDoesNotExist()
        compose.runOnIdle { assertNull(model().downloadConsent.value) }
        compose.onNodeWithContentDescription("Open navigation menu").performClick()
        compose.onNode(hasText("Downloads") and hasAnyAncestor(hasTestTag("navigation-drawer"))).performClick()
        compose.onNodeWithText("MB saved",substring=true).assertIsDisplayed()
    }
}
