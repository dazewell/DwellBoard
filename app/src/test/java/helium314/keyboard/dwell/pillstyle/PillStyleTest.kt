// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.pillstyle

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue

class PillStyleTest {
    // Every corner of the icon square lies within the round end of radius min(w, h) / 2 at (w - r, r).
    private fun assertInsideRoundEnd(w: Int, h: Int, icon: Int) {
        val left = PillStyle.hintIconLeft(w, h, icon)
        val top = PillStyle.hintIconTop(w, h, icon)
        val r = minOf(w, h) / 2f
        val cx = w - r
        for ((x, y) in listOf(left to top, left + icon to top, left to top + icon, left + icon to top + icon)) {
            // left of the cap centre the pill is a full band, so only x > cx needs the circle
            val inside = if (x <= cx) y in 0f..2 * r else hypot(x - cx, y - r) <= r
            assertTrue(inside, "corner ($x, $y) outside the round end of a ${w}x$h key")
        }
    }

    // 1080 px phone screenshot: the search key is 143 x 138 with a 30 px hint icon
    @Test fun nearSquareActionKey() = assertInsideRoundEnd(143, 138, 30)

    @Test fun wideKey() = assertInsideRoundEnd(220, 120, 30)

    @Test fun tallKey() = assertInsideRoundEnd(90, 140, 26)

    @Test fun iconStillInTheTopRightQuarter() {
        val left = PillStyle.hintIconLeft(143, 138, 30)
        val top = PillStyle.hintIconTop(143, 138, 30)
        assertTrue(left + 15 > 143 / 2f && top + 15 < 138 / 2f)
    }

    // An icon too big for the round end ends up centred on it instead of escaping the key
    @Test fun oversizedIconCentresOnRoundEnd() {
        val left = PillStyle.hintIconLeft(60, 60, 80)
        val top = PillStyle.hintIconTop(60, 60, 80)
        assertTrue(left == 30f - 40f && top == 30f - 40f)
    }
}
