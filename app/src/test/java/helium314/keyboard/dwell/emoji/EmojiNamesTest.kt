// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import kotlin.test.Test
import kotlin.test.assertEquals

class EmojiNamesTest {
    @Test fun plainEmojiAlsoTriesWithVariationSelector() {
        assertEquals(listOf("☺", "☺️"), EmojiNames.candidates("☺", "☺"))
    }

    @Test fun qualifiedEmojiAlsoTriesWithoutVariationSelector() {
        assertEquals(listOf("❤️", "❤"), EmojiNames.candidates("❤️", "❤️"))
    }

    @Test fun tonedEmojiFallsBackToBase() {
        val toned = "👍🏽" // thumbs up, medium skin tone
        assertEquals(listOf(toned, "👍"), EmojiNames.candidates(toned, toned))
    }

    @Test fun neutralVersionComesFirst() {
        val toned = "👍🏽"
        assertEquals(listOf("👍", toned), EmojiNames.candidates(toned, "👍"))
    }

    @Test fun multiCodePointWithoutSelectorIsNotPadded() {
        val flag = "🇺🇸"
        assertEquals(listOf(flag), EmojiNames.candidates(flag, flag))
    }
}
