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
 * Marked on the main thread by a bin tap, read on the IO thread when a write lands. Keyed in any case, since removing
 * "Darn" can block "darn".
 */
object RemovedWords {
    // a write lands 25 s after it was queued, so an older removal can no longer be newer than any waiting write
    private val KEEP_NS = TimeUnit.SECONDS.toNanos(60)
    private val removedAt = ConcurrentHashMap<String, Long>()

    /** When a write is queued, to pass to [removedSince] when it lands. */
    @JvmStatic
    fun now(): Long = System.nanoTime()

    fun markRemoved(word: String, now: Long = now()) {
        removedAt.values.removeIf { now - it > KEEP_NS }
        removedAt[key(word)] = now
    }

    /** Whether [word] was removed after a write queued at [queuedAt], so that write must not land. */
    fun removedSince(word: String, queuedAt: Long): Boolean = removedAt[key(word)]?.let { it - queuedAt >= 0 } == true

    private fun key(word: String) = word.lowercase(Locale.ROOT)
}
