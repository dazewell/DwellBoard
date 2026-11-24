// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learning

import helium314.keyboard.latin.dictionary.Dictionary
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LearnedWordPopupTest {
    @Test fun newWordStoredValidIsLearned() {
        assertTrue(LearnedWordPopup.becameSuggestable(Dictionary.NOT_A_PROBABILITY, 111))
    }

    @Test fun newWordStoredWithoutCountIsNotLearnedYet() {
        assertFalse(LearnedWordPopup.becameSuggestable(Dictionary.NOT_A_PROBABILITY, Dictionary.NOT_A_PROBABILITY))
    }

    @Test fun alreadySuggestableWordDoesNotPopAgain() {
        assertFalse(LearnedWordPopup.becameSuggestable(111, 115))
    }
}
