// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.swipesensitivity

import kotlin.test.Test
import kotlin.test.assertEquals

class SwipeSensitivityTest {
    @Test fun defaultKeepsUpstreamStep() {
        assertEquals(26, SwipeSensitivity.step(26, 100, started = false))
        assertEquals(26, SwipeSensitivity.step(26, 100, started = true))
    }

    @Test fun higherSensitivityShortensStepOnceStarted() {
        assertEquals(13, SwipeSensitivity.step(26, 200, started = true))
    }

    @Test fun higherSensitivityKeepsUpstreamStartThreshold() {
        assertEquals(26, SwipeSensitivity.step(26, 200, started = false))
    }

    @Test fun lowerSensitivityLengthensEveryStep() {
        assertEquals(104, SwipeSensitivity.step(26, 25, started = false))
        assertEquals(104, SwipeSensitivity.step(26, 25, started = true))
    }

    @Test fun stepNeverZero() {
        assertEquals(1, SwipeSensitivity.step(1, SwipeSensitivity.MAX, started = true))
    }
}
