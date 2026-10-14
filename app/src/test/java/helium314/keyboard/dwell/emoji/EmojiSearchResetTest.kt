// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmojiSearchResetTest {
    @Test fun abcAndEmojiKeyByHandClear() {
        assertTrue(EmojiSearchReset.clearsOn(KeyCode.ALPHA, automaticAlpha = false))
        assertTrue(EmojiSearchReset.clearsOn(KeyCode.EMOJI, automaticAlpha = false))
    }

    @Test fun abcSentAfterAnEmojiPickDoesNot() {
        assertFalse(EmojiSearchReset.clearsOn(KeyCode.ALPHA, automaticAlpha = true))
    }

    @Test fun otherKeysDoNot() {
        assertFalse(EmojiSearchReset.clearsOn(KeyCode.EMOJI_SEARCH, automaticAlpha = false))
        assertFalse(EmojiSearchReset.clearsOn(KeyCode.DELETE, automaticAlpha = false))
        assertFalse(EmojiSearchReset.clearsOn('a'.code, automaticAlpha = false))
    }
}
