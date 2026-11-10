// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.swipesensitivity

import kotlin.test.Test
import kotlin.test.assertEquals

class SwipeSensitivityTest {
    // dx < 0 selects (percentLeft), dx > 0 takes the selection back (percentRight)
    private val dxLeft = -1
    private val dxRight = 1

    @Test fun defaultKeepsUpstreamStep() {
        assertEquals(26, SwipeSensitivity.step(26, dxLeft, 100, 100, started = false))
        assertEquals(26, SwipeSensitivity.step(26, dxLeft, 100, 100, started = true))
        assertEquals(26, SwipeSensitivity.step(26, dxRight, 100, 100, started = true))
    }

    @Test fun eachDirectionUsesItsOwnPercent() {
        assertEquals(13, SwipeSensitivity.step(26, dxLeft, 200, 100, started = true))
        assertEquals(26, SwipeSensitivity.step(26, dxRight, 200, 100, started = true))
        assertEquals(26, SwipeSensitivity.step(26, dxLeft, 100, 200, started = true))
        assertEquals(13, SwipeSensitivity.step(26, dxRight, 100, 200, started = true))
    }

    @Test fun zeroDisplacementFallsToRightPercent() {
        // steps are 0 for dx 0 either way, so only the returned step is observable
        assertEquals(13, SwipeSensitivity.step(26, 0, 100, 200, started = true))
    }

    @Test fun higherSensitivityShortensStepOnceStarted() {
        assertEquals(13, SwipeSensitivity.step(26, dxLeft, 200, 200, started = true))
        assertEquals(13, SwipeSensitivity.step(26, dxRight, 200, 200, started = true))
    }

    @Test fun higherSensitivityKeepsUpstreamStartThresholdPerDirection() {
        assertEquals(26, SwipeSensitivity.step(26, dxLeft, 200, 100, started = false))
        assertEquals(26, SwipeSensitivity.step(26, dxRight, 100, 200, started = false))
    }

    @Test fun lowerSensitivityLengthensEveryStep() {
        assertEquals(104, SwipeSensitivity.step(26, dxLeft, 25, 25, started = false))
        assertEquals(104, SwipeSensitivity.step(26, dxLeft, 25, 25, started = true))
        assertEquals(104, SwipeSensitivity.step(26, dxRight, 25, 25, started = true))
    }

    @Test fun stepNeverZero() {
        assertEquals(1, SwipeSensitivity.step(1, dxLeft, SwipeSensitivity.MAX, SwipeSensitivity.MAX, started = true))
    }
}
