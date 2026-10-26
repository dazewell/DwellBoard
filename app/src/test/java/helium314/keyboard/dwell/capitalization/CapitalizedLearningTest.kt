// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.capitalization

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapitalizedLearningTest {
    @Test fun detectsCapitalizedAndAllCaps() {
        listOf("Hello", "HELLO", "NASA", "I", "A1B", "O'NEIL").forEach { assertTrue(CapitalizedLearning.isSimplyCapitalized(it), it) }
    }

    @Test fun keepsLowercaseAndMixedCase() {
        listOf("", "hello", "iPhone", "HELlo", "McDonald", "NASA's").forEach { assertFalse(CapitalizedLearning.isSimplyCapitalized(it), it) }
    }

    @Test fun lowercasesOnlyWhenOnAndNotSentenceStart() {
        val en = Locale.ENGLISH
        assertEquals("HELLO", CapitalizedLearning.wordToLearn(false, "HELLO", false, false, en))
        assertEquals("Hello", CapitalizedLearning.wordToLearn(true, "Hello", true, false, en))
        assertEquals("Hello", CapitalizedLearning.wordToLearn(true, "Hello", false, true, en))
        // #40: the sentence-start path would otherwise decapitalize "CAPS" to "cAPS"
        assertEquals("caps", CapitalizedLearning.wordToLearn(true, "CAPS", true, false, en))
        assertEquals("words", CapitalizedLearning.wordToLearn(true, "WORDS", false, true, en))
        assertEquals("HELLO", CapitalizedLearning.wordToLearn(false, "HELLO", true, false, en))
        assertEquals("hello", CapitalizedLearning.wordToLearn(true, "HELLO", false, false, en))
        assertEquals("hello", CapitalizedLearning.wordToLearn(true, "Hello", false, false, en))
        assertEquals("iPhone", CapitalizedLearning.wordToLearn(true, "iPhone", false, false, en))
        assertEquals("HELlo", CapitalizedLearning.wordToLearn(true, "HELlo", false, false, en))
    }

    @Test fun keepsSingleLetterWords() {
        val en = Locale.ENGLISH
        listOf("I", "A", "I'm", "I’ll", "I've", "I'd").forEach { assertEquals(it, CapitalizedLearning.wordToLearn(true, it, false, false, en)) }
        assertEquals("o'neil", CapitalizedLearning.wordToLearn(true, "O'NEIL", false, false, en))
    }
}
