// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learning

/**
 * "Learn new words only when tapped": a word that neither a dictionary nor the learned history knows yet is learned
 * only when the user picks it on the suggestion strip, not when it is just typed and followed by a space.
 */
object PickedLearning {
    /**
     * Whether a committed word goes into the user history. [isKnown] costs dictionary lookups, so it is only asked
     * when the setting is on and the word was typed rather than picked.
     */
    inline fun shouldLearn(enabled: Boolean, picked: Boolean, isKnown: () -> Boolean): Boolean =
        !enabled || picked || isKnown()

    /**
     * Whether the word is learned as valid. The native history stores a new word that is not valid with a count of
     * zero, so it would only be suggested after a second pick; a picked word counts as confirmed instead.
     */
    fun learnAsValid(enabled: Boolean, picked: Boolean, inMainDictionary: Boolean): Boolean =
        inMainDictionary || (enabled && picked)

    /**
     * Whether a pick confirms the form that is learned. "Learn capitalized words in lowercase" can turn a tapped
     * "Monday" into "monday", which the user never confirmed; learning that as valid would let it win over the
     * dictionary's "Monday" after one tap.
     */
    fun learnsTappedForm(picked: Boolean, tappedWord: String, wordToLearn: String): Boolean =
        picked && wordToLearn == tappedWord
}
