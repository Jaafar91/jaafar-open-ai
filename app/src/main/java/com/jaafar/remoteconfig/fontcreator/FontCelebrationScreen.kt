package com.jaafar.remoteconfig.fontcreator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FontCelebrationScreen(
    fontName: String,
    characterCount: Int,
    fineTune: () -> Unit,
    backToLetters: () -> Unit,
) {
    val entrance = remember { Animatable(0f) }
    val confetti = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        entrance.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }
    LaunchedEffect(Unit) {
        confetti.animateTo(1f, tween(1800, easing = EaseOutCubic))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        CelebrationConfetti(confetti.value)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier
                        .size(168.dp)
                        .graphicsLayer {
                            alpha = entrance.value
                            scaleX = .72f + entrance.value * .28f
                            scaleY = .72f + entrance.value * .28f
                        },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f),
                ) {}
                Surface(
                    modifier = Modifier.size(126.dp).scale(.86f + entrance.value * .14f),
                    shape = RoundedCornerShape(38.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 10.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Aa", color = MaterialTheme.colorScheme.onPrimary, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                "Your handwriting is a font!",
                modifier = Modifier.padding(top = 30.dp),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "You completed all $characterCount characters for $fontName.",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Surface(
                modifier = Modifier.padding(top = 22.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    "✓  ALPHABET COMPLETE",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = fineTune,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("Fine-tune my font")
            }
            Text(
                "Next: preview your handwriting and adjust spacing",
                modifier = Modifier.padding(top = 9.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(
                onClick = backToLetters,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text("Review my letters")
            }
        }
    }
}

@Composable
private fun CelebrationConfetti(progress: Float) {
    val colors = listOf(
        Color(0xFF3859C7), Color(0xFF8B5CF6), Color(0xFFF59E0B),
        Color(0xFFEC4899), Color(0xFF14B8A6),
    )
    Canvas(Modifier.fillMaxSize()) {
        repeat(34) { index ->
            val x = size.width * (((index * 37) % 100) / 100f)
            val distance = size.height * (.35f + ((index * 29) % 65) / 100f)
            val y = -40f + distance * progress
            val fade = (1f - ((progress - .66f) / .34f)).coerceIn(0f, 1f)
            drawRoundRect(
                color = colors[index % colors.size].copy(alpha = fade),
                topLeft = Offset(x, y),
                size = Size(7.dp.toPx(), 14.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
    }
}
