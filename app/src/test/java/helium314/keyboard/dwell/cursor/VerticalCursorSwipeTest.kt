// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.cursor

import android.text.InputType
import android.view.inputmethod.EditorInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerticalCursorSwipeTest {
    private val multiLine = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE

    @Test fun multiLineWithNewlineEnterAllows() {
        assertTrue(VerticalCursorSwipe.isMultiLine(multiLine, EditorInfo.IME_ACTION_NONE))
    }
    @Test fun multiLineWithEnterActionBlocks() {
        assertFalse(VerticalCursorSwipe.isMultiLine(multiLine, EditorInfo.IME_ACTION_DONE))
        assertFalse(VerticalCursorSwipe.isMultiLine(multiLine, EditorInfo.IME_ACTION_UNSPECIFIED))
    }
    @Test fun singleLineBlocks() {
        assertFalse(VerticalCursorSwipe.isMultiLine(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_NONE))
    }
    @Test fun missingEditorInfoBlocks() {
        assertFalse(VerticalCursorSwipe.isMultiLine(multiLine, null))
    }
}
