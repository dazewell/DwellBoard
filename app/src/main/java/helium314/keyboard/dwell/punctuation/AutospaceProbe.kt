// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.punctuation

import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log

/**
 * Temporary probe: why , ; : ! ? did not get the space right after them. Logs only path data
 * (reasons, space states, booleans), never which character was typed or any text, and nothing in incognito mode.
 */
object AutospaceProbe {
    private const val TAG = "DWELL_SMOKE_comma-space"

    private val off get() = Settings.getValues().mIncognitoModeEnabled

    /** The punctuation followed a letter, but no word was being composed, so the space was not even tried. */
    @JvmStatic
    fun notTried(spaceState: Int, slowConnection: Boolean) {
        if (off) return
        Log.w(TAG, "DWELL_SMOKE_comma-space not tried after a letter: spaceState=$spaceState slow=$slowConnection")
    }

    /** The space was tried but left out; [reason] is a fixed label. */
    @JvmStatic
    fun skipped(reason: String, slowConnection: Boolean) {
        if (off) return
        Log.w(TAG, "DWELL_SMOKE_comma-space skipped: $reason slow=$slowConnection")
    }

    /** The space went in while the connection counted as slow, which before this fix was a skip. */
    @JvmStatic
    fun insertedWhileSlow() {
        if (off) return
        Log.w(TAG, "DWELL_SMOKE_comma-space inserted while slow")
    }
}
