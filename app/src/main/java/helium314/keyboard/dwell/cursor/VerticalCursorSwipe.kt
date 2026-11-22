// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.cursor

import android.text.InputType
import android.view.inputmethod.EditorInfo
import helium314.keyboard.latin.utils.InputTypeUtils

/**
 * Decides whether a vertical space swipe may move the cursor up and down. Arrow up/down in a single-line field
 * jumps the cursor to an end or moves focus to another field, so a slightly vertical slide on space did that by
 * accident. Apps commonly make a field look single-line by setting the multi-line flag (so long text wraps) while
 * giving Enter an action such as Done (TextView adds IME_FLAG_NO_ENTER_ACTION to every multi-line field, and these
 * apps strip it), so the flag alone is not trusted: Enter must also type a line break, judged exactly as
 * InputLogic does. Wrapped text in a real multi-line field keeps the swipe even with no line break in it.
 */
object VerticalCursorSwipe {
    fun allows(inputType: Int, editorInfo: EditorInfo?): Boolean =
        // TYPE_NULL is a terminal or similar raw-key view: up/down there is shell history or app navigation, not a
        // cursor jump, so it keeps the swipe
        inputType == InputType.TYPE_NULL
            || isMultiLine(inputType, editorInfo?.let { InputTypeUtils.getImeOptionsActionIdFromEditorInfo(it) })

    fun isMultiLine(inputType: Int, imeActionId: Int?): Boolean =
        inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0 && imeActionId == EditorInfo.IME_ACTION_NONE
}
