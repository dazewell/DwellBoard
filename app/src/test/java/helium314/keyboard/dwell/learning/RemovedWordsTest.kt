// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learning

import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemovedWordsTest {
    private val base = System.nanoTime() + TimeUnit.DAYS.toNanos(1) // later than any mark another test left
    private val en = listOf(Locale.ENGLISH)

    @Test fun removalAfterAWriteWasQueuedStopsIt() {
        RemovedWords.markRemoved("Quobble", en, base + 10)
        assertTrue(RemovedWords.removedSince("quobble", Locale.ENGLISH, base))
        assertTrue(RemovedWords.removedSince("Quobble", Locale.ENGLISH, base + 10))
    }

    @Test fun aLowercaseRemovalStopsACapitalizedWrite() {
        RemovedWords.markRemoved("darnel", en, base + 10)
        assertTrue(RemovedWords.removedSince("Darnel", Locale.ENGLISH, base))
    }

    @Test fun turkishCapitalsMatchTheirOwnLowercase() {
        val tr = Locale.forLanguageTag("tr")
        RemovedWords.markRemoved("Işıkçı", listOf(tr), base + 10)
        assertTrue(RemovedWords.removedSince("ışıkçı", tr, base))
        RemovedWords.markRemoved("İzmirli", listOf(tr), base + 10)
        assertTrue(RemovedWords.removedSince("izmirli", tr, base))
    }

    @Test fun aWriteQueuedAfterTheRemovalLands() {
        RemovedWords.markRemoved("wibblet", en, base)
        assertFalse(RemovedWords.removedSince("wibblet", Locale.ENGLISH, base + 1))
    }

    @Test fun otherWordsAreUnaffected() {
        RemovedWords.markRemoved("frandle", en, base + 10)
        assertFalse(RemovedWords.removedSince("frandles", Locale.ENGLISH, base))
    }

    @Test fun oldRemovalsArePruned() {
        RemovedWords.markRemoved("plimsy", en, base)
        RemovedWords.markRemoved("other", en, base + TimeUnit.SECONDS.toNanos(61))
        assertFalse(RemovedWords.removedSince("plimsy", Locale.ENGLISH, base - 1))
    }
}
