// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.capitalization

import java.util.Locale

/** Decides whether a typed word is learned in lowercase because it is capitalized or ALL CAPS. */
object CapitalizedLearning {
    /**
     * true for a word that starts with an uppercase letter and is either "Hello" (no other uppercase letter)
     * or "HELLO" (no lowercase letter). Mixed case like "HELlo" or "iPhone" is not.
     */
    @JvmStatic
    fun isSimplyCapitalized(word: String): Boolean {
        if (word.isEmpty() || !Character.isUpperCase(word.codePointAt(0))) return false
        var i = Character.charCount(word.codePointAt(0))
        var hasUpper = false
        var hasLower = false
        while (i < word.length) {
            val cp = word.codePointAt(i)
            if (Character.isUpperCase(cp)) hasUpper = true
            else if (Character.isLowerCase(cp)) hasLower = true
            i += Character.charCount(cp)
        }
        return !hasUpper || !hasLower
    }

    /**
     *  The form of [word] to put into the user history. Words at the beginning of a sentence (or auto-capitalized)
     *  are already decapitalized by the user history logic where that is safe, so they are returned unchanged.
     *  So are single letters and their short contractions: learning "I" or "I'm" as "i" or "i'm" would
     *  compete with the autocorrection to "I".
     */
    @JvmStatic
    fun wordToLearn(enabled: Boolean, word: String, wasAutoCapitalized: Boolean, isBeginningOfSentence: Boolean, locale: Locale): String =
        if (enabled && !wasAutoCapitalized && !isBeginningOfSentence && isSimplyCapitalized(word) && !isSingleLetter(word))
            word.lowercase(locale)
        else word

    /** "I", "A", or a single letter with an apostrophe suffix of at most two letters ("I'm", "I'll", not "O'Neil"). */
    private fun isSingleLetter(word: String): Boolean {
        val apostrophe = word.indexOfFirst { it == '\'' || it == '’' }
        if (apostrophe < 0) return word.codePointCount(0, word.length) == 1
        return word.codePointCount(0, apostrophe) == 1 && word.codePointCount(apostrophe + 1, word.length) <= 2
    }
}
