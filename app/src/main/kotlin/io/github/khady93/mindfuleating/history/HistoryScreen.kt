package io.github.khady93.mindfuleating.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())

@Composable
fun HistoryScreen(repository: HistoryRepository) {
    val sessions by repository.sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = "History")
        if (sessions.isEmpty()) {
            Text(text = "No sessions yet")
        } else {
            sessions.forEachIndexed { index, session ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(10.dp)
                            .background(Color(session.accentColorArgb), CircleShape),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = formatTimestamp(session.startTimeMillis))
                        Text(text = "${session.biteCount} bites · ${formatDuration(session.durationSeconds)}")
                    }
                    IconButton(onClick = {
                        scope.launch { repository.deleteSession(session.startTimeMillis) }
                    }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete session")
                    }
                }
                if (index != sessions.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.15f)),
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DATE_TIME_FORMATTER)
}

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}m ${seconds}s"
}
