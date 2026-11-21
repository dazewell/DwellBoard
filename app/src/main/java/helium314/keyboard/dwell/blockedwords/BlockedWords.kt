// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.blockedwords

import android.content.Context
import android.content.Intent
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/** A word the keyboard never suggests, because it was removed from the suggestion strip. */
data class BlockedWord(val locale: Locale, val word: String)

/**
 * Lists and unblocks the words in the keyboard's block list: one file per language, `filesDir/blacklists/<tag>.txt`,
 * written by `DictionaryGroup` in `DictionaryFacilitatorImpl`. The keyboard keeps its own copy in memory, so an unblock
 * is followed by the same broadcast that reloads dictionaries after one is added or deleted.
 *
 * The keyboard writes these files without a lock that can be shared, so a rewrite here can still race the keyboard
 * blocking a word at the same instant, just as the keyboard's own writes race each other. The mutex only orders ours.
 */
object BlockedWords {
    private const val TAG = "BlockedWords"
    private const val SUFFIX = ".txt"
    private val writeLock = Mutex()

    private fun directory(context: Context) = File(context.filesDir, "blacklists")

    // the same name DictionaryGroup reads, so a locale and its file are one to one
    private fun file(context: Context, locale: Locale) = File(directory(context), locale.toLanguageTag() + SUFFIX)

    /** Blocked words of all languages, or null if a file could not be read. */
    suspend fun load(context: Context): List<BlockedWord>? = withContext(Dispatchers.IO) {
        try {
            val directory = directory(context)
            if (!directory.isDirectory) return@withContext emptyList()
            val files = directory.listFiles() ?: return@withContext null
            files.filter { it.isFile }.flatMap { file ->
                val locale = localeOfFile(file.name) ?: return@flatMap emptyList()
                blockedLines(file.readLines()).map { BlockedWord(locale, it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "cannot read blocked words", e)
            null
        }
    }

    /**
     * Removes the words from the block list and returns those that could not be removed. It finishes even if the caller
     * is cancelled, because a file written without the reload broadcast would leave the keyboard blocking the words.
     */
    suspend fun unblock(context: Context, words: Collection<BlockedWord>): List<BlockedWord> =
        withContext(NonCancellable + Dispatchers.IO) {
            writeLock.withLock {
                val failed = ArrayList<BlockedWord>()
                var changed = false
                for ((locale, localeWords) in words.groupBy { it.locale }) {
                    try {
                        if (rewrite(file(context, locale), localeWords.mapTo(HashSet()) { it.word })) changed = true
                    } catch (e: Exception) {
                        Log.e(TAG, "cannot unblock words", e)
                        failed += localeWords
                    }
                }
                if (changed) context.sendBroadcast(
                    Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION).setPackage(context.packageName)
                )
                failed
            }
        }

    /** Returns whether the file changed. A file already missing has nothing left to unblock. */
    private fun rewrite(file: File, unblocked: Set<String>): Boolean {
        if (!file.isFile) return false
        val lines = file.readLines()
        if (lines.none { it in unblocked }) return false
        val kept = remainingLines(lines, unblocked)
        if (kept.isEmpty()) {
            if (!file.delete() && file.exists()) throw IOException("cannot delete ${file.name}")
            return true
        }
        // a write that fails halfway must not leave a truncated list, which would unblock everything after the break
        val temporary = File(file.parentFile, file.name + ".tmp")
        try {
            // synced first, or a power cut right after the rename can leave the renamed file empty
            FileOutputStream(temporary).use {
                it.write(kept.joinToString("") { word -> word + "\n" }.toByteArray())
                it.fd.sync()
            }
            if (!temporary.renameTo(file)) throw IOException("cannot replace ${file.name}")
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        return true
    }
}

/** The language of a block list file, or null for a file the keyboard would never read. */
internal fun localeOfFile(name: String): Locale? {
    if (!name.endsWith(".txt")) return null
    val tag = name.removeSuffix(".txt")
    val locale = Locale.forLanguageTag(tag)
    return locale.takeIf { it.toLanguageTag() == tag }
}

/** The words in a block list file, skipping blank and repeated lines. */
internal fun blockedLines(lines: List<String>): List<String> = lines.filter { it.isNotBlank() }.distinct()

/** The lines to write back after unblocking, each word once and with every line ended. */
internal fun remainingLines(lines: List<String>, unblocked: Set<String>): List<String> =
    blockedLines(lines).filterNot { it in unblocked }
