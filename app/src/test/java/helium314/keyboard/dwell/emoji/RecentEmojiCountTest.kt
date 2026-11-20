// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import kotlin.test.Test
import kotlin.test.assertEquals

class RecentEmojiCountTest {
    @Test fun defaultRoundsDeviceCountToWholeRows() {
        assertEquals(77, RecentEmojiCount.maxKeys(0, 80, 7)) // 80 / 7 = 11.4 rows
        assertEquals(80, RecentEmojiCount.maxKeys(0, 80, 8))
        assertEquals(84, RecentEmojiCount.maxKeys(0, 80, 12)) // 6.7 rows rounds up
        assertEquals(12, RecentEmojiCount.maxKeys(0, 3, 12)) // never less than one row
    }
    @Test fun chosenRowsTimesActualColumns() {
        assertEquals(21, RecentEmojiCount.maxKeys(3, 80, 7))
        assertEquals(18, RecentEmojiCount.maxKeys(3, 80, 6)) // split spacer took a column: still whole rows
    }
    @Test fun cappedAtStoredListInWholeRows() {
        assertEquals(RecentEmojiCount.MAX_STORED, RecentEmojiCount.maxKeys(RecentEmojiCount.MAX_ROWS, 80, RecentEmojiCount.MAX_COLUMNS))
        assertEquals(238, RecentEmojiCount.maxKeys(RecentEmojiCount.MAX_ROWS * 3, 80, 14)) // wide auto layout: 17 rows of 14
    }
    @Test fun unknownColumnsKeepDeviceDefault() = assertEquals(80, RecentEmojiCount.maxKeys(5, 80, 0))
    @Test fun rowsDefaultOrClamped() {
        assertEquals(0, RecentEmojiCount.rows(0))
        assertEquals(0, RecentEmojiCount.rows(-4))
        assertEquals(7, RecentEmojiCount.rows(7))
        assertEquals(RecentEmojiCount.MAX_ROWS, RecentEmojiCount.rows(99))
    }
    @Test fun columnsAutoOrClamped() {
        assertEquals(0, RecentEmojiCount.columns(0))
        assertEquals(4, RecentEmojiCount.columns(2))
        assertEquals(9, RecentEmojiCount.columns(9))
        assertEquals(12, RecentEmojiCount.columns(99))
    }
}
