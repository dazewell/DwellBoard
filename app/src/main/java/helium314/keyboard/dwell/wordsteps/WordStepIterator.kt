// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.wordsteps

import helium314.keyboard.compat.CompatBreakIterator
import helium314.keyboard.latin.common.codePointAt

/**
 * Word steps for delete and enter swipe. The word iterator also counts a run of spaces or
 * punctuation as a word, so one step could select " word" and the next "word". Here only the
 * boundaries where a word starts count, so spaces and punctuation go along with the word after them.
 */
class WordStepIterator(private val words: CompatBreakIterator) : CompatBreakIterator {
    override var text: CharSequence
        get() = words.text
        set(value) {
            words.text = value
            index = 0
        }
    private var index = 0

    override fun current() = index

    override fun first(): Int {
        index = 0
        return index
    }

    override fun last(): Int {
        index = text.length
        return index
    }

    // stepsToCharCount keeps stepping after -1, and the ICU word iterator turns -1 back into 0
    override fun following(offset: Int): Int {
        if (offset < 0) {
            index = -1
            return index
        }
        var i = words.following(offset)
        while (i != -1 && !isWordStart(i)) i = words.following(i)
        index = i
        return index
    }

    override fun preceding(offset: Int): Int {
        if (offset < 0) {
            index = -1
            return index
        }
        var i = words.preceding(offset)
        while (i != -1 && !isWordStart(i)) i = words.preceding(i)
        index = i
        return index
    }

    override fun isBoundary(offset: Int) = words.isBoundary(offset) && isWordStart(offset)

    // the ends of the text are where the caller's selection stops, so they always count
    private fun isWordStart(offset: Int): Boolean {
        if (offset <= 0 || offset >= text.length) return true
        val cp = text.codePointAt(offset)
        return !Character.isWhitespace(cp) && !isPunctuation(cp)
    }

    private fun isPunctuation(cp: Int) = when (Character.getType(cp).toByte()) {
        Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
        Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION -> true
        else -> false
    }
}
