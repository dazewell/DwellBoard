// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.enterswipe

import android.content.SharedPreferences
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings

/** Enter swipe mode as delete swipe stores it: 0 off, 1 letters, 2 words. */
object EnterSwipe {
    @JvmStatic
    fun read(prefs: SharedPreferences): Int {
        val stored = runCatching { prefs.getInt(Settings.PREF_ENTER_SWIPE, Defaults.PREF_ENTER_SWIPE) }
            .getOrDefault(Defaults.PREF_ENTER_SWIPE)
        return mode(stored)
    }

    // PointerTracker divides by the mode, and only lets values above 0 through to the divisor
    fun mode(stored: Int) = stored.coerceIn(0, 2)
}
