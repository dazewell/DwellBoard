// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learning

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Suggestions the user removed while a history write for the same word may still be waiting. Each typing queues a
 * write that lands 25 s later, takes the typed word off the block list and adds it to history, so without this a word
 * removed within that time was unblocked and relearned. Typing a word twice replaces its entry in the facilitator's
 * waiting map while the first write still runs, so cancelling from the map cannot reach every write; each write
 * checks here instead.
 *
 * Marked on the main thread by a bin tap, read on the IO thread when a write lands. Removing "Darn" can block "darn",
 * so each side also goes by its lowercase in the language's own locale: in Turkish "Işık" lowercases to "ışık", which
 * a locale-free fold would not match.
 */
object RemovedWords {
    // a write lands 25 s after it was queued, so an older removal can no longer be newer than any waiting write
    private val KEEP_NS = TimeUnit.SECONDS.toNanos(60)
    private val removedAt = ConcurrentHashMap<String, Long>()

    /** When a write is queued, to pass to [removedSince] when it lands. */
    @JvmStatic
    fun now(): Long = System.nanoTime()

    /** [word] was removed, as shown, in the languages of [locales]. */
    fun markRemoved(word: String, locales: Collection<Locale>, now: Long = now()) {
        removedAt.values.removeIf { now - it > KEEP_NS }
        removedAt[word] = now
        locales.forEach { removedAt[word.lowercase(it)] = now }
    }

    /** Whether [word], typed in [locale], was removed after a write queued at [queuedAt], so that write must not land. */
    fun removedSince(word: String, locale: Locale, queuedAt: Long): Boolean =
        removedAfter(word, queuedAt) || removedAfter(word.lowercase(locale), queuedAt)

    private fun removedAfter(key: String, queuedAt: Long) = removedAt[key]?.let { it - queuedAt >= 0 } == true
}
