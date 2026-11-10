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

    /**
     * Finger travel per step: higher sensitivity, shorter step. 100 % keeps [basePx] exactly.
     * A move batch going left ([dx] < 0, extending the selection) uses [percentLeft], one going
     * right (taking the selection back) uses [percentRight].
     * Before the swipe has [started], the step is also the only thing telling a tap from a swipe on
     * delete and enter, so it never drops below [basePx] there; a drifting tap would lose its key.
     */
    @JvmStatic
    fun step(basePx: Int, dx: Int, percentLeft: Int, percentRight: Int, started: Boolean): Int {
        val percent = if (dx < 0) percentLeft else percentRight
        val step = (basePx * 100 / percent).coerceAtLeast(1)
        return if (started) step else maxOf(step, basePx)
    }
}
