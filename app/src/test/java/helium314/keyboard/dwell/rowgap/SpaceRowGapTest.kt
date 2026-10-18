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

    // 4 rows of 0.25: half a row is 0.125 of the keyboard
    @Test fun gapInAverageRows() = assertEquals(0.125f, SpaceRowGap.gapHeight(0.5f, 1f, 0.75f, 4), 1e-6f)

    @Test fun outOfRangeStoredGapIsClamped() = assertEquals(0.25f, SpaceRowGap.gapHeight(9f, 1f, 0.75f, 4), 1e-6f)

    // two equal rows at the maximum would eat the whole letter row; it keeps half instead
    @Test fun upperRowsKeepHalfTheirHeight() = assertEquals(0.25f, SpaceRowGap.gapHeight(SpaceRowGap.MAX, 1f, 0.5f, 2), 1e-6f)
}
