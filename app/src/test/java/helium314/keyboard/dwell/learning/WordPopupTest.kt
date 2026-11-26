// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learning

import helium314.keyboard.latin.dictionary.Dictionary
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordPopupTest {
    @Test fun newWordStoredValidIsLearned() {
        assertTrue(WordPopup.becameSuggestable(Dictionary.NOT_A_PROBABILITY, 111))
    }

    @Test fun newWordStoredWithoutCountIsNotLearnedYet() {
        assertFalse(WordPopup.becameSuggestable(Dictionary.NOT_A_PROBABILITY, Dictionary.NOT_A_PROBABILITY))
    }

    @Test fun alreadySuggestableWordDoesNotPopAgain() {
        assertFalse(WordPopup.becameSuggestable(111, 115))
    }

    @Test fun popupShowsOnlyInTheFieldTheWordWasTypedIn() {
        assertTrue(WordPopup.shouldShow(3, 3, true))
        assertFalse(WordPopup.shouldShow(3, 4, true))
    }

    @Test fun popupIsDroppedWhenTheFieldNowForbidsIt() {
        assertFalse(WordPopup.shouldShow(3, 3, false))
    }
}
