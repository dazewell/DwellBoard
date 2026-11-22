// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.cursor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerticalCursorSwipeTest {
    @Test fun lineBreakOnEitherSideAllows() {
        assertTrue(VerticalCursorSwipe.hasLineBreak("first\nsec", "ond"))
        assertTrue(VerticalCursorSwipe.hasLineBreak("first", " line\nsecond"))
    }
    @Test fun noLineBreakBlocks() {
        assertFalse(VerticalCursorSwipe.hasLineBreak("one long wrapped paragraph", " goes on"))
        assertFalse(VerticalCursorSwipe.hasLineBreak("", ""))
    }
    @Test fun unreadableTextBlocks() {
        assertFalse(VerticalCursorSwipe.hasLineBreak(null, null))
        assertTrue(VerticalCursorSwipe.hasLineBreak(null, "a\nb"))
    }
}
