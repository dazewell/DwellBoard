// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.capitalization

import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.NgramContext.WordInfo

/**
 *  Remembers the last word learned in lowercase, so the word typed after it is learned as following that form.
 *  The ngram context comes from the editor text and holds "Paris", but the user history only knows "paris", and
 *  learning looks up the previous word exactly, so without this the "paris is" pair would be dropped.
 *  Upstream's tryChangingWords does the same for words decapitalized at sentence start.
 *
 *  Only used from the input thread, like tryChangingWords. The pair is kept until another word is learned in
 *  lowercase, so retyping the next word still finds it.
 */
class LowercasedPreviousWord {
    private var typed: String? = null
    private var learned: String? = null

    fun remember(typedWord: String, learnedWord: String) {
        if (typedWord == learnedWord) return
        typed = typedWord
        learned = learnedWord
    }

    /** [ngramContext] with its previous word replaced by the remembered lowercase form, if it is the remembered word. */
    fun apply(enabled: Boolean, ngramContext: NgramContext): NgramContext {
        val typed = typed ?: return ngramContext
        if (!enabled || ngramContext.prevWordCount == 0 || ngramContext.isNthPrevWordBeginningOfSentence(1)
            || ngramContext.getNthPrevWord(1)?.toString() != typed
        ) return ngramContext
        val prevWords = Array(ngramContext.prevWordCount) { i ->
            when {
                i == 0 -> WordInfo(learned)
                ngramContext.isNthPrevWordBeginningOfSentence(i + 1) -> WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO
                else -> WordInfo(ngramContext.getNthPrevWord(i + 1))
            }
        }
        // same max previous word count as the editor-derived contexts from NgramContextUtils
        return NgramContext(*prevWords)
    }
}
