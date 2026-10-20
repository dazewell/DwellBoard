// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.cursor

import helium314.keyboard.latin.RichInputConnection

/**
 * Keeps RichInputConnection's composing text in step with the editor after a space swipe moved the cursor inside
 * the composing word without finishing it. RichInputConnection.setSelection reloads its cache and empties the
 * composing text there, while the editor keeps its composing span, so the next setComposingText or commitText
 * would count the part of the word before the cursor twice and the expected cursor position would drift from then on.
 */
object ComposingResync {
    /**
     * Sets the same composing region again, which splits the cache back into committed and composing text, the
     * way restartSuggestions does. Returns false without touching anything if the cached text before the cursor
     * does not end with the part of [typedWord] before [cursorCodePoints]: the word is then out of step with the
     * editor, and a region taken from it could cover text after the word.
     */
    @JvmStatic
    fun afterMoveInsideWord(connection: RichInputConnection, typedWord: String, cursorCodePoints: Int): Boolean {
        val cursor = connection.expectedSelectionStart
        if (cursor < 0 || cursorCodePoints < 0 || cursorCodePoints > typedWord.codePointCount(0, typedWord.length)) return false
        val charsBefore = typedWord.offsetByCodePoints(0, cursorCodePoints)
        if (charsBefore > cursor) return false
        val before = connection.getTextBeforeCursor(charsBefore, 0) ?: return false
        if (before.toString() != typedWord.substring(0, charsBefore)) return false
        val start = cursor - charsBefore
        connection.setComposingRegion(start, start + typedWord.length)
        return true
    }
}
