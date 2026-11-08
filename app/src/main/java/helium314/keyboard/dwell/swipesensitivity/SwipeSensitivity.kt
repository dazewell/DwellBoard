// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.swipesensitivity

import android.content.SharedPreferences

/** Sensitivity of delete and enter swipe, in percent of upstream's step distance. */
object SwipeSensitivity {
    const val MIN = 25
    const val MAX = 300

    @JvmStatic
    fun read(prefs: SharedPreferences, key: String, default: Int): Int {
        val stored = runCatching { prefs.getInt(key, default) }.getOrDefault(default)
        return stored.coerceIn(MIN, MAX)
    }

    /** Finger travel per step: higher sensitivity, shorter step. 100 % keeps [basePx] exactly. */
    @JvmStatic
    fun step(basePx: Int, percent: Int): Int = (basePx * 100 / percent).coerceAtLeast(1)
}
