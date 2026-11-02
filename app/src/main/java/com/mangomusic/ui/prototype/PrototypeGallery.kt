package com.mangomusic.ui.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangomusic.ui.MangoDarkColors
import com.mangomusic.ui.MangoLightColors

enum class PrototypeScreenMode {
    DISCOVER_RETURNING,
    DISCOVER_NEW,
    NOW_PLAYING_AMBIENT_ON,
    NOW_PLAYING_AMBIENT_OFF,
    ONBOARDING,
    SETTINGS_PREVIEW
}

/**
 * Interactive Prototype Gallery
 * Allows switching between all requested visual states and Light/Dark themes.
 * Completely decoupled from production database, navigation, or playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeGallery(
    initialMode: PrototypeScreenMode = PrototypeScreenMode.DISCOVER_RETURNING,
    initialDarkTheme: Boolean = true
) {
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var isDarkTheme by rememberSaveable { mutableStateOf(initialDarkTheme) }
    var dynamicBgEnabled by rememberSaveable { mutableStateOf(true) }
    var selectedTrack by remember { mutableStateOf(PrototypeData.trendingTracks[0]) }

    val colorScheme = if (isDarkTheme) MangoDarkColors else MangoLightColors

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Prototype Navigation & Theme Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "v2.0 Prototype View",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.weight(1f))
                            // Theme Toggle Pill
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { isDarkTheme = !isDarkTheme }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isDarkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                                        contentDescription = "Toggle Theme",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isDarkTheme) "Dark" else "Light",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Screen Mode Selector Chips
                        ScrollableTabRow(
                            selectedTabIndex = mode.ordinal,
                            edgePadding = 0.dp,
                            divider = {},
                            containerColor = Color.Transparent,
                            indicator = {}
                        ) {
                            val tabs = listOf(
                                "Discover (Returning)" to PrototypeScreenMode.DISCOVER_RETURNING,
                                "Discover (New User)" to PrototypeScreenMode.DISCOVER_NEW,
                                "Now Playing (Ambient ON)" to PrototypeScreenMode.NOW_PLAYING_AMBIENT_ON,
                                "Now Playing (Ambient OFF)" to PrototypeScreenMode.NOW_PLAYING_AMBIENT_OFF,
                                "Taste Onboarding" to PrototypeScreenMode.ONBOARDING,
                                "Settings" to PrototypeScreenMode.SETTINGS_PREVIEW
                            )

                            tabs.forEach { (label, screenMode) ->
                                val isSelected = mode == screenMode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        mode = screenMode
                                        if (screenMode == PrototypeScreenMode.NOW_PLAYING_AMBIENT_ON) dynamicBgEnabled = true
                                        if (screenMode == PrototypeScreenMode.NOW_PLAYING_AMBIENT_OFF) dynamicBgEnabled = false
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.padding(end = 6.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }

                // Render Selected Prototype Screen
                Box(modifier = Modifier.weight(1f)) {
                    when (mode) {
                        PrototypeScreenMode.DISCOVER_RETURNING -> {
                            DiscoverHomeScreen(
                                isReturningUser = true,
                                onToggleUserState = { mode = PrototypeScreenMode.DISCOVER_NEW },
                                onTrackSelect = {
                                    selectedTrack = it
                                    mode = PrototypeScreenMode.NOW_PLAYING_AMBIENT_ON
                                },
                                onOpenNowPlaying = { mode = PrototypeScreenMode.NOW_PLAYING_AMBIENT_ON }
                            )
                        }
                        PrototypeScreenMode.DISCOVER_NEW -> {
                            DiscoverHomeScreen(
                                isReturningUser = false,
                                onToggleUserState = { mode = PrototypeScreenMode.DISCOVER_RETURNING },
                                onTrackSelect = {
                                    selectedTrack = it
                                    mode = PrototypeScreenMode.NOW_PLAYING_AMBIENT_ON
                                },
                                onOpenNowPlaying = { mode = PrototypeScreenMode.NOW_PLAYING_AMBIENT_ON }
                            )
                        }
                        PrototypeScreenMode.NOW_PLAYING_AMBIENT_ON -> {
                            PrototypeNowPlayingScreen(
                                track = selectedTrack,
                                dynamicBackgroundEnabled = true,
                                onToggleDynamicBackground = { mode = PrototypeScreenMode.NOW_PLAYING_AMBIENT_OFF },
                                onClose = { mode = PrototypeScreenMode.DISCOVER_RETURNING },
                                onOpenQueue = { }
                            )
                        }
                        PrototypeScreenMode.NOW_PLAYING_AMBIENT_OFF -> {
                            PrototypeNowPlayingScreen(
                                track = selectedTrack,
                                dynamicBackgroundEnabled = false,
                                onToggleDynamicBackground = { mode = PrototypeScreenMode.NOW_PLAYING_AMBIENT_ON },
                                onClose = { mode = PrototypeScreenMode.DISCOVER_RETURNING },
                                onOpenQueue = { }
                            )
                        }
                        PrototypeScreenMode.ONBOARDING -> {
                            MusicTasteOnboardingScreen(
                                onContinue = { mode = PrototypeScreenMode.DISCOVER_RETURNING },
                                onSkip = { mode = PrototypeScreenMode.DISCOVER_NEW }
                            )
                        }
                        PrototypeScreenMode.SETTINGS_PREVIEW -> {
                            PrototypeSettingsScreen(
                                dynamicBackground = dynamicBgEnabled,
                                onToggleDynamicBackground = { dynamicBgEnabled = it },
                                isDark = isDarkTheme,
                                onToggleTheme = { isDarkTheme = !isDarkTheme }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrototypeSettingsScreen(
    dynamicBackground: Boolean,
    onToggleDynamicBackground: (Boolean) -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "Settings Preview",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Visual prototype of playback and theme options",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Player Appearance",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(10.dp))

        // Dynamic Background Toggle Row
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Dynamic Ambient Background",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Soft artwork-inspired color glow in Now Playing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = dynamicBackground,
                    onCheckedChange = onToggleDynamicBackground,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Appearance Mode Row
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Color Theme",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isDark) "Dark appearance (Charcoal #121214)" else "Light appearance (Off-white #FAFAFB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onToggleTheme,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isDark) "Switch Light" else "Switch Dark")
                }
            }
        }
    }
}
