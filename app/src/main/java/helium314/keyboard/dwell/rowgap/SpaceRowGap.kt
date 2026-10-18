// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.rowgap

import android.content.SharedPreferences
import helium314.keyboard.keyboard.Key.KeyParams
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
 * the hit box of the key next to it but not to its drawn rect. Halving matters: the nearest-key
 * grid in ProximityInfo only lists keys within about one key width of their drawn rect, so a
 * gap owned by one side alone could leave untappable cells at its far edge.
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

    @JvmStatic
    fun apply(rows: List<List<KeyParams>>, storedGap: Float) {
        if (clamp(storedGap) == 0f) return
        val nonEmpty = rows.filter { it.isNotEmpty() }
        if (nonEmpty.size < 2) return
        // only when the bottom row really is the space row; a layout without functional keys has none
        val spaceRow = nonEmpty.last()
        if (spaceRow.none { it.mCode == Constants.CODE_SPACE }) return
        if (nonEmpty.any { row -> row.any { it.mHeight <= 0f } }) return
        val upperRows = nonEmpty.subList(0, nonEmpty.size - 1)
        val upper = upperRows.sumOf { it.first().mHeight.toDouble() }.toFloat()
        val total = upper + spaceRow.first().mHeight
        val gapHeight = gapHeight(storedGap, total, upper, nonEmpty.size)
        val scale = 1f - gapHeight / upper
        upperRows.forEach { row -> row.forEach { it.mHeight *= scale } }
        val half = gapHeight / 2
        upperRows.last().forEach {
            it.mHeight += half
            it.mDwellGapBelow = half
        }
        spaceRow.forEach {
            it.mHeight += half
            it.mDwellGapAbove = half
        }
    }
}
