// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.capitalization

/** Decides whether a typed word is kept out of the user history because it is "simply capitalized". */
object CapitalizedLearning {
    /** true for a word that starts with an uppercase letter and has no other uppercase letter, e.g. "Hello" but not "HELlo" or "iPhone" */
    @JvmStatic
    fun isSimplyCapitalized(word: String): Boolean {
        if (word.isEmpty() || !Character.isUpperCase(word.codePointAt(0))) return false
        var i = Character.charCount(word.codePointAt(0))
        while (i < word.length) {
            val cp = word.codePointAt(i)
            if (Character.isUpperCase(cp)) return false
            i += Character.charCount(cp)
        }
        return true
    }

    /**
     *  Words at the beginning of a sentence (or auto-capitalized) are decapitalized before learning anyway,
     *  so they are never skipped here.
     */
    @JvmStatic
    fun shouldSkip(enabled: Boolean, word: String, wasAutoCapitalized: Boolean, isBeginningOfSentence: Boolean): Boolean =
        enabled && !wasAutoCapitalized && !isBeginningOfSentence && isSimplyCapitalized(word)
}
