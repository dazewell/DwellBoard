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

    /**
     * The punctuation got no space and none was even tried. Only the kind of the character before it is logged
     * (never the character), and digits and white space are left out, since "1,000" is meant to stay without one.
     */
    @JvmStatic
    fun notTried(spaceState: Int, wasComposingWord: Boolean, codePointBefore: Int, slowConnection: Boolean) {
        val kind = when {
            Character.isLetter(codePointBefore) -> "letter"
            codePointBefore == '"'.code -> "double quote"
            Character.isDigit(codePointBefore) || Character.isWhitespace(codePointBefore) || codePointBefore < 0 -> return
            else -> "other"
        }
        if (off) return
        Log.w(TAG, "DWELL_SMOKE_comma-space not tried after $kind: spaceState=$spaceState composing=$wasComposingWord slow=$slowConnection")
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
