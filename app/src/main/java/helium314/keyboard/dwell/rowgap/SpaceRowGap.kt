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
 * The keyboard keeps its height: every row shrinks a little to make room. The gap is added to
 * the letter row's height but not to its visible keys, so like the normal key gap, a touch in it
 * still hits the key above instead of falling into a dead zone.
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

    /** Factor applied to every row so the keyboard height stays the same once [gap] rows are taken out. */
    fun rowScale(rowCount: Int, gap: Float) = 1f - gap / rowCount

    @JvmStatic
    fun apply(rows: List<List<KeyParams>>, storedGap: Float) {
        val gap = clamp(storedGap)
        if (gap == 0f) return
        val nonEmpty = rows.filter { it.isNotEmpty() }
        if (nonEmpty.size < 2) return
        // only when the bottom row really is the space row; a layout without functional keys has none
        if (nonEmpty.last().none { it.mCode == Constants.CODE_SPACE }) return
        val total = nonEmpty.sumOf { it.first().mHeight.toDouble() }.toFloat()
        if (total <= 0f || nonEmpty.any { row -> row.any { it.mHeight <= 0f } }) return
        val scale = rowScale(nonEmpty.size, gap)
        // exactly the height the scaling took out, so the rows still add up to the same total
        val relativeGap = gap * total / nonEmpty.size
        nonEmpty.forEach { row -> row.forEach { it.mHeight *= scale } }
        nonEmpty[nonEmpty.size - 2].forEach {
            it.mHeight += relativeGap
            it.mDwellGapBelow = relativeGap
        }
    }
}
