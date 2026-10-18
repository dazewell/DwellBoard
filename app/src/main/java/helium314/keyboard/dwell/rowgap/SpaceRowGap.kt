// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.rowgap

import android.content.SharedPreferences
import helium314.keyboard.keyboard.Key.KeyParams
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings
import helium314.keyboard.latin.settings.findIndexOfDefaultSetting

/**
 * Extra empty space between the last letter row and the row holding the space bar.
 *
 * The keyboard keeps its height and the space row keeps its own, so it still matches the emoji
 * and clipboard bottom row: the rows above shrink to make room. Each half of the gap belongs to
 * the hit box of the key next to it but not to its drawn rect. The nearest-key grid in
 * ProximityInfo only lists keys within about one key width of their drawn rect, so each side's
 * share is capped in px ([overhangLimit]); past that the slider stops growing the gap rather
 * than leave cells above the space bar where taps are dropped.
 */
object SpaceRowGap {
    const val MAX = 1f

    /** Stored value in units of one average row height; absent, NaN or out of range falls back into 0..[MAX]. */
    fun clamp(stored: Float) = if (stored.isNaN()) 0f else stored.coerceIn(0f, MAX)

    /** Per orientation and fold state, keyed like the other keyboard size settings. */
    @JvmStatic
    fun read(prefs: SharedPreferences, landscape: Boolean, folded: Boolean): Float {
        val index = findIndexOfDefaultSetting(landscape, folded)
        val key = createPrefKeyForBooleanSettings(Settings.PREF_DWELL_SPACE_ROW_GAP_PREFIX, index, 2)
        val default = Defaults.PREF_DWELL_SPACE_ROW_GAP[index]
        return clamp(runCatching { prefs.getFloat(key, default) }.getOrDefault(default))
    }

    /**
     * Relative gap height for [gap] rows, given the summed heights of all rows ([total]) and of the
     * rows above the space row ([upper]). Capped at half of [upper] so those rows keep half their height.
     */
    fun gapHeight(gap: Float, total: Float, upper: Float, rowCount: Int) =
        minOf(clamp(gap) * total / rowCount, upper / 2)

    /**
     * Largest overhang in px a hit box may have past its drawn rect and still be listed for every
     * grid cell it covers: ProximityInfo keeps a key for a cell only if the cell centre is within
     * 1.2 key slot widths of the drawn rect, and a touch can be half a cell away from that centre.
     */
    fun overhangLimit(keySlotWidth: Float, cellWidth: Int, cellHeight: Int) =
        0.9f * 1.2f * keySlotWidth - cellWidth / 2f - cellHeight / 2f

    /**
     * Splits [gap] px into the part below the letter row and the part above the space row. The letter
     * row's side already overhangs by the normal [verticalGap]; the space row's side takes the rest.
     */
    fun split(gap: Float, verticalGap: Float, limit: Float): Pair<Float, Float> {
        val below = minOf(gap / 2, limit - verticalGap).coerceAtLeast(0f)
        val above = minOf(gap - below, limit).coerceAtLeast(0f)
        return below to above
    }

    @JvmStatic
    fun apply(rows: List<List<KeyParams>>, storedGap: Float, params: KeyboardParams) {
        if (clamp(storedGap) == 0f) return
        val nonEmpty = rows.filter { it.isNotEmpty() }
        if (nonEmpty.size < 2) return
        // only when the bottom row really is the space row; a layout without functional keys has none
        val spaceRow = nonEmpty.last()
        if (spaceRow.none { it.mCode == Constants.CODE_SPACE }) return
        if (nonEmpty.any { row -> row.any { it.mHeight <= 0f } }) return
        if (params.mBaseHeight <= 0 || params.GRID_WIDTH <= 0 || params.GRID_HEIGHT <= 0) return
        val upperRows = nonEmpty.subList(0, nonEmpty.size - 1)
        val upper = upperRows.sumOf { it.first().mHeight.toDouble() }.toFloat()
        val total = upper + spaceRow.first().mHeight

        // KeyboardBuilder.addSplit narrows keys later; the limit has to use the narrowed width
        var keySlotWidth = params.mDefaultKeyWidth * params.mBaseWidth
        if (params.mId.isSplitLayout && params.mId.element.isAlphaOrSymbol)
            keySlotWidth /= 1f + Settings.getValues().mSplitKeyboardSpacerRelativeWidth
        val cellWidth = (params.mOccupiedWidth + params.GRID_WIDTH - 1) / params.GRID_WIDTH
        val cellHeight = (params.mOccupiedHeight + params.GRID_HEIGHT - 1) / params.GRID_HEIGHT
        val limit = overhangLimit(keySlotWidth, cellWidth, cellHeight)
        val gapPx = gapHeight(storedGap, total, upper, nonEmpty.size) * params.mBaseHeight
        val (belowPx, abovePx) = split(gapPx, params.mVerticalGap.toFloat(), limit)
        val below = belowPx / params.mBaseHeight
        val above = abovePx / params.mBaseHeight
        if (below + above <= 0f) return

        val scale = 1f - (below + above) / upper
        upperRows.forEach { row -> row.forEach { it.mHeight *= scale } }
        upperRows.last().forEach {
            it.mHeight += below
            it.mDwellGapBelow = below
        }
        spaceRow.forEach {
            it.mHeight += above
            it.mDwellGapAbove = above
        }
    }
}
