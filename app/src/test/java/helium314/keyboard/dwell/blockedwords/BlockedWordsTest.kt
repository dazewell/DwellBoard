// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.blockedwords

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlockedWordsTest {
    @Test
    fun readsOnlyFilesNamedAsTheKeyboardNamesThem() {
        assertEquals(Locale.forLanguageTag("en-US"), localeOfFile("en-US.txt"))
        assertEquals(Locale.forLanguageTag("sr-Latn"), localeOfFile("sr-Latn.txt"))
        assertEquals(Locale.forLanguageTag("zz"), localeOfFile("zz.txt"))
        // these would collapse onto another locale's key, and the keyboard never reads them
        assertNull(localeOfFile("en_US.txt"))
        assertNull(localeOfFile("EN-us.txt"))
        assertNull(localeOfFile("not a tag.txt"))
        assertNull(localeOfFile("en-US.txt.tmp"))
        assertNull(localeOfFile("en-US"))
    }

    @Test
    fun skipsBlankAndRepeatedLines() {
        assertEquals(listOf("Apple", "apple", "pear"), blockedLines(listOf("Apple", "", "apple", "  ", "pear", "Apple")))
    }

    @Test
    fun keepsOrderAndCaseOfRemainingWords() {
        assertEquals(listOf("pear", "Apple"), remainingLines(listOf("pear", "apple", "", "Apple", "fig"), setOf("apple", "fig")))
        assertEquals(emptyList(), remainingLines(listOf("fig", ""), setOf("fig")))
    }
}
