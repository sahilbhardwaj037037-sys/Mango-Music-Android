package com.mangomusic

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mangomusic.ui.Vinyl
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VinylAlignmentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun logoSharesDiscCenterAcrossRotationPauseResumeAndResize() {
        val angle = mutableFloatStateOf(0f)
        val size = mutableStateOf(268.dp)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Vinyl(angle, Modifier.size(size.value))
                }
            }
        }
        fun assertCentered() {
            val disc = compose.onNodeWithContentDescription("Mango Music vinyl disc").fetchSemanticsNode().boundsInRoot.center
            val logo = compose.onNodeWithTag("vinyl-logo", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
            assertEquals("Logo horizontal center", disc.x, logo.x, 1f)
            assertEquals("Logo vertical center", disc.y, logo.y, 1f)
        }
        for (width in listOf(268.dp, 200.dp)) {
            compose.runOnIdle { size.value = width }
            for (rotation in listOf(0f, 37f, 123f, 270f, 359f)) {
                compose.runOnIdle { angle.floatValue = rotation }
                assertCentered()
                // Pausing leaves the transform untouched; resuming changes only its angle.
                compose.mainClock.advanceTimeBy(500)
                assertCentered()
                compose.runOnIdle { angle.floatValue = (rotation + 4f) % 360f }
                assertCentered()
            }
        }
    }
}
