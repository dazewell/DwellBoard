// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import helium314.keyboard.keyboard.emoji.EmojiSearchActivity
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.settings.Settings

/**
 * Clears the remembered emoji search once the user returns to the main keyboard by hand, i.e. presses ABC or the
 * emoji key while the emoji keyboard is showing. Everything else is left as it was: the emoji keyboard being restored
 * after a search, and the ABC that "ABC after emoji" sends after a pick, keep the text for the next search.
 * Everything runs on the main thread, so the flag needs no synchronisation.
 */
object EmojiSearchReset {
    private var automaticAlpha = false

    /** Set around the ABC press the emoji keyboard sends by itself after an emoji was picked. */
    @JvmStatic
    fun setAutomaticAlpha(on: Boolean) {
        automaticAlpha = on
    }

    /** Called per key event while the emoji keyboard is showing; the cheap code check comes first. */
    @JvmStatic
    fun onEmojiKey(code: Int) {
        if (clearsOn(code, automaticAlpha) && Settings.getValues().mClearEmojiSearch) EmojiSearchActivity.searchText = ""
    }

    internal fun clearsOn(code: Int, automaticAlpha: Boolean) =
        !automaticAlpha && (code == KeyCode.ALPHA || code == KeyCode.EMOJI)
}
