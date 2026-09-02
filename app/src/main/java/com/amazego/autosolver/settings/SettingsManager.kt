package com.amazego.autosolver.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestisce la persistenza delle preferenze operative e di sicurezza del bot.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("amazego_solver_prefs", Context.MODE_PRIVATE)

    var tapDelayMs: Long
        get() = prefs.getLong(KEY_TAP_DELAY, 220L)
        set(value) = prefs.edit().putLong(KEY_TAP_DELAY, value).apply()

    var verificationDelayMs: Long
        get() = prefs.getLong(KEY_VERIFICATION_DELAY, 300L)
        set(value) = prefs.edit().putLong(KEY_VERIFICATION_DELAY, value).apply()

    var minConfidenceThreshold: Float
        get() = prefs.getFloat(KEY_MIN_CONFIDENCE, 0.85f)
        set(value) = prefs.edit().putFloat(KEY_MIN_CONFIDENCE, value).apply()

    var isAutoRecalculationEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECALC, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RECALC, value).apply()

    var isAutoNextLevelEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_NEXT, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_NEXT, value).apply()

    var isSafeModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SAFE_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_SAFE_MODE, value).apply()

    var maxAutoLevels: Int
        get() = prefs.getInt(KEY_MAX_AUTO_LEVELS, 10)
        set(value) = prefs.edit().putInt(KEY_MAX_AUTO_LEVELS, value).apply()

    var isDebugOverlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_OVERLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_OVERLAY, value).apply()

    companion object {
        private const val KEY_TAP_DELAY = "key_tap_delay"
        private const val KEY_VERIFICATION_DELAY = "key_verification_delay"
        private const val KEY_MIN_CONFIDENCE = "key_min_confidence"
        private const val KEY_AUTO_RECALC = "key_auto_recalc"
        private const val KEY_AUTO_NEXT = "key_auto_next"
        private const val KEY_SAFE_MODE = "key_safe_mode"
        private const val KEY_MAX_AUTO_LEVELS = "key_max_auto_levels"
        private const val KEY_DEBUG_OVERLAY = "key_debug_overlay"
    }
}
