package io.github.khady93.eatslower.timer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text

// Ambient (always-on) mode uses a thin, monochrome ring instead of the accent color —
// lower power draw and matches Wear OS's standard low-power display convention.
private val AMBIENT_RING_COLOR = Color(0xFF8A8A8A)
private val AMBIENT_TRACK_COLOR = Color(0xFF2A2A2A)
private val AMBIENT_STROKE_WIDTH = 2.dp

// Small nudge so the centered content doesn't crowd up against the settings icon above it —
// kept modest since the round display leaves little slack at the bottom for "Hold to reset".
private val ICON_CLEARANCE_NUDGE = 20.dp

@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    onSettingsClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ambientMode = LocalAmbientModeManager.current?.currentAmbientMode
    val isAmbient = ambientMode is AmbientMode.Ambient

    // Brief glow + counter "pop" each time a bite registers, synced with the vibration cue.
    val biteFlashAlpha = remember { Animatable(0f) }
    val biteScale = remember { Animatable(1f) }
    LaunchedEffect(uiState.biteCount) {
        if (uiState.biteCount > 0) {
            biteFlashAlpha.snapTo(0.35f)
            biteScale.snapTo(1.3f)
            biteFlashAlpha.animateTo(0f, animationSpec = tween(durationMillis = 500))
            biteScale.animateTo(1f, animationSpec = tween(durationMillis = 300))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isAmbient) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                uiState.accentColor.copy(alpha = 0.16f + biteFlashAlpha.value),
                                Color.Black,
                            ),
                        ),
                    ),
            )
        }

        CircularProgressIndicator(
            progress = { uiState.progress },
            modifier = Modifier.fillMaxSize(),
            colors = if (isAmbient) {
                ProgressIndicatorDefaults.colors(
                    indicatorColor = AMBIENT_RING_COLOR,
                    trackColor = AMBIENT_TRACK_COLOR,
                )
            } else {
                ProgressIndicatorDefaults.colors(indicatorColor = uiState.accentColor)
            },
            strokeWidth = if (isAmbient) AMBIENT_STROKE_WIDTH else CircularProgressIndicatorDefaults.largeStrokeWidth,
        )

        if (!isAmbient) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp),
            ) {
                Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings")
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(PaddingValues(horizontal = 24.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(ICON_CLEARANCE_NUDGE))

            Text(
                text = "Bites: ${uiState.biteCount}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.scale(biteScale.value),
            )
            Text(text = "${uiState.remainingSeconds}s", style = MaterialTheme.typography.displayLarge)

            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(84.dp)
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onLongClick = { viewModel.stopSession() },
                        onClick = {
                            when (uiState.phase) {
                                TimerPhase.Idle -> viewModel.startEating()
                                TimerPhase.Running -> viewModel.pause()
                                TimerPhase.Paused -> viewModel.resume()
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = uiState.phase,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "primary-button",
                ) { phase ->
                    val icon = if (phase == TimerPhase.Running) Icons.Filled.Pause else Icons.Filled.PlayArrow
                    val label = when (phase) {
                        TimerPhase.Idle -> "Start"
                        TimerPhase.Running -> "Pause"
                        TimerPhase.Paused -> "Resume"
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = icon, contentDescription = label, tint = uiState.accentColor)
                        Text(text = label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (uiState.phase != TimerPhase.Idle) {
                Text(text = "Hold to reset", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
