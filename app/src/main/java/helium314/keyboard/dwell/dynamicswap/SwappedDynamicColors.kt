// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.dynamicswap

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.DefaultColors

/**
 * Dynamic colors with the roles turned around: a near-white (or near-black) background and keys
 * in the wallpaper's accent tones, where upstream's Dynamic colors has tinted background and
 * plain keys. Light and dark are separate themes so either can sit in the day or night slot.
 *
 * The drawing is plain [DefaultColors]; this class only picks the palette and reports when the
 * system palette moved, which DefaultColors never does on its own.
 */
@RequiresApi(Build.VERSION_CODES.S)
class SwappedDynamicColors private constructor(
    private val dark: Boolean,
    private val palette: IntArray,
    delegate: Colors,
) : Colors by delegate {

    override fun haveColorsChanged(context: Context) = !palette.contentEquals(palette(context, dark))

    companion object {
        const val THEME_LIGHT = "dwell_dynamic_swapped_light"
        const val THEME_DARK = "dwell_dynamic_swapped_dark"

        /** Entries for the color theme list, light only for the day slot like upstream's Light. */
        @JvmStatic
        fun availableThemes(isNight: Boolean): Array<String?> =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) emptyArray()
            else arrayOf(if (!isNight) THEME_LIGHT else null, THEME_DARK)

        /** Null below Android 12, where there is no system palette; the caller falls back. */
        @JvmStatic
        fun create(themeName: String, themeStyle: String, hasKeyBorders: Boolean, context: Context, keyboardBackground: Drawable?): Colors? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            val dark = themeName == THEME_DARK
            val p = palette(context, dark)
            val colors = DefaultColors(
                themeStyle,
                hasKeyBorders,
                accent = p[0],
                background = p[1],
                keyBackground = p[2],
                functionalKey = p[3],
                spaceBar = p[2],
                keyText = p[4],
                keyHintText = p[5],
                keyboardBackground = keyboardBackground,
            )
            return SwappedDynamicColors(dark, p, colors)
        }

        // accent, background, key, functional key, key text, hint text
        @RequiresApi(Build.VERSION_CODES.S)
        private fun palette(context: Context, dark: Boolean): IntArray {
            val ids = if (dark) intArrayOf(
                android.R.color.system_accent1_200,
                android.R.color.system_neutral1_900,
                android.R.color.system_accent1_700,
                android.R.color.system_accent1_800,
                android.R.color.system_neutral1_10,
                android.R.color.system_accent1_100,
            ) else intArrayOf(
                android.R.color.system_accent1_600,
                android.R.color.system_neutral1_10,
                android.R.color.system_accent1_100,
                android.R.color.system_accent1_200,
                android.R.color.system_accent1_900,
                android.R.color.system_accent1_700,
            )
            return IntArray(ids.size) { ContextCompat.getColor(context, ids[it]) }
        }
    }
}
