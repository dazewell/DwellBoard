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

    @Test fun separatorDash() {
        assertTrue(PunctuationAutospace.isSeparatorDash('-'.code, "e "))
        assertTrue(PunctuationAutospace.isSeparatorDash('—'.code, "e "))
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

    @Test fun email() {
        assertTrue(PunctuationAutospace.looksLikeEmail("mail@example.com,"))
        assertFalse(PunctuationAutospace.looksLikeEmail("@bob,"))
        assertFalse(PunctuationAutospace.looksLikeEmail("hello,"))
    }
}
