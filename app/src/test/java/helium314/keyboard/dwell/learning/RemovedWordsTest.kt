// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learning

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemovedWordsTest {
    private val base = System.nanoTime() + TimeUnit.DAYS.toNanos(1) // later than any mark another test left

    @Test fun removalAfterAWriteWasQueuedStopsIt() {
        RemovedWords.markRemoved("Quobble", base + 10)
        assertTrue(RemovedWords.removedSince("quobble", base))
        assertTrue(RemovedWords.removedSince("QUOBBLE", base + 10))
    }

    @Test fun aWriteQueuedAfterTheRemovalLands() {
        RemovedWords.markRemoved("wibblet", base)
        assertFalse(RemovedWords.removedSince("wibblet", base + 1))
    }

    @Test fun otherWordsAreUnaffected() {
        RemovedWords.markRemoved("frandle", base + 10)
        assertFalse(RemovedWords.removedSince("frandles", base))
    }

    @Test fun oldRemovalsArePruned() {
        RemovedWords.markRemoved("plimsy", base)
        RemovedWords.markRemoved("other", base + TimeUnit.SECONDS.toNanos(61))
        assertFalse(RemovedWords.removedSince("plimsy", base - 1))
    }
}
