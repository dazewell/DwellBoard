// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EmojiSearchResetTest {
    private var clears = 0
    private fun start(enabled: Boolean = true) = EmojiSearchReset.onEmojiKeyboardStarted(enabled) { clears++ }

    @BeforeTest fun reset() {
        EmojiSearchReset.reset()
        clears = 0
    }

    @Test fun freshVisitClears() {
        start()
        assertEquals(1, clears)
    }

    @Test fun returnFromSearchKeepsTextOnce() {
        EmojiSearchReset.onReturnPosted()
        start()
        assertEquals(0, clears)
        start() // the next visit is fresh again
        assertEquals(1, clears)
    }

    @Test fun disabledNeverClears() {
        start(enabled = false)
        assertEquals(0, clears)
    }

    @Test fun disabledStillConsumesReturn() {
        EmojiSearchReset.onReturnPosted()
        start(enabled = false)
        start()
        assertEquals(1, clears)
    }
}
