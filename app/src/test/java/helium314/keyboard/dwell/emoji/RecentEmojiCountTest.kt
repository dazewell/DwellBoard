// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import kotlin.test.Test
import kotlin.test.assertEquals

class RecentEmojiCountTest {
    @Test fun unsetKeepsDeviceDefault() = assertEquals(32, RecentEmojiCount.count(32, 0))
    @Test fun growsUpToTriple() = assertEquals(96, RecentEmojiCount.count(32, 96))
    @Test fun shrinksDownToMinimum() {
        assertEquals(10, RecentEmojiCount.count(80, 10))
        assertEquals(25, RecentEmojiCount.count(80, 25))
        assertEquals(6, RecentEmojiCount.count(6, 3)) // floor never above the device default
    }
    @Test fun clampsOutOfRangeCount() {
        assertEquals(10, RecentEmojiCount.count(32, 5))
        assertEquals(117, RecentEmojiCount.count(39, 500))
        assertEquals(32, RecentEmojiCount.count(32, -3))
    }
    @Test fun storageCoversLargestDefault() = assertEquals(RecentEmojiCount.MAX_STORED, RecentEmojiCount.count(80, 1000))
    @Test fun rowRangeSnapsToWholeRows() {
        assertEquals(14..238, RecentEmojiCount.rowRange(80, 7)) // floor 10 rounds up, 240 rounds down
        assertEquals(12..240, RecentEmojiCount.rowRange(80, 12))
        assertEquals(6..18, RecentEmojiCount.rowRange(6, 6)) // small device default: floor is the default
    }
    @Test fun rowRangeNeedsKnownColumnsAndTwoRows() {
        assertEquals(null, RecentEmojiCount.rowRange(80, 0))
        assertEquals(null, RecentEmojiCount.rowRange(4, 12)) // not even one whole row fits
    }
    @Test fun columnsAutoOrClamped() {
        assertEquals(0, RecentEmojiCount.columns(0))
        assertEquals(4, RecentEmojiCount.columns(2))
        assertEquals(9, RecentEmojiCount.columns(9))
        assertEquals(12, RecentEmojiCount.columns(99))
    }
}
