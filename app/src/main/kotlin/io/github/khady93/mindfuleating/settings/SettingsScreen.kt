package io.github.khady93.mindfuleating.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text
import io.github.khady93.mindfuleating.theme.AccentSwatches
import kotlinx.coroutines.launch

private const val SWATCHES_PER_ROW = 5

@Composable
fun SettingsScreen(repository: SettingsRepository, onHistoryClick: () -> Unit) {
    val durationSeconds by repository.durationSecondsFlow.collectAsStateWithLifecycle(initialValue = DEFAULT_DURATION_SECONDS)
    val accentColor by repository.accentColorFlow.collectAsStateWithLifecycle(initialValue = AccentSwatches.first())
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Bite timer")
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                scope.launch { repository.setDurationSeconds(durationSeconds - 5) }
            }) {
                Icon(imageVector = Icons.Filled.Remove, contentDescription = "Decrease duration")
            }
            Text(text = "${durationSeconds}s", modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = {
                scope.launch { repository.setDurationSeconds(durationSeconds + 5) }
            }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Increase duration")
            }
        }

        Text(text = "Accent color")
        AccentSwatches.chunked(SWATCHES_PER_ROW).forEach { rowSwatches ->
            Row(horizontalArrangement = Arrangement.Center) {
                rowSwatches.forEach { swatch ->
                    val isSelected = swatch == accentColor
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = Color.White,
                                shape = CircleShape,
                            )
                            .clickable {
                                scope.launch { repository.setAccentColor(swatch) }
                            },
                    )
                }
            }
        }

        Text(
            text = "View history",
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable(onClick = onHistoryClick),
        )
    }

    ScrollIndicator(
        state = scrollState,
        modifier = Modifier.align(Alignment.CenterEnd),
    )
    }
}
