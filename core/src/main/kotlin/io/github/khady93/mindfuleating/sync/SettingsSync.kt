package io.github.khady93.mindfuleating.sync

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import io.github.khady93.mindfuleating.settings.SettingsRepository
import kotlinx.coroutines.runBlocking

/**
 * Syncs duration/accent-color settings between the watch and phone apps via the Wearable Data
 * Layer API. Both apps must share the same package name + signing key for this to work at all
 * — a hard Google Play services requirement, not something configured here.
 */
object SettingsSync {
    const val PATH = "/settings"
    private const val KEY_DURATION_SECONDS = "duration_seconds"
    private const val KEY_ACCENT_COLOR_ARGB = "accent_color_argb"

    // Marks a value we just applied FROM a remote sync, so the repository Flow emission that
    // write causes isn't mistaken for a new local change and re-pushed (which would echo forever
    // between the two devices). Cleared once the corresponding local emission is observed.
    @Volatile private var lastAppliedDurationSeconds: Int? = null
    @Volatile private var lastAppliedAccentColorArgb: Int? = null

    /** Called whenever the combined (duration, accent color) state settles locally. */
    fun pushIfChanged(context: Context, durationSeconds: Int, accentColor: Color) {
        val accentColorArgb = accentColor.toArgb()
        val durationEcho = lastAppliedDurationSeconds == durationSeconds
        val colorEcho = lastAppliedAccentColorArgb == accentColorArgb
        if (durationEcho && colorEcho) return
        if (!durationEcho) lastAppliedDurationSeconds = null
        if (!colorEcho) lastAppliedAccentColorArgb = null

        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putInt(KEY_DURATION_SECONDS, durationSeconds)
            dataMap.putInt(KEY_ACCENT_COLOR_ARGB, accentColorArgb)
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }

    fun onRemoteDataChanged(context: Context, item: DataItem) {
        val dataMap = DataMapItem.fromDataItem(item).dataMap
        val settingsRepository = SettingsRepository(context)
        if (dataMap.containsKey(KEY_DURATION_SECONDS)) {
            val seconds = dataMap.getInt(KEY_DURATION_SECONDS)
            lastAppliedDurationSeconds = seconds
            runBlocking { settingsRepository.setDurationSeconds(seconds) }
        }
        if (dataMap.containsKey(KEY_ACCENT_COLOR_ARGB)) {
            val argb = dataMap.getInt(KEY_ACCENT_COLOR_ARGB)
            lastAppliedAccentColorArgb = argb
            runBlocking { settingsRepository.setAccentColor(Color(argb)) }
        }
    }
}
