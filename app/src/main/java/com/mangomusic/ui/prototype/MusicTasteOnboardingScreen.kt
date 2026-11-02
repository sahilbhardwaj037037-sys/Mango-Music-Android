package com.mangomusic.ui.prototype

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangomusic.R

data class VibeOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradientColors: List<Color>
)

/**
 * Screen C: First-Launch Music Taste Onboarding Prototype
 * 
 * Heading: "What's your music vibe?"
 * Multi-select language options with "All Music" selected by default.
 * Clear Continue and Skip actions.
 */
@Composable
fun MusicTasteOnboardingScreen(
    onContinue: (Set<String>) -> Unit = {},
    onSkip: () -> Unit = {}
) {
    var selectedVibes by rememberSaveable { mutableStateOf(setOf("all")) }

    val options = remember {
        listOf(
            VibeOption(
                id = "all",
                title = "All Music",
                subtitle = "No limits · Everything trending across languages",
                icon = Icons.Outlined.Public,
                gradientColors = listOf(Color(0xFFFF5364), Color(0xFF8B1D38))
            ),
            VibeOption(
                id = "punjabi",
                title = "Punjabi",
                subtitle = "Bhangra beats, urban melodies & high energy",
                icon = Icons.Outlined.MusicNote,
                gradientColors = listOf(Color(0xFFE85D04), Color(0xFF6A040F))
            ),
            VibeOption(
                id = "hindi",
                title = "Hindi",
                subtitle = "Bollywood romance, soul, and chartbusters",
                icon = Icons.Outlined.Audiotrack,
                gradientColors = listOf(Color(0xFF1D3557), Color(0xFF457B9D))
            ),
            VibeOption(
                id = "english",
                title = "English",
                subtitle = "Global Pop, Hip-Hop, Synthwave & R&B",
                icon = Icons.Outlined.Headphones,
                gradientColors = listOf(Color(0xFF7209B7), Color(0xFF3F37C9))
            ),
            VibeOption(
                id = "regional",
                title = "Other Regional",
                subtitle = "Tamil, Telugu, Malayalam, Kannada & more",
                icon = Icons.Outlined.Radio,
                gradientColors = listOf(Color(0xFF2A9D8F), Color(0xFF264653))
            )
        )
    }

    fun toggleVibe(id: String) {
        selectedVibes = if (id == "all") {
            setOf("all")
        } else {
            val updated = selectedVibes.toMutableSet()
            updated.remove("all")
            if (updated.contains(id)) {
                updated.remove(id)
            } else {
                updated.add(id)
            }
            if (updated.isEmpty()) setOf("all") else updated
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Brand Logo & Header
            Spacer(Modifier.height(12.dp))
            Image(
                painter = painterResource(R.drawable.mango_logo),
                contentDescription = "Mango Music",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "What's your music vibe?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Choose the languages you want to explore.\nYou can always adjust this anytime.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // Vibe Cards List
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                options.forEach { option ->
                    val isSelected = selectedVibes.contains(option.id)
                    VibeSelectionCard(
                        option = option,
                        isSelected = isSelected,
                        onToggle = { toggleVibe(option.id) }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Primary Action: Continue
            Button(
                onClick = { onContinue(selectedVibes) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(Modifier.height(8.dp))

            // Secondary Action: Skip
            TextButton(
                onClick = onSkip,
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = "Skip for now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun VibeSelectionCard(
    option: VibeOption,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Pill with Gradient
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(option.gradientColors)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = option.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
