package com.mangomusic

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.mangomusic.ui.MusicViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MixUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun model()=ViewModelProvider(compose.activity)[MusicViewModel::class.java]
    @Test fun mixToggleSurvivesNavigationRecreationAndRespectsOffline() {
        compose.runOnIdle { model().offline(false);model().mix(false) }
        compose.onAllNodesWithText("Online").onFirst().performClick()
        compose.onNodeWithText("Mix off").performClick()
        compose.onNodeWithText("Mix on").assertIsSelected()
        compose.onNodeWithContentDescription("Open navigation menu").performClick()
        compose.onNode(hasText("Up Next") and hasAnyAncestor(hasTestTag("navigation-drawer"))).performClick()
        compose.onNodeWithText("Mix on").assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Mix on").assertIsSelected().performClick()
        compose.runOnIdle { assertFalse(model().mixEnabled.value);model().offline(true) }
        compose.onNodeWithText("Mix off").assertIsNotEnabled()
        compose.runOnIdle { model().offline(false);model().mix(false) }
    }
}
