// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class EmojiNamesTest {
    @Test fun plainEmojiAlsoTriesWithVariationSelector() {
        assertEquals(listOf("\u263A", "\u263A\uFE0F"), EmojiNames.candidates("\u263A", "\u263A"))
    }

    @Test fun qualifiedEmojiAlsoTriesWithoutVariationSelector() {
        assertEquals(listOf("\u2764\uFE0F", "\u2764"), EmojiNames.candidates("\u2764\uFE0F", "\u2764\uFE0F"))
    }

    @Test fun tonedEmojiFallsBackToBase() {
        val toned = "\uD83D\uDC4D\uD83C\uDFFD" // thumbs up, medium skin tone
        assertEquals(listOf(toned, "\uD83D\uDC4D"), EmojiNames.candidates(toned, toned))
    }

    @Test fun neutralVersionComesFirst() {
        val toned = "\uD83D\uDC4D\uD83C\uDFFD"
        assertEquals(listOf("\uD83D\uDC4D", toned), EmojiNames.candidates(toned, "\uD83D\uDC4D"))
    }

    @Test fun currentLanguageFirstThenSubtypeOrder() {
        val (en, ru, he) = listOf(Locale.ENGLISH, Locale.forLanguageTag("ru"), Locale.forLanguageTag("he"))
        assertEquals(listOf(he, en, ru), EmojiNames.fallbackLocales(listOf(en, ru, he, en), he, null))
    }

    @Test fun languageTheCallerHasOpenIsLeftOut() {
        val (en, ru) = listOf(Locale.ENGLISH, Locale.forLanguageTag("ru"))
        assertEquals(listOf(ru), EmojiNames.fallbackLocales(listOf(en, ru), en, en))
    }

    @Test fun multiCodePointWithoutSelectorIsNotPadded() {
        val flag = "\uD83C\uDDFA\uD83C\uDDF8"
        assertEquals(listOf(flag), EmojiNames.candidates(flag, flag))
    }
}
