// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learnedwords

import android.content.Context
import helium314.keyboard.latin.personalization.PersonalizationHelper
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/** A word in the user history dictionary of a learning context. [probability] includes decay, so it ranks like suggestions do. */
data class LearnedWord(val locale: Locale, val word: String, val probability: Int, val count: Int)

/**
 * Lists and removes the learned words of a learning context. The dictionaries come from [PersonalizationHelper], so they
 * are the same instances the keyboard uses, and all access runs on the dictionary's own executor and lock.
 */
object LearnedWords {
    private const val TAG = "LearnedWords"

    /** Learned words of all languages in the context, or null if a dictionary could not be read. */
    suspend fun load(context: Context, contextId: String): List<LearnedWord>? {
        val locales = withContext(Dispatchers.IO) { PersonalizationHelper.getUserHistoryLocales(context, contextId) }
        val result = ArrayList<LearnedWord>()
        for (locale in locales) {
            result += loadLocale(context, contextId, locale) ?: return null
        }
        return result
    }

    private suspend fun loadLocale(context: Context, contextId: String, locale: Locale): List<LearnedWord>? =
        suspendCancellableCoroutine { cont ->
            val dict = PersonalizationHelper.getUserHistoryDictionary(context, locale, contextId)
            dict.runWithDictionary(false) { binaryDict ->
                if (binaryDict == null) {
                    cont.resume(null)
                    return@runWithDictionary
                }
                // the screen waits for the result, so it must come even if reading fails
                val words = try {
                    val words = ArrayList<LearnedWord>()
                    var token = 0
                    do {
                        val next = binaryDict.getNextWordProperty(token)
                        val property = next.mWordProperty ?: break
                        // a negative probability means the word has been forgotten, but not yet removed by GC
                        if (!property.mIsBeginningOfSentence && !property.mIsNotAWord && property.mProbabilityInfo.mProbability >= 0)
                            words += LearnedWord(locale, property.mWord, property.mProbabilityInfo.mProbability, property.mProbabilityInfo.mCount)
                        token = next.mNextToken
                    } while (token != 0)
                    words
                } catch (e: Exception) {
                    Log.e(TAG, "cannot read learned words", e)
                    null
                }
                cont.resume(words)
            }
        }

    /** Removes the words from the context's dictionaries and writes them to file. */
    suspend fun delete(context: Context, contextId: String, words: Collection<LearnedWord>) {
        for ((locale, localeWords) in words.groupBy { it.locale }) {
            suspendCancellableCoroutine { cont ->
                val dict = PersonalizationHelper.getUserHistoryDictionary(context, locale, contextId)
                dict.runWithDictionary(true) { binaryDict ->
                    try {
                        if (binaryDict != null) {
                            localeWords.forEach { binaryDict.removeUnigramEntry(it.word) }
                            binaryDict.flushWithGCIfHasUpdated()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "cannot remove learned words", e)
                    }
                    cont.resume(Unit)
                }
            }
        }
    }
}
