// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.punctuation

import helium314.keyboard.latin.utils.Log

/**
 * Temporary probe: why , ; : ! ? did not get the space right after them. Logs only path data
 * (reasons, space states, booleans), never which character was typed or any text.
 */
object AutospaceProbe {
    private const val TAG = "DWELL_SMOKE_comma-space"

    /** The separator handling decided against the space before it was even tried. */
    @JvmStatic
    fun notTried(spaceState: Int, wasComposingWord: Boolean, letterBefore: Boolean, slowConnection: Boolean) {
        Log.w(TAG, "DWELL_SMOKE_comma-space not tried: spaceState=$spaceState wasComposing=$wasComposingWord "
                + "letterBefore=$letterBefore slow=$slowConnection")
    }

    /** The space was tried but left out; [reason] is a fixed label. */
    @JvmStatic
    fun skipped(reason: String, slowConnection: Boolean) {
        Log.w(TAG, "DWELL_SMOKE_comma-space skipped: $reason slow=$slowConnection")
    }

    /** The space went in while the connection counted as slow, which before this fix was a skip. */
    @JvmStatic
    fun insertedWhileSlow() {
        Log.w(TAG, "DWELL_SMOKE_comma-space inserted while slow")
    }
}
