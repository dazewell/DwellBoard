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
     *  The form of [word] to put into the user history. Plain "Hello" words at the beginning of a sentence (or
     *  auto-capitalized) are already decapitalized by the user history logic where that is safe, so they are returned
     *  unchanged. ALL CAPS words are lowercased everywhere, as that logic would only lowercase their first letter.
     *  So are single letters and their short contractions: learning "I" or "I'm" as "i" or "i'm" would
     *  compete with the autocorrection to "I".
     */
    @JvmStatic
    fun wordToLearn(enabled: Boolean, word: String, wasAutoCapitalized: Boolean, isBeginningOfSentence: Boolean, locale: Locale): String {
        if (!enabled || !isSimplyCapitalized(word) || isSingleLetter(word)) return word
        // The user history logic only lowercases the first letter of a sentence-start word, which turns "CAPS" into
        // "cAPS". Only a plain "Hello" can be left to it, an ALL CAPS word is lowercased here wherever it is.
        if ((wasAutoCapitalized || isBeginningOfSentence) && !isAllCaps(word)) return word
        return word.lowercase(locale)
    }

    /** A capitalized word with a second uppercase letter, so with no lowercase one ("HELLO", not "Hello"). */
    private fun isAllCaps(word: String): Boolean {
        var i = Character.charCount(word.codePointAt(0))
        while (i < word.length) { // no String.codePoints(): minSdk 21
            val cp = word.codePointAt(i)
            if (Character.isUpperCase(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /** "I", "A", or a single letter with an apostrophe suffix of at most two letters ("I'm", "I'll", not "O'Neil"). */
    private fun isSingleLetter(word: String): Boolean {
        val apostrophe = word.indexOfFirst { it == '\'' || it == '’' }
        if (apostrophe < 0) return word.codePointCount(0, word.length) == 1
        return word.codePointCount(0, apostrophe) == 1 && word.codePointCount(apostrophe + 1, word.length) <= 2
    }
}
