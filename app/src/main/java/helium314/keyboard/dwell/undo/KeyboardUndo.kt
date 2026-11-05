// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.undo

import android.text.TextUtils
import android.os.Looper
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/** Bounded, UI-thread-only history. Ordinary typing reads only the connection's existing cache. */
class KeyboardUndo {
    data class Snapshot(val origin: Int, val text: String, val start: Int, val end: Int) {
        val prefix: String get() = text.substring(0, start - origin)
    }

    private data class Edit(val before: Snapshot, val after: Snapshot, val changeStart: Int,
                            val typing: Boolean) {
        val bytes: Int get() = (before.text.length + after.text.length) * 2
    }

    private val undo = ArrayDeque<Edit>()
    private val redo = ArrayDeque<Edit>()
    private var before: Snapshot? = null
    private var enabled = false
    private var suppressed = false
    private var unsafeComposition = false
    private var touched = Int.MAX_VALUE
    private var typing = false
    private var bytes = 0
    private var preserveReload = false
    private val invalidatedByReader = AtomicBoolean()
    var replayAttempted = false
        private set

    fun reset() {
        invalidate()
        invalidatedByReader.set(false)
        unsafeComposition = false
        enabled = false
    }

    fun invalidate() {
        undo.clear()
        redo.clear()
        before = null
        bytes = 0
        preserveReload = false
    }

    fun begin(ic: InputConnection?, committed: CharSequence, composing: CharSequence,
              start: Int, end: Int, allowed: Boolean) {
        consumeReaderInvalidation()
        enabled = allowed
        suppressed = false
        touched = Int.MAX_VALUE
        typing = false
        if (!enabled || unsafeComposition) {
            invalidate()
            return
        }
        before = capture(ic, committed, composing, start, end)
        consumeReaderInvalidation()
        if (before == null) invalidate()
    }

    fun end(ic: InputConnection?, committed: CharSequence, composing: CharSequence,
            start: Int, end: Int) {
        consumeReaderInvalidation()
        if (!enabled || suppressed || touched == Int.MAX_VALUE) {
            before = null
            return
        }
        val initial = before ?: return
        val final = capture(ic, committed, composing, start, end)
        if (consumeReaderInvalidation()) return
        if (final == null) invalidate() else record(initial, final, touched, typing)
        before = null
    }

    /** Every changed range must be fully captured, including text removed earlier in this batch. */
    fun replacing(start: Int, end: Int, newCursor: Int) {
        if (!enabled || suppressed) return
        val initial = before
        if (initial == null || start < initial.origin || end < start || newCursor != 1
            || unsafeComposition) {
            invalidate()
            return
        }
        touched = minOf(touched, start)
    }

    fun composingRegion(start: Int, end: Int, cursor: Int) {
        unsafeComposition = end != cursor || start > end
        if (unsafeComposition) invalidate()
    }

    fun finishComposition() { unsafeComposition = false }

    fun selection(start: Int, end: Int, oldStart: Int, oldEnd: Int) {
        val initial = before
        // Selected deletion first collapses to its end, then deletes backwards in the same batch.
        preserveReload = enabled && !suppressed && initial != null && oldStart < oldEnd
            && start == end && end == oldEnd && initial.start == oldStart && initial.end == oldEnd
            && touched == Int.MAX_VALUE
        if (!preserveReload) invalidate()
    }

    fun reload() {
        // Suggestion workers can refresh the shared text cache. They must never mutate the stacks.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            invalidatedByReader.set(true)
            return
        }
        if (preserveReload) preserveReload = false else invalidate()
    }

    private fun consumeReaderInvalidation(): Boolean {
        if (!invalidatedByReader.getAndSet(false)) return false
        invalidate()
        return true
    }

    fun composing(old: CharSequence, text: CharSequence) {
        if (!enabled || suppressed) return
        val added = text.length - old.length
        typing = added in 1..2 && TextUtils.regionMatches(text, 0, old, 0, old.length)
            && Character.codePointCount(text, old.length, text.length) == 1
            && Character.isLetterOrDigit(Character.codePointAt(text, old.length))
    }

    fun beforeCommit(ic: InputConnection?, committed: CharSequence, composing: CharSequence,
                     text: CharSequence, start: Int, end: Int) {
        if (!enabled || suppressed || composing.isEmpty() || TextUtils.equals(composing, text)) return
        // A substitution is separate from any earlier typing in the same editor batch.
        val current = capture(ic, committed, composing, start, end) ?: run { invalidate(); return }
        if (consumeReaderInvalidation()) return
        val initial = before ?: return
        if (touched != Int.MAX_VALUE) record(initial, current, touched, typing)
        before = current
        touched = Int.MAX_VALUE
        typing = false
    }

    /** No native fallback: local replacements also enter the app's native history. */
    fun replay(ic: InputConnection?, isRedo: Boolean): Snapshot? {
        replayAttempted = false
        consumeReaderInvalidation()
        suppressed = true
        before = null
        if (!enabled || ic == null) return null
        val source = if (isRedo) redo else undo
        val destination = if (isRedo) undo else redo
        val edit = source.peekLast() ?: return null
        val expected = if (isRedo) edit.before else edit.after
        val target = if (isRedo) edit.after else edit.before
        if (!matchesEditor(ic, expected) || consumeReaderInvalidation()) {
            invalidate()
            return null
        }
        // Selection and replacement are queued in order; no read assumes they have landed yet.
        replayAttempted = true
        if (!ic.finishComposingText()
            || !ic.setSelection(edit.changeStart, expected.end)
            || !ic.commitText(target.text.substring(edit.changeStart - target.origin), 1)
            || !ic.setSelection(target.start, target.end)) {
            invalidate()
            return null
        }
        if (consumeReaderInvalidation()) return null
        source.removeLast()
        destination.addLast(edit)
        unsafeComposition = false
        return target
    }

    private fun matchesEditor(ic: InputConnection, expected: Snapshot): Boolean {
        val request = ExtractedTextRequest().apply { hintMaxChars = 1; hintMaxLines = 1 }
        val extracted = ic.getExtractedText(request, 0) ?: return false
        if (extracted.startOffset + extracted.selectionStart != expected.start
            || extracted.startOffset + extracted.selectionEnd != expected.end) return false
        val prefixLength = expected.start - expected.origin
        val prefix = ic.getTextBeforeCursor(prefixLength, 0) ?: return false
        if (!TextUtils.equals(prefix, expected.prefix)) return false
        if (expected.start != expected.end) {
            val selected = ic.getSelectedText(0) ?: return false
            if (!TextUtils.equals(selected, expected.text.substring(prefixLength))) return false
        }
        return true
    }

    private fun capture(ic: InputConnection?, committed: CharSequence, composing: CharSequence,
                        start: Int, end: Int): Snapshot? {
        if (ic == null || start < 0 || end < start || unsafeComposition) return null
        val selectedLength = end - start
        if (selectedLength > MAX_CHARS) return null
        val selected = if (selectedLength == 0) "" else ic.getSelectedText(0)?.toString() ?: return null
        if (selected.length != selectedLength || composing.length > MAX_CHARS) return null
        val capacity = MAX_CHARS - selectedLength
        val composingLength = minOf(composing.length, capacity)
        val committedLength = minOf(committed.length, capacity - composingLength)
        val prefixLength = composingLength + committedLength
        if (prefixLength > start) return null
        val text = buildString(prefixLength + selectedLength) {
            append(committed, committed.length - committedLength, committed.length)
            append(composing, composing.length - composingLength, composing.length)
            append(selected)
        }
        return Snapshot(start - prefixLength, text, start, end)
    }

    private fun record(initial: Snapshot, final: Snapshot, minTouched: Int, isTyping: Boolean) {
        val origin = maxOf(initial.origin, final.origin)
        if (minTouched < origin || origin > initial.end || origin > final.end) {
            invalidate()
            return
        }
        var position = origin
        while (position < minOf(initial.end, final.end)
            && initial.text[position - initial.origin] == final.text[position - final.origin]) position++
        if (position == initial.end && position == final.end) return
        // Keep surrogate pairs intact even when the common prefix ends inside one.
        if (position > origin && position < initial.end
            && Character.isLowSurrogate(initial.text[position - initial.origin])) position--
        if (position > origin && position < final.end
            && Character.isLowSurrogate(final.text[position - final.origin])) position--
        val previous = undo.peekLast()
        var edit = Edit(initial, final, position, isTyping)
        if (isTyping && previous?.typing == true && initial.start == initial.end
            && previous.after.end == initial.start && position == initial.start
            && previous.changeStart >= maxOf(previous.before.origin, final.origin)) {
            undo.removeLast()
            bytes -= previous.bytes
            edit = Edit(previous.before, final, previous.changeStart, true)
        }
        for (discarded in redo) bytes -= discarded.bytes
        redo.clear()
        undo.addLast(edit)
        bytes += edit.bytes
        while (undo.size > MAX_ENTRIES || bytes > MAX_BYTES) bytes -= undo.removeFirst().bytes
    }

    companion object {
        private const val MAX_CHARS = 1024
        private const val MAX_ENTRIES = 64
        private const val MAX_BYTES = 64 * 1024
    }
}
