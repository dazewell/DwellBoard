// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.punctuation

import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.latin.settings.SettingsValues

/**
 * Policy for the space that is inserted right after punctuation (setting "Autospace after punctuation").
 * Only decides; inserting and the space state stay in InputLogic.
 */
object PunctuationAutospace {
    /** Punctuation that gets its space immediately. '.' is excluded because of domains, abbreviations and decimals. */
    @JvmStatic
    fun isImmediate(codePoint: Int): Boolean = codePoint == ','.code || codePoint == ';'.code || codePoint == ':'.code
            || codePoint == '!'.code || codePoint == '?'.code

    /** Punctuation that gets its space immediately after a number too. ',' and ':' are excluded for "1,000" and "10:30". */
    @JvmStatic
    fun isImmediateAfterNumber(codePoint: Int, codePointBefore: Int): Boolean =
        (codePoint == ';'.code || codePoint == '!'.code || codePoint == '?'.code) && Character.isDigit(codePointBefore)

    @JvmStatic
    fun isDash(codePoint: Int): Boolean = codePoint == '-'.code || codePoint == '–'.code || codePoint == '—'.code

    /**
     * A dash after "word " is a separator ("Look here - a cat"), unlike in "well-known" or "x = -5".
     * [lastTwo] is the text right before the cursor, only its last 2 chars are used.
     */
    @JvmStatic
    fun isSeparatorDash(codePoint: Int, lastTwo: CharSequence?): Boolean =
        isDash(codePoint) && lastTwo != null && lastTwo.length == 2 && lastTwo[1] == ' ' && Character.isLetter(lastTwo[0])

    /**
     * Whether [codePoint] typed right after an automatically inserted space belongs directly behind the previous
     * punctuation ("?!", "://"), so the space has to go. [lastTwo] is the punctuation and the inserted space.
     */
    @JvmStatic
    fun removesAutoSpace(codePoint: Int, lastTwo: CharSequence?): Boolean =
        codePoint == '.'.code || isImmediate(codePoint)
                || (codePoint == '/'.code && lastTwo != null && lastTwo.length == 2 && lastTwo[0] == ':')

    /**
     * Whether [codePoint] typed after a just typed "word " takes the place of that space ("word ," becomes
     * "word, "), the way punctuation picked from the suggestion strip already does.
     */
    @JvmStatic
    fun swapsTypedSpace(codePoint: Int, settings: SettingsValues, connection: RichInputConnection): Boolean =
        settings.mAutospaceAfterPunctuation && isImmediate(codePoint)
                && !settings.isUsuallyPrecededBySpace(codePoint) // French keeps "mot :"
                && settings.shouldInsertSpacesAutomatically()
                && settings.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
                && isWordThenSpace(connection.getTextBeforeCursor(2, 0))

    /** [lastTwo] is the text right before the cursor: true for "d ", false for "  ", ", " or "= ". */
    @JvmStatic
    fun isWordThenSpace(lastTwo: CharSequence?): Boolean =
        lastTwo != null && lastTwo.length == 2 && lastTwo[1] == ' ' && Character.isLetterOrDigit(lastTwo[0])

    /** [word] is the text since the last whitespace, e.g. "mail@example.com," */
    @JvmStatic
    fun looksLikeEmail(word: CharSequence): Boolean {
        val at = word.indexOf('@')
        return at >= 0 && word.indexOf('.', at) > at
    }
}
