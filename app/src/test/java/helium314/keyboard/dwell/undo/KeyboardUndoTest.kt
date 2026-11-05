// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.undo

import android.text.Selection
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KeyboardUndoTest {
    private val history = KeyboardUndo()
    private val ic = object : BaseInputConnection(View(RuntimeEnvironment.getApplication()), true) {
        var reads = 0
        var rejectSelection = false
        var rejectSelectionAfter = 0
        var invalidateDuringRead = false
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int) = ExtractedText().apply {
            selectionStart = Selection.getSelectionStart(editable)
            selectionEnd = Selection.getSelectionEnd(editable)
        }
        override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence? {
            reads++
            if (invalidateDuringRead) {
                invalidateDuringRead = false
                val worker = Thread { history.reload() }
                worker.start()
                worker.join(1000)
                assertFalse(worker.isAlive)
            }
            return super.getTextBeforeCursor(length, flags)
        }
        override fun getSelectedText(flags: Int): CharSequence? {
            reads++
            return super.getSelectedText(flags)
        }
        override fun setSelection(start: Int, end: Int): Boolean {
            if (rejectSelectionAfter > 0 && --rejectSelectionAfter == 0) return false
            return !rejectSelection && super.setSelection(start, end)
        }
    }
    private val text get() = ic.editable!!.toString()
    private val start get() = Selection.getSelectionStart(ic.editable)
    private val end get() = Selection.getSelectionEnd(ic.editable)
    private val composing: String get() {
        val from = BaseInputConnection.getComposingSpanStart(ic.editable)
        return if (from < 0) "" else text.substring(from, start)
    }
    private val committed get() = text.substring(0, start - composing.length)

    private fun begin(allowed: Boolean = true) = history.begin(ic, committed, composing, start, end, allowed)
    private fun finish() = history.end(ic, committed, composing, start, end)
    private fun setText(value: String, cursor: Int = value.length) {
        ic.finishComposingText()
        ic.editable!!.replace(0, ic.editable!!.length, value)
        Selection.setSelection(ic.editable, cursor)
        history.reset()
    }
    private fun type(word: String) {
        begin()
        history.composing(composing, word)
        history.replacing(start - composing.length, end, 1)
        ic.setComposingText(word, 1)
        finish()
    }
    private fun commit(value: String) {
        begin()
        history.beforeCommit(ic, committed, composing, value, start, end)
        history.replacing(start - composing.length, end, 1)
        ic.commitText(value, 1)
        finish()
    }
    private fun replay(redo: Boolean = false): KeyboardUndo.Snapshot? {
        begin()
        val result = history.replay(ic, redo)
        finish()
        return result
    }

    @Test fun correctionIsSeparateFromTypingAndRoundTrips() {
        setText("")
        type("t"); type("te"); type("teh")
        begin()
        history.beforeCommit(ic, committed, composing, "the", start, end)
        history.replacing(0, end, 1)
        ic.commitText("the", 1)
        history.replacing(start, end, 1)
        ic.commitText(" ", 1)
        finish()
        assertEquals("the ", text)
        assertNotNull(replay())
        assertEquals("teh", text)
        assertNotNull(replay(true))
        assertEquals("the ", text)
        assertNotNull(replay())
        assertEquals("teh", text)
        assertNotNull(replay())
        assertEquals("", text)
        assertNull(replay())
    }

    @Test fun suggestionRestoresTypedText() {
        setText("prefix ")
        type("w"); type("wo")
        commit("word")
        assertNotNull(replay())
        assertEquals("prefix wo", text)
        assertNotNull(replay(true))
        assertEquals("prefix word", text)
    }

    @Test fun pasteCutAndSelectionReplacementRestoreSelection() {
        for (replacement in listOf("", "new", "longer replacement")) {
            setText("before original after")
            Selection.setSelection(ic.editable, 7, 15)
            commit(replacement)
            assertEquals("before $replacement after", text)
            assertNotNull(replay())
            assertEquals("before original after", text)
            assertEquals(7, start)
            assertEquals(15, end)
            assertNotNull(replay(true))
            assertEquals("before $replacement after", text)
        }
    }

    @Test fun deletionAndNewEditClearRedo() {
        setText("hello world")
        begin()
        history.replacing(start - 5, end, 1)
        ic.deleteSurroundingText(5, 0)
        finish()
        assertNotNull(replay())
        assertEquals("hello world", text)
        commit("!")
        assertNull(replay(true))
        assertEquals("hello world!", text)
    }

    @Test fun normalTypingUsesNoEditorReadsAndDisabledModeRetainsNothing() {
        setText("")
        type("h"); type("he"); type("hel")
        assertEquals(0, ic.reads)
        begin(false)
        history.replacing(start, end, 1)
        ic.commitText("!", 1)
        finish()
        assertNull(replay())
    }

    @Test fun changedTextAndCursorFailValidationWithoutEditing() {
        setText("before ")
        commit("paste")
        ic.editable!!.replace(0, 1, "B")
        assertNull(replay())
        assertEquals("Before paste", text)
        setText("before ")
        commit("paste")
        Selection.setSelection(ic.editable, 0)
        assertNull(replay())
        assertEquals("before paste", text)
    }

    @Test fun invalidationAndSessionResetDropBothStacks() {
        setText("before ")
        commit("paste")
        history.invalidate()
        assertNull(replay())
        commit("again")
        assertNotNull(replay())
        history.reset()
        assertNull(replay(true))
    }

    @Test fun shiftedCacheOriginKeepsAbsolutePositions() {
        setText("x".repeat(1500))
        commit("paste")
        assertNotNull(replay())
        assertEquals("x".repeat(1500), text)
        assertNotNull(replay(true))
        assertEquals("x".repeat(1500) + "paste", text)
    }

    @Test fun uncapturedDeletionAndOversizedPasteInvalidateHistory() {
        setText("x".repeat(1500))
        begin()
        history.replacing(0, end, 1)
        ic.deleteSurroundingText(1500, 0)
        finish()
        assertNull(replay())
        assertEquals("", text)
        commit("x".repeat(1500))
        assertNull(replay())
        assertEquals(1500, text.length)
    }

    @Test fun composingMiddleCannotRecordAnIncompleteWord() {
        setText("before word after", 9)
        history.composingRegion(7, 11, 9)
        ic.setComposingRegion(7, 11)
        begin()
        history.replacing(7, 11, 1)
        ic.setComposingText("replacement", 1)
        finish()
        assertNull(replay())
    }

    @Test fun failedEditorRequestDropsHistory() {
        setText("before ")
        commit("paste")
        ic.rejectSelection = true
        assertNull(replay())
        assertTrue(history.replayAttempted)
        ic.rejectSelection = false
        assertNull(replay())
        assertEquals("before paste", text)
    }

    @Test fun failedFinalSelectionReportsAnAttemptAfterTextChanged() {
        setText("")
        type("word")
        ic.rejectSelectionAfter = 2
        assertNull(replay())
        assertTrue(history.replayAttempted)
        assertEquals("", text)
        assertNull(replay(true))
    }

    @Test fun workerReloadDuringValidationCancelsReplayBeforeMutation() {
        setText("before ")
        commit("paste")
        ic.invalidateDuringRead = true
        assertNull(replay())
        assertFalse(history.replayAttempted)
        assertEquals("before paste", text)
        assertNull(replay())
    }

    @Test fun finishingMiddleWordCompositionAllowsANewEditChain() {
        setText("word", 2)
        history.composingRegion(0, 4, 2)
        history.finishComposition()
        ic.finishComposingText()
        Selection.setSelection(ic.editable, 4)
        commit("!")
        assertNotNull(replay())
        assertEquals("word", text)
    }

    @Test fun selectedDeletionPreservesOnlyItsInternalCollapseReload() {
        setText("before original after")
        Selection.setSelection(ic.editable, 7, 15)
        begin()
        history.selection(15, 15, 7, 15)
        ic.setSelection(15, 15)
        history.reload()
        history.replacing(7, 15, 1)
        ic.deleteSurroundingText(8, 0)
        finish()
        assertNotNull(replay())
        assertEquals("before original after", text)
        assertEquals(7, start)
        assertEquals(15, end)
    }

    @Test fun boundedHistoryStopsWhenOldEntriesExpire() {
        setText("")
        repeat(90) { commit(".") }
        var count = 0
        while (replay() != null) count++
        assertEquals(64, count)
        assertEquals(".".repeat(26), text)
    }

    @Test fun supplementaryCharacterNeverSplitsASurrogatePair() {
        setText("prefix ")
        commit("\uD83D\uDE00")
        assertNotNull(replay())
        assertEquals("prefix ", text)
        assertNotNull(replay(true))
        assertEquals("prefix \uD83D\uDE00", text)
    }
}
