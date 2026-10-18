// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.rowgap

import kotlin.test.Test
import kotlin.test.assertEquals

class SpaceRowGapTest {
    @Test fun clampsStoredValue() {
        assertEquals(0f, SpaceRowGap.clamp(-0.5f))
        assertEquals(0f, SpaceRowGap.clamp(Float.NaN))
        assertEquals(0.3f, SpaceRowGap.clamp(0.3f))
        assertEquals(SpaceRowGap.MAX, SpaceRowGap.clamp(7f))
    }

    @Test fun keepsTotalHeight() {
        // 4 rows of 0.25 with a gap of half a row: rows shrink, the gap gives the height back
        val rows = 4
        val gap = 0.5f
        val scale = SpaceRowGap.rowScale(rows, gap)
        val total = rows * 0.25f * scale + gap * 0.25f
        assertEquals(1f, total, 1e-6f)
    }

    @Test fun largestGapLeavesRowsPositive() = assertEquals(0.5f, SpaceRowGap.rowScale(2, SpaceRowGap.MAX))
}
