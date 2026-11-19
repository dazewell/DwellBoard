// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.clipboard

import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.settings.LearningContexts.DEFAULT
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContextPinsTest {
    private fun clip(id: Long, pinned: Boolean) = ClipboardHistoryEntry(id, id, pinned, "clip $id", null, null)
    private val valid = setOf("aaaaaaaa", "bbbbbbbb")

    @Test fun unknownOrMissingContextIsDefault() {
        assertEquals(DEFAULT, ContextPins.contextOf(null, valid))
        assertEquals(DEFAULT, ContextPins.contextOf("", valid))
        assertEquals(DEFAULT, ContextPins.contextOf("deadbeef", valid))
        assertEquals("aaaaaaaa", ContextPins.contextOf("aaaaaaaa", valid))
    }

    @Test fun switchingHidesOtherPinsAndShowsOwn() {
        val unpinned = clip(1, false)
        val defaultPin = clip(2, true)
        val aPin = clip(3, true)
        val bPin = clip(4, true)
        val contexts = mapOf(3L to "aaaaaaaa", 4L to "bbbbbbbb")
        val (hide, show) = ContextPins.partition(listOf(unpinned, defaultPin, aPin), listOf(bPin), contexts, valid, "bbbbbbbb")
        assertEquals(listOf(defaultPin, aPin), hide)
        assertEquals(listOf(bPin), show)
    }

    @Test fun unpinnedClipsAreNeverHidden() {
        val (hide, show) = ContextPins.partition(listOf(clip(1, false)), emptyList(), mapOf(1L to "aaaaaaaa"), valid, "bbbbbbbb")
        assertEquals(emptyList<ClipboardHistoryEntry>(), hide)
        assertEquals(emptyList<ClipboardHistoryEntry>(), show)
    }

    @Test fun pinsOfADeletedContextShowInDefault() {
        val orphan = clip(5, true)
        val (_, show) = ContextPins.partition(emptyList(), listOf(orphan), mapOf(5L to "cccccccc"), valid, DEFAULT)
        assertEquals(listOf(orphan), show)
    }

    @Test fun nothingMovesWhenAlreadyRight() {
        val aPin = clip(3, true)
        val (hide, show) = ContextPins.partition(listOf(clip(1, false), aPin), emptyList(), mapOf(3L to "aaaaaaaa"), valid, "aaaaaaaa")
        assertEquals(0, hide.size + show.size)
    }
}
