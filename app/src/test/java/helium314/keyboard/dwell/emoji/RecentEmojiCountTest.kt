// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import kotlin.test.Test
import kotlin.test.assertEquals

class RecentEmojiCountTest {
    @Test fun defaultKeepsCurrentCount() = assertEquals(32, RecentEmojiCount.scaled(32, 1f))
    @Test fun growsUpToTriple() = assertEquals(96, RecentEmojiCount.scaled(32, 3f))
    @Test fun clampsOutOfRange() {
        assertEquals(32, RecentEmojiCount.scaled(32, 0f))
        assertEquals(117, RecentEmojiCount.scaled(39, 10f))
        assertEquals(32, RecentEmojiCount.scaled(32, Float.NaN))
    }
    @Test fun storageCoversLargestDefault() = assertEquals(RecentEmojiCount.MAX_STORED, RecentEmojiCount.scaled(39, 3f))
}
