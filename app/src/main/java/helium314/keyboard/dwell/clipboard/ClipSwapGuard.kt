// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.clipboard

import android.content.ClipData
import android.text.TextUtils

/**
 * Recognises the clip-changed notifications caused by the keyboard's own paste fallback, which swaps the
 * system clip and restores it about 500 ms later. Those must not refresh the clipboard suggestion, but a
 * clip the user copies in the meantime must. Matching on content keeps the two apart; the time limit stops
 * an expected notification that never arrives from hiding a later copy of the same content.
 *
 * Main thread only: both the swap and the listener run there.
 */
class ClipSwapGuard {
    private var items: List<ClipData.Item> = emptyList()
    private var until = 0L

    fun begin(now: Long, vararg swapClips: ClipData?) {
        items = swapClips.mapNotNull { firstItem(it) }
        until = now + WINDOW_MILLIS
    }

    fun isSwap(now: Long, current: ClipData?): Boolean {
        if (now >= until) return false
        val item = firstItem(current) ?: return false
        return items.any { it.uri == item.uri && TextUtils.equals(it.text, item.text) }
    }

    // getItemAt throws on an empty clip
    private fun firstItem(clip: ClipData?) = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)

    companion object {
        const val WINDOW_MILLIS = 1500L
    }
}
