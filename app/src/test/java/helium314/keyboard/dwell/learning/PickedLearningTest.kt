// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learning

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class PickedLearningTest {
    @Test fun offLearnsEverythingWithoutLookingUp() {
        assertTrue(PickedLearning.shouldLearn(false, false) { fail("lookup while off") })
        assertTrue(PickedLearning.shouldLearn(false, true) { fail("lookup while off") })
    }

    @Test fun pickedWordIsLearnedWithoutLookingUp() {
        assertTrue(PickedLearning.shouldLearn(true, true) { fail("lookup for a picked word") })
    }

    @Test fun typedWordIsLearnedOnlyWhenKnown() {
        assertTrue(PickedLearning.shouldLearn(true, false) { true })
        assertFalse(PickedLearning.shouldLearn(true, false) { false })
    }

    @Test fun pickedNewWordIsValidOnlyWhenOn() {
        assertTrue(PickedLearning.learnAsValid(true, true, false))
        assertFalse(PickedLearning.learnAsValid(false, true, false))
        assertFalse(PickedLearning.learnAsValid(true, false, false))
        assertTrue(PickedLearning.learnAsValid(false, false, true))
    }
}
