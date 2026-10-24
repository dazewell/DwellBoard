// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.enterswipe

import kotlin.test.Test
import kotlin.test.assertEquals

class EnterSwipeTest {
    @Test fun keepsValidModes() {
        assertEquals(0, EnterSwipe.mode(0))
        assertEquals(1, EnterSwipe.mode(1))
        assertEquals(2, EnterSwipe.mode(2))
    }
    @Test fun clampsOutOfRangeModes() {
        assertEquals(0, EnterSwipe.mode(-1))
        assertEquals(2, EnterSwipe.mode(7))
    }
}
