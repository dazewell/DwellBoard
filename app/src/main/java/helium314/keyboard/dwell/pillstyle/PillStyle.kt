// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.pillstyle

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors

/**
 * The Pill keyboard style: flat keys without the bottom bevel, roomier gaps, and fully rounded
 * action and ?123 keys while shift and delete stay rounded rectangles like the letters.
 *
 * Most of it is resources (themes-dwell-pill-base*.xml). Code is only needed where a background
 * type can't tell keys apart: ?123 shares FUNCTIONAL with shift and delete, and the globe key is
 * FUNCTIONAL in every other style.
 */
object PillStyle {
    const val STYLE = "Pill"

    // Aligned with the PillBase / PillBaseBorder themeId enums in attrs.xml.
    const val THEME_ID_BASE = 5
    const val THEME_ID_BASE_BORDER = 6

    @JvmStatic
    fun isPillTheme(themeId: Int) = themeId == THEME_ID_BASE || themeId == THEME_ID_BASE_BORDER

    /** The icon style that follows the theme style when none is chosen; Pill has no icon set of its own. */
    @JvmStatic
    fun iconStyleFor(themeStyle: String) = if (themeStyle == STYLE) KeyboardTheme.STYLE_ROUNDED else themeStyle

    /** The keys switching between letters and symbols, the ones Gboard draws as pills. */
    @JvmStatic
    fun isPillKey(key: Key): Boolean {
        val code = key.code
        return code == KeyCode.SYMBOL_ALPHA || code == KeyCode.SYMBOL || code == KeyCode.ALPHA
    }

    @JvmStatic
    fun isPillStyle(colors: Colors) = colors.themeStyle == STYLE

    /** Keys drawn with round ends in this style, whose hints belong in the middle rather than a corner. */
    @JvmStatic
    fun isPillShaped(colors: Colors, key: Key) = isPillStyle(colors) && (key.hasActionKeyBackground() || isPillKey(key))

    /**
     * Background for [isPillKey] keys, coloured like the other functional keys, or null outside
     * the Pill style. Same white base as the functional drawable, so the MULTIPLY tint gives the
     * same shade.
     */
    @JvmStatic
    fun pillKeyBackground(context: Context, colors: Colors): Drawable? {
        if (!isPillStyle(colors)) return null
        val drawable = ContextCompat.getDrawable(context, R.drawable.btn_keyboard_key_dwell_pill_round)?.mutate() ?: return null
        colors.setColor(drawable, ColorType.FUNCTIONAL_KEY_BACKGROUND)
        return drawable
    }

    /**
     * Whether a top-right hint icon has to move inside the round end. Not when the background keeps
     * its aspect ratio (no key borders): that circle is Rounded's, and so is the hint placement.
     */
    @JvmStatic
    fun hintInsideRoundEnd(colors: Colors, key: Key, defaultLabelFlags: Int) = isPillShaped(colors, key)
            && !key.hasHintLabel() && !key.hasShiftedLetterHintIcon() && !key.needsToKeepBackgroundAspectRatio(defaultLabelFlags)

    // The pill fills the key, so its top-right round end is a circle of radius min(w, h) / 2 centred
    // at (w - r, r). The icon goes on that circle's 45° line, pulled in until its far corner clears
    // the edge. Rounded's fixed corner offset puts it outside the curve once the key is near square.
    private const val HALF_DIAGONAL = 0.7071f
    private const val EDGE_MARGIN = 0.15f

    private fun hintIconCenterOffset(keyWidth: Int, keyHeight: Int, iconSize: Int): Float {
        val r = minOf(keyWidth, keyHeight) / 2f
        return (r - iconSize * (HALF_DIAGONAL + EDGE_MARGIN)).coerceAtLeast(0f) * HALF_DIAGONAL
    }

    @JvmStatic
    fun hintIconLeft(keyWidth: Int, keyHeight: Int, iconSize: Int): Float =
        keyWidth - minOf(keyWidth, keyHeight) / 2f + hintIconCenterOffset(keyWidth, keyHeight, iconSize) - iconSize / 2f

    @JvmStatic
    fun hintIconTop(keyWidth: Int, keyHeight: Int, iconSize: Int): Float =
        minOf(keyWidth, keyHeight) / 2f - hintIconCenterOffset(keyWidth, keyHeight, iconSize) - iconSize / 2f

    /** [pill] in the pressed state the key just gave [selected]. */
    @JvmStatic
    fun withStateOf(pill: Drawable, selected: Drawable): Drawable {
        pill.state = selected.state
        return pill
    }
}
