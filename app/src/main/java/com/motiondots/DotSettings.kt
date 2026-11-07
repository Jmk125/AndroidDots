package com.motiondots

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/** All user-customisable options, persisted in SharedPreferences. */
class DotSettings(context: Context) {

    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("dot_settings", Context.MODE_PRIVATE)

    var pattern: Int
        get() = prefs.getInt("pattern", PATTERN_EDGES)
        set(v) = prefs.edit().putInt("pattern", v).apply()

    /** Dots per column / row (meaning depends on pattern). */
    var count: Int
        get() = prefs.getInt("count", 7)
        set(v) = prefs.edit().putInt("count", v).apply()

    /** Dot diameter in dp. */
    var sizeDp: Int
        get() = prefs.getInt("size", 9)
        set(v) = prefs.edit().putInt("size", v).apply()

    /** 10..100 percent. */
    var opacity: Int
        get() = prefs.getInt("opacity", 75)
        set(v) = prefs.edit().putInt("opacity", v).apply()

    var color: Int
        get() = prefs.getInt("color", Color.WHITE)
        set(v) = prefs.edit().putInt("color", v).apply()

    /** How far dots travel per unit of acceleration, percent (20..300). */
    var sensitivity: Int
        get() = prefs.getInt("sensitivity", 100)
        set(v) = prefs.edit().putInt("sensitivity", v).apply()

    /** Spring stiffness / how snappy the dots feel, 0..100. */
    var responsiveness: Int
        get() = prefs.getInt("responsiveness", 50)
        set(v) = prefs.edit().putInt("responsiveness", v).apply()

    /** Distance from screen edge in dp. */
    var marginDp: Int
        get() = prefs.getInt("margin", 14)
        set(v) = prefs.edit().putInt("margin", v).apply()

    /** Flip movement direction. */
    var invert: Boolean
        get() = prefs.getBoolean("invert", false)
        set(v) = prefs.edit().putBoolean("invert", v).apply()

    /** Thin contrasting outline so dots stay visible on any background. */
    var outline: Boolean
        get() = prefs.getBoolean("outline", true)
        set(v) = prefs.edit().putBoolean("outline", v).apply()

    /** Dim the dots when the phone has been still for a few seconds. */
    var fadeWhenStill: Boolean
        get() = prefs.getBoolean("fade_still", false)
        set(v) = prefs.edit().putBoolean("fade_still", v).apply()

    fun reset() = prefs.edit().clear().apply()

    companion object {
        const val PATTERN_EDGES = 0
        const val PATTERN_FRAME = 1
        const val PATTERN_GRID = 2
        const val PATTERN_RING = 3
        val PATTERN_NAMES = listOf("Left & right edges", "Full frame", "Grid", "Ring")
    }
}
