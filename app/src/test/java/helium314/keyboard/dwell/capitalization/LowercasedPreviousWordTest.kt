// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.capitalization

import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.NgramContext.WordInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LowercasedPreviousWordTest {
    private val parisAfterGo = NgramContext(WordInfo("Paris"), WordInfo("go"), WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO)

    @Test fun replacesRememberedPreviousWord() {
        val w = LowercasedPreviousWord().apply { remember("Paris", "paris") }
        val result = w.apply(true, parisAfterGo)
        assertEquals("paris", result.getNthPrevWord(1).toString())
        assertEquals("go", result.getNthPrevWord(2).toString())
        assertTrue(result.isNthPrevWordBeginningOfSentence(3))
        assertEquals(3, result.prevWordCount)
    }

    @Test fun keepsPairUntilAnotherWordIsLowercased() {
        val w = LowercasedPreviousWord().apply { remember("Paris", "paris"); remember("is", "is") }
        assertEquals("paris", w.apply(true, parisAfterGo).getNthPrevWord(1).toString())
        w.remember("London", "london")
        assertSame(parisAfterGo, w.apply(true, parisAfterGo))
    }

    @Test fun leavesContextAloneOtherwise() {
        assertSame(parisAfterGo, LowercasedPreviousWord().apply(true, parisAfterGo))
        val w = LowercasedPreviousWord().apply { remember("Paris", "paris") }
        assertSame(parisAfterGo, w.apply(false, parisAfterGo))
        assertSame(NgramContext.BEGINNING_OF_SENTENCE, w.apply(true, NgramContext.BEGINNING_OF_SENTENCE))
        val other = NgramContext(WordInfo("Rome"))
        assertSame(other, w.apply(true, other))
    }
}
