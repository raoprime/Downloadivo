package com.raoprime.downloadivo.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticUtil {

    /**
     * Crisp light tick for tabs, format chips, and navigation.
     */
    fun lightClick(context: Context) {
        vibrate(context, VibrationEffect.EFFECT_TICK, 12L)
    }

    /**
     * Satisfying click for play, pause, and preview actions.
     */
    fun mediumClick(context: Context) {
        vibrate(context, VibrationEffect.EFFECT_CLICK, 25L)
    }

    /**
     * Strong confirmation pulse for starting downloads.
     */
    fun heavyClick(context: Context) {
        vibrate(context, VibrationEffect.EFFECT_HEAVY_CLICK, 45L)
    }

    /**
     * Rich double-pulse for download completion.
     */
    fun success(context: Context) {
        try {
            val vibrator = getVibrator(context)
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 30, 60, 40)
                    val amplitudes = intArrayOf(0, 180, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 30, 60, 40), -1)
                }
            }
        } catch (_: Exception) {}
    }

    private fun vibrate(context: Context, effectId: Int, fallbackDuration: Long) {
        try {
            val vibrator = getVibrator(context)
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(effectId))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(fallbackDuration, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(fallbackDuration)
                }
            }
        } catch (_: Exception) {}
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
