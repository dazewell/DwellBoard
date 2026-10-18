// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.punctuation

import helium314.keyboard.latin.settings.Defaults
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SymbolsSwitchBackTest {
    private fun cps(s: String) = s.codePoints().sorted().toArray()

    @Test fun absentOrEmptyMeansNothing() {
        assertContentEquals(IntArray(0), SymbolsSwitchBack.parse(null))
        assertContentEquals(IntArray(0), SymbolsSwitchBack.parse(""))
        assertContentEquals(IntArray(0), SymbolsSwitchBack.parse(" \t\n"))
    }

    @Test fun dropsWhitespaceDigitsAndDuplicates() =
        assertContentEquals(cps("?-"), SymbolsSwitchBack.parse("? 1 ?? -9 "))

    @Test fun keepsSupplementaryCodePoints() {
        val chars = SymbolsSwitchBack.parse("?😀")
        assertTrue(SymbolsSwitchBack.matches(chars, 0x1F600))
        assertFalse(SymbolsSwitchBack.matches(chars, 0xD83D))
    }

    @Test fun defaultSet() {
        val chars = SymbolsSwitchBack.parse(Defaults.PREF_ABC_AFTER_SYMBOL_CHARS_LIST)
        for (c in "?!;-\"')") assertTrue(SymbolsSwitchBack.matches(chars, c.code), "$c")
        for (c in ",.(:@#/1") assertFalse(SymbolsSwitchBack.matches(chars, c.code), "$c")
    }

    @Test fun emptySetAndFunctionalCodesNeverMatch() {
        assertFalse(SymbolsSwitchBack.matches(IntArray(0), '?'.code))
        assertFalse(SymbolsSwitchBack.matches(SymbolsSwitchBack.parse("?"), -902))
    }
}
