package com.mangomusic

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mangomusic.ui.MangoDarkColors
import com.mangomusic.ui.MangoLightColors
import com.mangomusic.ui.prototype.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PrototypeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val outputDir = File(context.filesDir, "v2-prototypes").apply { mkdirs() }

    private fun capture(fileName: String) {
        val file = File(outputDir, fileName)
        file.outputStream().use { stream ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        assertTrue("Screenshot $fileName was created", file.isFile && file.length() > 0)
    }

    @Composable
    private fun PrototypeSurface(
        darkTheme: Boolean = true,
        width: Dp? = null,
        content: @Composable () -> Unit
    ) {
        MaterialTheme(colorScheme = if (darkTheme) MangoDarkColors else MangoLightColors) {
            Box(
                modifier = if (width != null) Modifier.width(width).fillMaxHeight() else Modifier.fillMaxSize()
            ) {
                content()
            }
        }
    }

    @Test
    fun captureDiscoverHomeReturningUserDark() {
        compose.setContent {
            PrototypeSurface(darkTheme = true) {
                DiscoverHomeScreen(isReturningUser = true)
            }
        }
        compose.waitForIdle()
        capture("discover-home-returning-dark.png")
    }

    @Test
    fun captureDiscoverHomeReturningUserLight() {
        compose.setContent {
            PrototypeSurface(darkTheme = false) {
                DiscoverHomeScreen(isReturningUser = true)
            }
        }
        compose.waitForIdle()
        capture("discover-home-returning-light.png")
    }

    @Test
    fun captureDiscoverHomeNewUserDark() {
        compose.setContent {
            PrototypeSurface(darkTheme = true) {
                DiscoverHomeScreen(isReturningUser = false)
            }
        }
        compose.waitForIdle()
        capture("discover-home-new-user-dark.png")
    }

    @Test
    fun captureDiscoverHomeNewUserLight() {
        compose.setContent {
            PrototypeSurface(darkTheme = false) {
                DiscoverHomeScreen(isReturningUser = false)
            }
        }
        compose.waitForIdle()
        capture("discover-home-new-user-light.png")
    }

    @Test
    fun captureNowPlayingAmbientOnDark() {
        compose.setContent {
            PrototypeSurface(darkTheme = true) {
                PrototypeNowPlayingScreen(
                    track = PrototypeData.trendingTracks[0], // Kinni Kinni - Crimson
                    dynamicBackgroundEnabled = true
                )
            }
        }
        compose.waitForIdle()
        capture("now-playing-ambient-on-dark.png")
    }

    @Test
    fun captureNowPlayingAmbientOnLight() {
        compose.setContent {
            PrototypeSurface(darkTheme = false) {
                PrototypeNowPlayingScreen(
                    track = PrototypeData.trendingTracks[2], // Chaleya - Blue
                    dynamicBackgroundEnabled = true
                )
            }
        }
        compose.waitForIdle()
        capture("now-playing-ambient-on-light.png")
    }

    @Test
    fun captureNowPlayingAmbientOffDark() {
        compose.setContent {
            PrototypeSurface(darkTheme = true) {
                PrototypeNowPlayingScreen(
                    track = PrototypeData.trendingTracks[0],
                    dynamicBackgroundEnabled = false
                )
            }
        }
        compose.waitForIdle()
        capture("now-playing-ambient-off-dark.png")
    }

    @Test
    fun captureOnboardingDark() {
        compose.setContent {
            PrototypeSurface(darkTheme = true) {
                MusicTasteOnboardingScreen()
            }
        }
        compose.waitForIdle()
        capture("onboarding-dark.png")
    }

    @Test
    fun captureOnboardingLight() {
        compose.setContent {
            PrototypeSurface(darkTheme = false) {
                MusicTasteOnboardingScreen()
            }
        }
        compose.waitForIdle()
        capture("onboarding-light.png")
    }

    @Test
    fun captureCompactPhone360dp() {
        compose.setContent {
            PrototypeSurface(darkTheme = true, width = 360.dp) {
                DiscoverHomeScreen(isReturningUser = true)
            }
        }
        compose.waitForIdle()
        capture("discover-compact-phone-360dp.png")
    }

    @Test
    fun captureLargePhone430dp() {
        compose.setContent {
            PrototypeSurface(darkTheme = true, width = 430.dp) {
                DiscoverHomeScreen(isReturningUser = true)
            }
        }
        compose.waitForIdle()
        capture("discover-large-phone-430dp.png")
    }

    @Test
    fun captureSettingsAmbientToggle() {
        compose.setContent {
            PrototypeSurface(darkTheme = true) {
                PrototypeSettingsScreen(
                    dynamicBackground = true,
                    onToggleDynamicBackground = {},
                    isDark = true,
                    onToggleTheme = {}
                )
            }
        }
        compose.waitForIdle()
        capture("settings-ambient-toggle-dark.png")
    }
}
