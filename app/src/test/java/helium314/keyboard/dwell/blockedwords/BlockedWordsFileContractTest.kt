// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.blockedwords

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.dwell.FacilitatorHarness
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The block list screen reads the files upstream's `DictionaryGroup` writes. This blocks a word through the real
 * facilitator and reads it back with [BlockedWords], so a change to upstream's file name or format fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowLocaleManagerCompat::class, ShadowInputMethodManager2::class])
class BlockedWordsFileContractTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun load(word: String): List<BlockedWord> {
        val until = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < until) {
            val words = runBlocking { BlockedWords.load(context) }.orEmpty()
            if (words.any { it.word == word }) return words
            Thread.sleep(10)
        }
        return runBlocking { BlockedWords.load(context) }.orEmpty()
    }

    @Test fun `a word removed from suggestions shows on the block list and unblocking clears it`() {
        FacilitatorHarness.loadSettings(context)
        val h = FacilitatorHarness(context, setOf("darn"), withContext = true)
        h.facilitator.removeWord("darn") // the main dictionary can't drop it, so it is blocked
        assertEquals(listOf(BlockedWord(Locale.ENGLISH, "darn")), load("darn"))

        assertTrue(runBlocking { BlockedWords.unblock(context, listOf(BlockedWord(Locale.ENGLISH, "darn"))) }.isEmpty())
        assertEquals(emptyList(), runBlocking { BlockedWords.load(context) })
    }
}
