// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import android.content.Context
import helium314.keyboard.keyboard.emoji.EmojiSearchActivity
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

/**
 * Starts each emoji search empty once the user has left the emoji keyboard, instead of showing the last search.
 *
 * The search runs in its own activity and the emoji keyboard is restored when it ends, so "the emoji keyboard
 * started" alone cannot tell a fresh visit from the return of a search. [onReturnPosted] marks that return.
 * Both entry points run on the main thread, so the flag needs no synchronisation.
 */
object EmojiSearchReset {
    private var returningFromSearch = false

    /** The emoji keyboard is about to be restored after a search; that start keeps the text. */
    @JvmStatic
    fun onReturnPosted() {
        returningFromSearch = true
    }

    /** The emoji keyboard was (re)started. */
    @JvmStatic
    fun onEmojiKeyboardStarted(context: Context) {
        onEmojiKeyboardStarted(isEnabled(context)) { EmojiSearchActivity.searchText = "" }
    }

    internal fun onEmojiKeyboardStarted(enabled: Boolean, clear: () -> Unit) {
        val returning = returningFromSearch
        returningFromSearch = false
        if (enabled && !returning) clear()
    }

    // an unreadable stored value falls back to the default
    private fun isEnabled(context: Context) = runCatching {
        context.prefs().getBoolean(Settings.PREF_CLEAR_EMOJI_SEARCH, Defaults.PREF_CLEAR_EMOJI_SEARCH)
    }.getOrDefault(Defaults.PREF_CLEAR_EMOJI_SEARCH)

    internal fun reset() {
        returningFromSearch = false
    }
}
