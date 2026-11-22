// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.cursor

import android.text.InputType
import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.latin.common.Constants

/**
 * Decides whether a vertical space swipe may move the cursor up and down. Arrow up/down in a single-line field
 * jumps the cursor to an end or moves focus to another field, so a slightly vertical slide on space did that by
 * accident. Some apps flag a single-line field as multi-line, so the flag alone is not trusted: the text also
 * has to contain a line break near the cursor.
 */
object VerticalCursorSwipe {
    // how far either side of the cursor to look for a line break; RichInputConnection's cache size, so the text
    // before the cursor usually comes from the cache and only the text after it is read from the editor
    private const val LOOKAROUND = Constants.EDITOR_CONTENTS_CACHE_SIZE

    // the editor is read once per swipe, not on every move event; main thread only
    private var lastSwipeStart = Long.MIN_VALUE
    private var lastAllowed = true

    fun allows(swipeStart: Long, inputType: Int, connection: RichInputConnection): Boolean {
        if (swipeStart == lastSwipeStart) return lastAllowed
        lastSwipeStart = swipeStart
        // TYPE_NULL is a terminal or similar raw-key view: up/down there is shell history or app navigation, not a
        // cursor jump, and such views report no text, so they keep the swipe
        lastAllowed = inputType == InputType.TYPE_NULL || inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
            && hasLineBreak(connection.getTextBeforeCursor(LOOKAROUND, 0), connection.getTextAfterCursor(LOOKAROUND, 0))
        return lastAllowed
    }

    fun hasLineBreak(before: CharSequence?, after: CharSequence?): Boolean =
        before?.contains('\n') == true || after?.contains('\n') == true
}
