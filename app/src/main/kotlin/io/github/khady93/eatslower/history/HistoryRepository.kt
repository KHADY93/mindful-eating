package io.github.khady93.eatslower.history

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.khady93.eatslower.settings.dataStore
import kotlinx.coroutines.flow.map

data class SessionRecord(
    val startTimeMillis: Long,
    val biteCount: Int,
    val durationSeconds: Long,
    val accentColorArgb: Int = LEGACY_COLOR_ARGB,
)

private val SESSION_HISTORY_KEY = stringPreferencesKey("session_history")
private const val MAX_HISTORY_SIZE = 500
private const val FIELD_DELIMITER = "|"
private const val RECORD_DELIMITER = "\n"

// Neutral grey shown for entries recorded before per-session color was tracked.
private const val LEGACY_COLOR_ARGB = 0xFF9E9E9E.toInt()

class HistoryRepository(private val context: Context) {

    val sessionsFlow = context.dataStore.data.map { prefs ->
        (prefs[SESSION_HISTORY_KEY] ?: "")
            .split(RECORD_DELIMITER)
            .filter { it.isNotBlank() }
            .mapNotNull(::parseRecord)
    }

    suspend fun addSession(record: SessionRecord) {
        context.dataStore.edit { prefs ->
            val existing = (prefs[SESSION_HISTORY_KEY] ?: "")
                .split(RECORD_DELIMITER)
                .filter { it.isNotBlank() }
            val updated = (listOf(encodeRecord(record)) + existing).take(MAX_HISTORY_SIZE)
            prefs[SESSION_HISTORY_KEY] = updated.joinToString(RECORD_DELIMITER)
        }
    }

    /** Deletes the session that started at [startTimeMillis] — unique enough in practice to key on. */
    suspend fun deleteSession(startTimeMillis: Long) {
        context.dataStore.edit { prefs ->
            val remaining = (prefs[SESSION_HISTORY_KEY] ?: "")
                .split(RECORD_DELIMITER)
                .filter { it.isNotBlank() }
                .filterNot { line -> parseRecord(line)?.startTimeMillis == startTimeMillis }
            prefs[SESSION_HISTORY_KEY] = remaining.joinToString(RECORD_DELIMITER)
        }
    }

    private fun encodeRecord(record: SessionRecord): String {
        return "${record.startTimeMillis}$FIELD_DELIMITER${record.biteCount}$FIELD_DELIMITER" +
            "${record.durationSeconds}$FIELD_DELIMITER${record.accentColorArgb}"
    }

    private fun parseRecord(line: String): SessionRecord? {
        val parts = line.split(FIELD_DELIMITER)
        if (parts.size != 3 && parts.size != 4) return null
        val startTimeMillis = parts[0].toLongOrNull() ?: return null
        val biteCount = parts[1].toIntOrNull() ?: return null
        val durationSeconds = parts[2].toLongOrNull() ?: return null
        val accentColorArgb = parts.getOrNull(3)?.toIntOrNull() ?: LEGACY_COLOR_ARGB
        return SessionRecord(startTimeMillis, biteCount, durationSeconds, accentColorArgb)
    }
}
