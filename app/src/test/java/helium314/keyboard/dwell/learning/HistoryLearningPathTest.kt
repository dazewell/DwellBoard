// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learning

import android.app.Application
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.dwell.FacilitatorHarness
import helium314.keyboard.dwell.FacilitatorHarness.Companion.previous
import helium314.keyboard.latin.DictionaryFacilitator
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The fork's learning options through upstream's own `DictionaryFacilitatorImpl.addToUserHistory`, not the fork
 * helpers alone: upstream 4.3 changed which word that path learns and when, and helper tests could not see it.
 * Expectations are the behaviour before the 4.3 merge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowLocaleManagerCompat::class, ShadowInputMethodManager2::class])
class HistoryLearningPathTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val harnesses = mutableListOf<FacilitatorHarness>()

    @BeforeTest fun reset() = context.prefs().edit { clear() }
    @AfterTest fun cancel() = harnesses.forEach { it.cancelWaiting() }

    private fun harness(vararg mainWords: String, addNow: Boolean = true, prefs: Map<String, Boolean> = emptyMap()): FacilitatorHarness {
        context.prefs().edit { prefs.forEach { (key, value) -> putBoolean(key, value) } }
        FacilitatorHarness.loadSettings(context)
        return FacilitatorHarness(context, mainWords.toSet()).also {
            if (addNow) it.addNow()
            harnesses += it
        }
    }

    private fun learnedWord(h: FacilitatorHarness) = h.awaitLearned(1).single().word

    @Test fun `new word typed first in a sentence is learned in lowercase`() {
        val h = harness()
        h.add("Zorbly", NgramContext.BEGINNING_OF_SENTENCE, wasAutoCapitalized = true)
        assertEquals("zorbly", learnedWord(h))
    }

    @Test fun `known word typed first in a sentence is learned in lowercase`() {
        for (main in listOf(arrayOf("hello"), arrayOf("hello", "Hello"))) {
            val h = harness(*main)
            h.add("Hello", NgramContext.BEGINNING_OF_SENTENCE, wasAutoCapitalized = true)
            assertEquals("hello", learnedWord(h), main.joinToString())
        }
    }

    @Test fun `name typed first in a sentence keeps its capital`() {
        val h = harness("Paris")
        h.add("Paris", NgramContext.BEGINNING_OF_SENTENCE, wasAutoCapitalized = true)
        assertEquals("Paris", learnedWord(h))
    }

    @Test fun `capitalized word mid-sentence is learned as typed by default`() {
        val h = harness()
        h.add("Zorbly", previous("the"))
        assertEquals("Zorbly", learnedWord(h))
    }

    @Test fun `capitalized words are learned in lowercase with the option, and so is the next word's previous word`() {
        val h = harness(prefs = mapOf(Settings.PREF_DONT_LEARN_CAPITALIZED to true))
        h.add("Zorbly", previous("the"))
        h.add("is", previous("Zorbly"))
        val learned = h.awaitLearned(2).associateBy { it.word }
        assertEquals(setOf("zorbly", "is"), learned.keys)
        assertEquals("zorbly", learned.getValue("is").previousWord)
    }

    @Test fun `all caps word first in a sentence is learned fully lowercase with the option`() {
        val h = harness(prefs = mapOf(Settings.PREF_DONT_LEARN_CAPITALIZED to true))
        h.add("ZORBLY", NgramContext.BEGINNING_OF_SENTENCE, wasAutoCapitalized = true)
        assertEquals("zorbly", learnedWord(h))
    }

    // #emoji-autocap capitalizes "and" in "Done. 🚀 and", where the text before is an emoji, not a sentence start
    @Test fun `word capitalized after an emoji is learned in lowercase`() {
        val h = harness()
        h.add("Zorbly", previous("🚀"), wasAutoCapitalized = true)
        assertEquals("zorbly", learnedWord(h))
    }

    // upstream's guard for word caps fields stays for anything but an emoji
    @Test fun `word auto-capitalized after a plain word is not learned`() {
        val h = harness(addNow = false)
        h.add("Zorbly", previous("the"), wasAutoCapitalized = true)
        assertFalse("Zorbly" in h.waiting())
    }

    @Test fun `with learn only when tapped a typed new word waits for a tap`() {
        val h = harness(addNow = false, prefs = mapOf(Settings.PREF_LEARN_ONLY_PICKED to true))
        assertTrue(Settings.getValues().mLearnOnlyPicked)
        h.add("zorbly", previous("the"))
        assertFalse("zorbly" in h.waiting())
        h.add("zorbly", previous("the"), picked = true)
        assertTrue("zorbly" in h.waiting())
        h.add("hello", previous("the"))
        assertFalse("hello" in h.waiting()) // not known yet either
    }

    @Test fun `with learn only when tapped a typed known word is learned and a tapped new one counts as valid`() {
        val h = harness("hello", prefs = mapOf(Settings.PREF_LEARN_ONLY_PICKED to true))
        h.add("hello", previous("the"))
        h.add("zorbly", previous("the"), picked = true)
        val learned = h.awaitLearned(2).associateBy { it.word }
        assertEquals(true, learned.getValue("hello").valid)
        assertEquals(true, learned.getValue("zorbly").valid)
    }

    @Test fun `without the option a typed new word is learned but not as valid`() {
        val h = harness()
        h.add("zorbly", previous("the"))
        assertEquals(false, h.awaitLearned(1).single().valid)
    }

    // the learned form can differ from the typed one; a correction unlearns what was typed
    @Test fun `correcting a word within the delay cancels learning it`() {
        val h = harness(addNow = false)
        h.add("Zorbly", NgramContext.BEGINNING_OF_SENTENCE, wasAutoCapitalized = true)
        val job = h.waiting().getValue("Zorbly")
        h.facilitator.unlearnFromUserHistory("Zorbly", NgramContext.BEGINNING_OF_SENTENCE, 0L,
            DictionaryFacilitator.UnlearnEvent.REVERT)
        assertTrue(job.isCancelled)
        assertFalse("Zorbly" in h.waiting())
    }

    // #learning-contexts: the word goes to the dictionary of the context it was typed in, which may be closed by then
    @Test fun `a word learned just before a learning-context switch still goes to the old context`() {
        val h = harness()
        val old = h.history
        val new = h.mockHistory()
        h.add("zorbly", previous("the"))
        val learned = h.awaitLearned(1).single()
        assertTrue(learned.stillUsed.asBoolean)
        h.switchTo(new)
        assertFalse(learned.stillUsed.asBoolean)
        org.mockito.Mockito.verify(old).updateEntriesForWordLater(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("zorbly"), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyInt())
        org.mockito.Mockito.verifyNoInteractions(new)
    }
}
