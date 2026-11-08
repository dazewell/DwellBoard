// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.swipesensitivity

import kotlin.test.Test
import kotlin.test.assertEquals

class SwipeSensitivityTest {
    @Test fun defaultKeepsUpstreamStep() {
        assertEquals(26, SwipeSensitivity.step(26, 100))
    }

    @Test fun higherSensitivityShortensStep() {
        assertEquals(13, SwipeSensitivity.step(26, 200))
        assertEquals(104, SwipeSensitivity.step(26, 25))
    }

    @Test fun stepNeverZero() {
        assertEquals(1, SwipeSensitivity.step(1, SwipeSensitivity.MAX))
    }
}
