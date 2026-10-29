package com.mangomusic.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Keep the reference app's semantic hierarchy; reserve red for actions and selection.
internal val MangoRed = Color(0xFFFF5364)
internal val MangoDarkColors = darkColorScheme(
    primary = MangoRed,
    onPrimary = Color(0xFF22080D),
    primaryContainer = Color(0xFF332025),
    onPrimaryContainer = Color(0xFFFFDADF),
    secondary = Color(0xFFB5B5BB),
    onSecondary = Color(0xFF18181B),
    secondaryContainer = Color(0xFF28282C),
    onSecondaryContainer = Color(0xFFF2F2F4),
    background = Color(0xFF121214),
    onBackground = Color(0xFFF3F3F5),
    surface = Color(0xFF18181B),
    onSurface = Color(0xFFF3F3F5),
    surfaceVariant = Color(0xFF242427),
    onSurfaceVariant = Color(0xFFACACB4),
    surfaceContainer = Color(0xFF202023),
    surfaceContainerLow = Color(0xFF18181B),
    surfaceContainerHigh = Color(0xFF29292D),
    surfaceContainerHighest = Color(0xFF303035),
    outline = Color(0xFF73737D),
    outlineVariant = Color(0xFF323238)
)
internal val MangoLightColors = lightColorScheme(
    primary = Color(0xFFBF263B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFBE9EC),
    onPrimaryContainer = Color(0xFF76172A),
    secondary = Color(0xFF606069),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEDF0),
    onSecondaryContainer = Color(0xFF222226),
    background = Color(0xFFFAFAFB),
    onBackground = Color(0xFF202024),
    surface = Color.White,
    onSurface = Color(0xFF202024),
    surfaceVariant = Color(0xFFF0F0F3),
    onSurfaceVariant = Color(0xFF62626C),
    surfaceContainer = Color(0xFFF3F3F5),
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainerHigh = Color(0xFFEDEDF1),
    surfaceContainerHighest = Color(0xFFE8E8EC),
    outline = Color(0xFF777780),
    outlineVariant = Color(0xFFDEDEE3)
)
