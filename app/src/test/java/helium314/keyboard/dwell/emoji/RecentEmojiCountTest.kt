// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import kotlin.test.Test
import kotlin.test.assertEquals

class RecentEmojiCountTest {
    @Test fun unsetKeepsDeviceDefault() = assertEquals(32, RecentEmojiCount.count(32, 0))
    @Test fun growsUpToTriple() = assertEquals(96, RecentEmojiCount.count(32, 96))
    @Test fun clampsOutOfRangeCount() {
        assertEquals(32, RecentEmojiCount.count(32, 5))
        assertEquals(117, RecentEmojiCount.count(39, 500))
        assertEquals(32, RecentEmojiCount.count(32, -3))
    }
    @Test fun storageCoversLargestDefault() = assertEquals(RecentEmojiCount.MAX_STORED, RecentEmojiCount.count(39, 1000))
    @Test fun columnsAutoOrClamped() {
        assertEquals(0, RecentEmojiCount.columns(0))
        assertEquals(4, RecentEmojiCount.columns(2))
        assertEquals(9, RecentEmojiCount.columns(9))
        assertEquals(12, RecentEmojiCount.columns(99))
    }
}
