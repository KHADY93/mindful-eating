package io.github.khady93.mindfuleating.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    onSettingsClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mindful Eating") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(PaddingValues(horizontal = 32.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                CircularProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxSize(),
                    color = uiState.accentColor,
                    strokeWidth = 10.dp,
                    trackColor = uiState.accentColor.copy(alpha = 0.2f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Bites: ${uiState.biteCount}", style = MaterialTheme.typography.titleMedium)
                    Text(text = "${uiState.remainingSeconds}s", style = MaterialTheme.typography.displayLarge)
                }
            }

            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .size(96.dp)
                    .background(uiState.accentColor.copy(alpha = 0.15f), CircleShape)
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
                val icon = if (uiState.phase == TimerPhase.Running) Icons.Filled.Pause else Icons.Filled.PlayArrow
                val label = when (uiState.phase) {
                    TimerPhase.Idle -> "Start"
                    TimerPhase.Running -> "Pause"
                    TimerPhase.Paused -> "Resume"
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = icon, contentDescription = label, tint = uiState.accentColor)
                    Text(text = label, style = MaterialTheme.typography.labelMedium)
                }
            }

            if (uiState.phase != TimerPhase.Idle) {
                Text(
                    text = "Hold to reset",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
