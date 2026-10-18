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
    // 1080 px phone: key slot 108 px, cells 34 x 42 px
    @Test fun overhangLimitLeavesHalfACellMargin() =
        assertEquals(0.9f * 1.2f * 108f - 17f - 21f, SpaceRowGap.overhangLimit(108f, 34, 42), 1e-3f)

    @Test fun smallGapSplitsEvenly() = assertEquals(20f to 20f, SpaceRowGap.split(40f, 23f, 78f))

    @Test fun largeGapIsCappedOnBothSides() {
        val (below, above) = SpaceRowGap.split(240f, 23f, 78f)
        assertEquals(55f, below) // plus the normal 23 px gap reaches the limit
        assertEquals(78f, above)
    }

    @Test fun limitBelowNormalGapGivesSpaceRowEverything() = assertEquals(0f to 10f, SpaceRowGap.split(10f, 30f, 20f))

    @Test fun upperRowsKeepHalfTheirHeight() = assertEquals(0.25f, SpaceRowGap.gapHeight(SpaceRowGap.MAX, 1f, 0.5f, 2), 1e-6f)
}
