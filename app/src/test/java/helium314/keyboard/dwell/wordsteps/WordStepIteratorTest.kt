// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.wordsteps

import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.compat.CompatBreakIterator
import helium314.keyboard.latin.App
import helium314.keyboard.latin.common.WordBreakIterator
import helium314.keyboard.latin.common.stepsToCharCount
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class WordStepIteratorTest {
    private fun iterator() = WordStepIterator(WordBreakIterator.getForSpacingAndPunctuations(
        SpacingAndPunctuations(ApplicationProvider.getApplicationContext<App>().resources, false, Locale.ROOT)))

    // negative steps grow a selection back from the cursor, as delete and enter swipe do
    @Test fun selectsWholeWordsBackwards() {
        val it = iterator()
        assertEquals(-3, stepsToCharCount("hello yes", -1, it))
        assertEquals(-9, stepsToCharCount("hello yes", -2, it))
        assertEquals(-9, stepsToCharCount("hello yes", -3, it))
        assertEquals(-4, stepsToCharCount("hello yes ", -1, it))
        assertEquals(-10, stepsToCharCount("hello yes ", -2, it))
        assertEquals(-1, stepsToCharCount("a   b", -1, it))
        assertEquals(-5, stepsToCharCount("a   b", -2, it))
        assertEquals(-2, stepsToCharCount("  ", -1, it))
        assertEquals(-4, stepsToCharCount("word\u00A0word", -1, it))
        assertEquals(-9, stepsToCharCount("word\u00A0word", -2, it))
    }

    @Test fun punctuationGoesWithTheWordBefore() {
        val it = iterator()
        assertEquals(-5, stepsToCharCount("hello, world", -1, it))
        assertEquals(-12, stepsToCharCount("hello, world", -2, it))
        assertEquals(-6, stepsToCharCount("hello.", -1, it))
        assertEquals(-1, stepsToCharCount("a (b", -1, it))
        assertEquals(-4, stepsToCharCount("a (b", -2, it))
    }

    // the ICU iterator for languages without spaces answers 0 when asked past the end
    @Test fun staysAtTheEndPastTheLastStep() {
        val it = WordStepIterator(CompatBreakIterator.getWordInstance(Locale.JAPANESE))
        assertEquals(5, stepsToCharCount("今日は晴れ", 5, it))
        assertEquals(5, stepsToCharCount("今日は晴れ", 8, it))
        assertEquals(0, stepsToCharCount("", 2, it))
        assertEquals(0, stepsToCharCount("", -2, it))
        assertEquals(0, stepsToCharCount("", 2, iterator()))
        assertEquals(0, stepsToCharCount("", -2, iterator()))
    }

    // positive steps shrink the selection again, landing on the same word starts
    @Test fun shrinksToWordStarts() {
        val it = iterator()
        assertEquals(6, stepsToCharCount("hello yes", 1, it))
        assertEquals(9, stepsToCharCount("hello yes", 2, it))
        assertEquals(7, stepsToCharCount("hello, world", 1, it))
    }
}
