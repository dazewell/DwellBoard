// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.punctuation

import helium314.keyboard.keyboard.internal.KeyboardState
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.settings.Settings

/**
 * Returns to the main keyboard after the user types one of a chosen set of symbols on the symbols pages, for marks
 * like ? or - that are almost always followed by a letter or a space. The set is parsed once when settings load and
 * kept as a sorted code point array, so the per-key check is a binary search with no allocation.
 */
object SymbolsSwitchBack {
    /** Parses the stored symbol string. Whitespace and digits never count, duplicates collapse; nothing here throws. */
    @JvmStatic
    fun parse(raw: String?): IntArray {
        if (raw.isNullOrEmpty()) return IntArray(0)
        // not String.codePoints(), which needs API 24
        return StringUtils.toCodePointArray(raw)
            .filter { !Character.isWhitespace(it) && !Character.isSpaceChar(it) && !Character.isDigit(it) }
            .distinct().sorted().toIntArray()
    }

    /**
     * Called per key event from KeyboardState.onEvent, after the character was committed. Skipped while a symbols
     * slide is in progress or the symbol or shift key is held for chording: releasing them already returns to the
     * previous keyboard, and switching early would put the rest of the chord on the letters.
     */
    @JvmStatic
    fun shouldSwitch(mode: KeyboardState.Mode, code: Int, heldOrSliding: Boolean): Boolean {
        if (mode != KeyboardState.Mode.SYMBOLS && mode != KeyboardState.Mode.SYMBOLS_SHIFTED) return false
        return !heldOrSliding && matches(Settings.getValues().mAlphaAfterSymbolChars, code)
    }

    internal fun matches(chars: IntArray, code: Int) = chars.isNotEmpty() && chars.binarySearch(code) >= 0
}
