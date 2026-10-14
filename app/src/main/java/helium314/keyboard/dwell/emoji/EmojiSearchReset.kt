// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import helium314.keyboard.keyboard.emoji.EmojiSearchActivity
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.settings.Settings

/**
 * Clears the remembered emoji search once the user returns to the main keyboard by hand, i.e. presses ABC or the
 * emoji key while the emoji keyboard is showing. Everything around a search (the emoji keyboard being restored when
 * it ends) is left as it was, so the text is kept for repeated searches within one visit.
 */
object EmojiSearchReset {
    /** Called per key event while the emoji keyboard is showing; the cheap code check comes first. */
    @JvmStatic
    fun onEmojiKey(code: Int) {
        if (leavesEmojiKeyboard(code) && Settings.getValues().mClearEmojiSearch) EmojiSearchActivity.searchText = ""
    }

    internal fun leavesEmojiKeyboard(code: Int) = code == KeyCode.ALPHA || code == KeyCode.EMOJI
}
