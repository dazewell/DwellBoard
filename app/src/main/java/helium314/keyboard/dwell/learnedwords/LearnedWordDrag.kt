// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learnedwords

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private class WordDrag(val anchor: Int, val baseline: Set<WordKey>, y: Float) {
    var y by mutableFloatStateOf(y)
    var end = anchor
    var hasMoved = false
}

@Composable
internal fun wordRangeDrag(
    list: LazyListState,
    rows: List<WordKey>,
    selected: Set<WordKey>,
    onSelectionChange: (Set<WordKey>) -> Unit,
): Modifier {
    val menuWidth = with(LocalDensity.current) { 52.dp.toPx() }
    val edgeSize = with(LocalDensity.current) { 64.dp.toPx() }
    val maxSpeed = with(LocalDensity.current) { 1000.dp.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptic = LocalHapticFeedback.current
    val liveSelection by rememberUpdatedState(selected)
    val updateSelection by rememberUpdatedState(onSelectionChange)
    val rowIndices = remember(rows) { rows.mapIndexed { index, word -> word.listKey to index }.toMap() }
    var drag by remember(rowIndices) { mutableStateOf<WordDrag?>(null) }

    fun updateRange(gesture: WordDrag, scrollDirection: Float = 0f) {
        val layout = list.layoutInfo
        val y = gesture.y.coerceIn(layout.viewportStartOffset.toFloat(), maxOf(layout.viewportStartOffset, layout.viewportEndOffset - 1).toFloat())
        val end = layout.wordIndexAt(y, rowIndices)
            ?: layout.edgeWordIndex(scrollDirection, rowIndices) ?: return
        if (end != gesture.end) {
            gesture.end = end
            updateSelection(selectWordRange(gesture.baseline, rows, gesture.anchor, end))
        }
    }

    val active = drag
    LaunchedEffect(active) {
        if (active == null) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val layout = list.layoutInfo
            val edge = minOf(edgeSize, (layout.viewportEndOffset - layout.viewportStartOffset) / 3f)
            if (active.hasMoved && edge > 0f) {
                val top = layout.viewportStartOffset + edge
                val bottom = layout.viewportEndOffset - edge
                val strength = when {
                    active.y < top -> (active.y - top) / edge
                    active.y > bottom -> (active.y - bottom) / edge
                    else -> 0f
                }.coerceIn(-1f, 1f)
                if (strength != 0f) {
                    list.scrollBy(strength * maxSpeed * (frame - lastFrame).coerceAtMost(32_000_000L) / 1_000_000_000f)
                    updateRange(active, strength)
                }
            }
            lastFrame = frame
        }
    }

    return Modifier.pointerInput(rowIndices, menuWidth, rtl, haptic) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Word actions have their own hit area and are excluded from the range gesture.
            val inMenu = if (rtl) down.position.x < menuWidth else down.position.x > size.width - menuWidth
            if (inMenu) return@awaitEachGesture
            val anchor = list.layoutInfo.wordIndexAt(down.position.y, rowIndices) ?: return@awaitEachGesture
            val pressed = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            val gesture = WordDrag(anchor, liveSelection, pressed.position.y)
            drag = gesture
            updateSelection(gesture.baseline + rows[anchor])
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            try {
                while (true) {
                    // Consume the release before the child toggle's tap detector can see it.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    gesture.hasMoved = gesture.hasMoved || change.position.y != pressed.position.y
                    gesture.y = change.position.y
                    updateRange(gesture)
                }
            } finally {
                drag = null
            }
        }
    }
}

private fun LazyListLayoutInfo.wordIndexAt(y: Float, indices: Map<String, Int>): Int? {
    // Pinned headings can overlap an underlying row in visibleItemsInfo.
    val item = visibleItemsInfo.firstOrNull { it.isLanguageHeading() && y >= it.offset && y < it.offset + it.size }
        ?: visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }
    val key = item?.key as? String ?: return null
    return indices[key]
}

private fun LazyListLayoutInfo.edgeWordIndex(direction: Float, indices: Map<String, Int>): Int? {
    if (direction == 0f) return null
    val firstUncoveredPixel = visibleItemsInfo.firstOrNull { it.isLanguageHeading() && it.offset <= viewportStartOffset }
        ?.let { it.offset + it.size } ?: viewportStartOffset
    val rows = if (direction < 0f) visibleItemsInfo else visibleItemsInfo.asReversed()
    return rows.firstNotNullOfOrNull { item ->
        val key = item.key as? String ?: return@firstNotNullOfOrNull null
        if (item.offset + item.size > firstUncoveredPixel && item.offset < viewportEndOffset) indices[key] else null
    }
}

private fun androidx.compose.foundation.lazy.LazyListItemInfo.isLanguageHeading() =
    (key as? String)?.startsWith("language:") == true
