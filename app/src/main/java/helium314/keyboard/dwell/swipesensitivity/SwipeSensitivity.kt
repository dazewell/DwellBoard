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
     * delete and enter, so the first move never fires before [basePx]; a drifting tap would lose its
     * key. With [evenWords] that floor holds per word of [mode] steps rather than per step, so the
     * first word costs no more than the rest unless the rest are shorter than [basePx].
     */
    @JvmStatic
    @JvmOverloads
    fun step(basePx: Int, dx: Int, percentLeft: Int, percentRight: Int, started: Boolean, mode: Int = 1, evenWords: Boolean = false): Int {
        val percent = if (dx < 0) percentLeft else percentRight
        val step = (basePx * 100 / percent).coerceAtLeast(1)
        if (started) return step
        val perWord = if (evenWords) mode.coerceAtLeast(1) else 1
        return maxOf(step, (basePx + perWord - 1) / perWord)
    }

    /**
     * Finger travel used up by [steps] steps of [step] px. Words mode ([mode] 2) needs twice the
     * travel per word, but upstream only takes back one [step]: the first word costs twice what each
     * later one does, and the first after turning back costs more again. With [evenWords] each word
     * takes back all the travel it needed, so every word costs the same.
     */
    @JvmStatic
    fun consumed(steps: Int, step: Int, mode: Int, evenWords: Boolean): Int =
        steps * step * (if (evenWords) mode.coerceAtLeast(1) else 1)
}
