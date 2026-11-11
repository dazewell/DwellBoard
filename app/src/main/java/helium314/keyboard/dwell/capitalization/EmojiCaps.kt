// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.capitalization

import helium314.keyboard.latin.common.StringUtils

/**
 *  Lets auto-caps look past emojis, so "Done. 🚀 and" capitalizes "and" like "Done. and" would,
 *  while "done 🚀 and" still doesn't. Runs on every cursor move, so it only checks code points and allocates nothing.
 */
object EmojiCaps {
    private const val ZWJ = 0x200D
    private const val KEYCAP = 0x20E3

    /**
     *  Moves [end] back over any emojis right before it, and the spaces before each of them.
     *  Returns [end] unchanged if no emoji ends there.
     */
    @JvmStatic
    fun skipTrailingEmojis(cs: CharSequence, end: Int): Int {
        var j = end
        while (j > 0) {
            val k = skipEmoji(cs, j)
            if (k == j) break
            j = k
            while (j > 0 && (Character.isSpaceChar(cs[j - 1]) || cs[j - 1] == '\t')) j--
        }
        return j
    }

    /** Start of the single emoji ending at [end], or [end] if there is none. */
    private fun skipEmoji(cs: CharSequence, end: Int): Int {
        var j = end
        while (j > 0) {
            val cp = Character.codePointBefore(cs, j)
            if (cp == KEYCAP) { // keycap: digit, # or *, optional FE0F, then 20E3
                j--
                if (j > 0 && cs[j - 1].code == 0xFE0F) j--
                return if (j > 0 && (cs[j - 1] in '0'..'9' || cs[j - 1] == '#' || cs[j - 1] == '*')) j - 1 else end
            }
            if (isModifier(cp)) {
                j -= Character.charCount(cp)
                continue
            }
            if (!isEmojiBase(cp)) break
            j -= Character.charCount(cp)
            // a ZWJ sequence continues with the emoji before the joiner
            if (j > 0 && cs[j - 1].code == ZWJ) continue
            return j
        }
        return end // only modifiers or nothing emoji-like
    }

    // older Android reports newer emojis as unassigned, like upstream checks in InputLogic.handleNonSpecialCharacterEvent
    private fun isEmojiBase(cp: Int): Boolean {
        if (!StringUtils.mightBeEmoji(cp)) return false
        val type = Character.getType(cp)
        return type == Character.OTHER_SYMBOL.toInt() || (type == Character.UNASSIGNED.toInt() && cp >= 0x1F000)
    }

    // joiner, variation selectors, skin tones and the tag characters of subdivision flags
    private fun isModifier(cp: Int) = cp == ZWJ || cp == 0xFE0E || cp == 0xFE0F
            || cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F
}
