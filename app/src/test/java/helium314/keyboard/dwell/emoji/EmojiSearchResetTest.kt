// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmojiSearchResetTest {
    @Test fun abcAndEmojiKeyLeaveTheEmojiKeyboard() {
        assertTrue(EmojiSearchReset.leavesEmojiKeyboard(KeyCode.ALPHA))
        assertTrue(EmojiSearchReset.leavesEmojiKeyboard(KeyCode.EMOJI))
    }

    @Test fun otherKeysDoNot() {
        assertFalse(EmojiSearchReset.leavesEmojiKeyboard(KeyCode.EMOJI_SEARCH))
        assertFalse(EmojiSearchReset.leavesEmojiKeyboard(KeyCode.DELETE))
        assertFalse(EmojiSearchReset.leavesEmojiKeyboard('a'.code))
    }
}
