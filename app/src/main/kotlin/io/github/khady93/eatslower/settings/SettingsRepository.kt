package io.github.khady93.eatslower.settings

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.khady93.eatslower.theme.DefaultAccentColor
import kotlinx.coroutines.flow.map

internal val Context.dataStore by preferencesDataStore(name = "settings")

private val DURATION_SECONDS_KEY = intPreferencesKey("duration_seconds")
private val ACCENT_COLOR_ARGB_KEY = intPreferencesKey("accent_color_argb")

const val MIN_DURATION_SECONDS = 10
const val MAX_DURATION_SECONDS = 180
const val DEFAULT_DURATION_SECONDS = 60

class SettingsRepository(private val context: Context) {

    val durationSecondsFlow = context.dataStore.data.map { prefs ->
        prefs[DURATION_SECONDS_KEY] ?: DEFAULT_DURATION_SECONDS
    }

    val accentColorFlow = context.dataStore.data.map { prefs ->
        val argb = prefs[ACCENT_COLOR_ARGB_KEY]
        if (argb != null) Color(argb) else DefaultAccentColor
    }

    suspend fun setDurationSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[DURATION_SECONDS_KEY] = seconds.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)
        }
    }

    suspend fun setAccentColor(color: Color) {
        context.dataStore.edit { prefs ->
            prefs[ACCENT_COLOR_ARGB_KEY] = color.toArgb()
        }
    }
}
