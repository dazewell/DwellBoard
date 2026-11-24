// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.punctuation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PunctuationAutospaceTest {
    @Test fun immediatePunctuation() {
        ",;:!?".forEach { assertTrue(PunctuationAutospace.isImmediate(it.code)) }
        ".)\"$-a".forEach { assertFalse(PunctuationAutospace.isImmediate(it.code)) }
    }

    @Test fun immediateAfterNumber() {
        ";!?".forEach { assertTrue(PunctuationAutospace.isImmediateAfterNumber(it.code, '5'.code)) }
        ",:.".forEach { assertFalse(PunctuationAutospace.isImmediateAfterNumber(it.code, '5'.code)) }
        assertFalse(PunctuationAutospace.isImmediateAfterNumber('!'.code, 'a'.code))
        assertFalse(PunctuationAutospace.isImmediateAfterNumber('!'.code, ' '.code))
    }

    @Test fun separatorDash() {
        assertTrue(PunctuationAutospace.isSeparatorDash('-'.code, "e "))
        assertTrue(PunctuationAutospace.isSeparatorDash('—'.code, "e "))
        assertTrue(PunctuationAutospace.isSeparatorDash('-'.code, "2 ")) // "I got 2 - 3"
        assertFalse(PunctuationAutospace.isSeparatorDash('-'.code, "2-")) // "2-3"
        listOf(") ", "] ", "\" ", "' ", "” ", "! ", ". ").forEach { assertTrue(PunctuationAutospace.isSeparatorDash('-'.code, it), it) }
        listOf("( ", "[ ", "+ ", "* ", "/ ", "< ", ", ", "; ", ": ").forEach { assertFalse(PunctuationAutospace.isSeparatorDash('-'.code, it), it) } // minus sign
        // Indic vowel sign, emoji, emoji with variation selector / skin tone / keycap, units after a number
        listOf("ते ", "e 👍 ", "❤️ ", "👍🏽 ", "1️⃣ ", "50% ", "20° ", "5€ ", "t… ", "a ",
            "🏴󠁧󠁢󠁥󠁮󠁧󠁿 ", // England flag, ends in a tag
            "𝟚€ ") // digit outside the BMP
            .forEach { assertTrue(PunctuationAutospace.isSeparatorDash('-'.code, it), it) }
        listOf("^ ", "$ ", "a€ ", "- ", "  ", "a\n ", "\uDC4D ")
            .forEach { assertFalse(PunctuationAutospace.isSeparatorDash('-'.code, it), it) }
        assertFalse(PunctuationAutospace.isSeparatorDash('-'.code, "= ")) // minus sign
        assertFalse(PunctuationAutospace.isSeparatorDash('-'.code, "ll")) // hyphenated word
        assertFalse(PunctuationAutospace.isSeparatorDash('-'.code, " "))
        assertFalse(PunctuationAutospace.isSeparatorDash('-'.code, null))
        assertFalse(PunctuationAutospace.isSeparatorDash(','.code, "e "))
    }

    @Test fun removesAutoSpace() {
        assertTrue(PunctuationAutospace.removesAutoSpace('?'.code, "! "))
        assertTrue(PunctuationAutospace.removesAutoSpace('.'.code, ", "))
        assertTrue(PunctuationAutospace.removesAutoSpace('/'.code, ": "))
        assertFalse(PunctuationAutospace.removesAutoSpace('/'.code, ", "))
        assertFalse(PunctuationAutospace.removesAutoSpace('a'.code, ", "))
        assertFalse(PunctuationAutospace.removesAutoSpace('"'.code, ", "))
    }

    @Test fun wordThenSpace() {
        assertTrue(PunctuationAutospace.isWordThenSpace("d "))
        assertTrue(PunctuationAutospace.isWordThenSpace("5 "))
        assertFalse(PunctuationAutospace.isWordThenSpace("  "))
        assertFalse(PunctuationAutospace.isWordThenSpace(", "))
        assertFalse(PunctuationAutospace.isWordThenSpace("= "))
        assertFalse(PunctuationAutospace.isWordThenSpace("d,"))
        assertFalse(PunctuationAutospace.isWordThenSpace(" "))
        assertFalse(PunctuationAutospace.isWordThenSpace(null))
    }

    @Test fun startsWithLetter() {
        assertTrue(PunctuationAutospace.startsWithLetter("l"))
        assertFalse(PunctuationAutospace.startsWithLetter("5"))
        assertFalse(PunctuationAutospace.startsWithLetter(" "))
        assertFalse(PunctuationAutospace.startsWithLetter(""))
        assertFalse(PunctuationAutospace.startsWithLetter(null))
    }

    @Test fun email() {
        assertTrue(PunctuationAutospace.looksLikeEmail("mail@example.com,"))
        assertFalse(PunctuationAutospace.looksLikeEmail("@bob,"))
        assertFalse(PunctuationAutospace.looksLikeEmail("hello,"))
    }
}
