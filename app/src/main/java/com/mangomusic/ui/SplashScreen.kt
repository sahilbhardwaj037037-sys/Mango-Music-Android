package com.mangomusic.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mangomusic.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0.88f) }
    val alpha = remember { Animatable(0f) }
    val splashAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
            )
        }
        delay(350)
        splashAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing)
        )
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = splashAlpha.value }
            .background(Color(0xFF121214)),
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient radial glow behind the yellow symbol
        Box(
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer {
                    scaleX = scale.value * 1.15f
                    scaleY = scale.value * 1.15f
                    this.alpha = alpha.value * 0.30f
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE5B842).copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Prominent original Mango Music logo
        Image(
            painter = painterResource(R.drawable.mango_logo),
            contentDescription = "Mango Music",
            modifier = Modifier
                .size(112.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
                .clip(RoundedCornerShape(26.dp))
        )
    }
}
