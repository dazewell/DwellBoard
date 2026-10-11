// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.capitalization

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapitalizedLearningTest {
    @Test fun skipsCapitalizedAndAllCaps() {
        listOf("Hello", "HELLO", "NASA", "I", "A1B", "O'NEIL").forEach { assertTrue(CapitalizedLearning.isSimplyCapitalized(it), it) }
    }

    @Test fun keepsLowercaseAndMixedCase() {
        listOf("", "hello", "iPhone", "HELlo", "McDonald", "NASA's").forEach { assertFalse(CapitalizedLearning.isSimplyCapitalized(it), it) }
    }

    @Test fun neverSkipsWhenOffOrSentenceStart() {
        assertFalse(CapitalizedLearning.shouldSkip(false, "HELLO", false, false))
        assertFalse(CapitalizedLearning.shouldSkip(true, "HELLO", true, false))
        assertFalse(CapitalizedLearning.shouldSkip(true, "HELLO", false, true))
        assertTrue(CapitalizedLearning.shouldSkip(true, "HELLO", false, false))
    }
}
