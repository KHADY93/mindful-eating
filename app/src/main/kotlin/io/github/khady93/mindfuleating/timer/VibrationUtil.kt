package io.github.khady93.mindfuleating.timer

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Distinct double-pulse haptic used to cue "take a bite" — deliberately different from a single system buzz. */
object VibrationUtil {

    private val timings = longArrayOf(0, 120, 100, 180)
    private val amplitudes = intArrayOf(0, 255, 0, 255)

    fun vibrateBiteAlert(context: Context) {
        val vibrator = getVibrator(context) ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
