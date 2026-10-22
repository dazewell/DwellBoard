// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.gboardtheme

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import helium314.keyboard.keyboard.KeyboardTheme.Companion.STYLE_HOLO
import helium314.keyboard.keyboard.KeyboardTheme.Companion.STYLE_MATERIAL
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.ColorType.*
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.utils.adjustLuminosityAndKeepAlpha
import helium314.keyboard.latin.utils.brighten
import helium314.keyboard.latin.utils.brightenOrDarken
import helium314.keyboard.latin.utils.darken
import helium314.keyboard.latin.utils.isBrightColor
import helium314.keyboard.latin.utils.isDarkColor

/**
 * Gboard's current Dynamic Color theme, and the same with background and key colors swapped.
 * Light and dark are separate themes, so [dark] replaces upstream's system night check and
 * either can sit in the day or night slot.
 *
 * The colors were sampled from Gboard 18.4 on Android 16 and matched to Material color roles
 * (Android 14+); older versions get the nearest palette tones. Gboard gives every function key
 * and Enter the same secondary container color.
 *
 * The drawing is a copy of upstream's DynamicColors (latin/common/Colors.kt), so upstream's
 * class stays untouched. When upstream changes DynamicColors, diff it against this file and
 * carry the change over. Intended differences: the class header, [dark] and [swapped], the
 * color getters, functional and action keys drawn in [functionalKey] with [functionalKeyText]
 * at night too, adjustedKeyBackground taken from the lighter surface, pressedKey for day
 * letter keys taken from the darker one, and the companion.
 */
@RequiresApi(Build.VERSION_CODES.S)
class GboardColors(context: Context, dark: Boolean, private val swapped: Boolean, override val themeStyle: String, override val hasKeyBorders: Boolean, private var keyboardBackground: Drawable? = null) : Colors {

    private val isNight = dark
    private val hasRoles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    private val accent = getAccent(context)
    private val gesture = getGesture(context)
    private val background = getBackground(context)
    private val keyBackground = getKeyBackground(context)
    private val functionalKey = getFunctionalKey(context)
    private val functionalKeyText = getFunctionalKeyText(context)
    private val spaceBar = getKeyBackground(context)
    private val keyText = getKeyText(context)
    private val keyHintText = getKeyHintText(context)
    private val spaceBarText = getSpaceBarText(context)

    /** [role] on Android 14+, else the palette [tone] nearest to it */
    private fun color(context: Context, role: Int, tone: Int) = ContextCompat.getColor(context, if (hasRoles) role else tone)

    private fun getAccent(context: Context) = if (isNight) ContextCompat.getColor(context, android.R.color.system_accent1_100)
        else ContextCompat.getColor(context, android.R.color.system_accent1_200)
    private fun getGesture(context: Context) = if (isNight) accent
        else ContextCompat.getColor(context, android.R.color.system_accent1_600)
    private fun getSurface(context: Context) = if (isNight) color(context, android.R.color.system_surface_container_dark, android.R.color.system_neutral1_900)
        else color(context, android.R.color.system_surface_container_light, android.R.color.system_neutral1_50)
    private fun getKeySurface(context: Context) = if (isNight) color(context, android.R.color.system_surface_bright_dark, android.R.color.system_neutral2_800)
        else color(context, android.R.color.system_surface_container_lowest_light, android.R.color.system_neutral1_0)
    private fun getBackground(context: Context) = if (swapped) getKeySurface(context) else getSurface(context)
    private fun getKeyBackground(context: Context) = if (swapped) getSurface(context) else getKeySurface(context)
    private fun getFunctionalKey(context: Context) = if (isNight) color(context, android.R.color.system_secondary_container_dark, android.R.color.system_accent2_700)
        else color(context, android.R.color.system_secondary_container_light, android.R.color.system_accent2_100)
    private fun getFunctionalKeyText(context: Context) = if (isNight) color(context, android.R.color.system_on_secondary_container_dark, android.R.color.system_accent2_100)
        else color(context, android.R.color.system_on_secondary_container_light, android.R.color.system_accent2_700)
    private fun getKeyText(context: Context) = if (isNight) color(context, android.R.color.system_on_surface_dark, android.R.color.system_neutral1_100)
        else color(context, android.R.color.system_on_surface_light, android.R.color.system_neutral1_900)
    private fun getKeyHintText(context: Context) = if (isNight) color(context, android.R.color.system_on_surface_variant_dark, android.R.color.system_neutral2_200)
        else color(context, android.R.color.system_on_surface_variant_light, android.R.color.system_neutral2_700)
    private fun getSpaceBarText(context: Context) = getKeyHintText(context)

    override fun haveColorsChanged(context: Context) =
        accent != getAccent(context)
                || gesture != getGesture(context)
                || background != getBackground(context)
                || keyBackground != getKeyBackground(context)
                || functionalKey != getFunctionalKey(context)
                || functionalKeyText != getFunctionalKeyText(context)
                || keyText != getKeyText(context)
                || keyHintText != getKeyHintText(context)
                || spaceBarText != getSpaceBarText(context)

    private val navBar: Int
    /** brightened or darkened variant of [background], to be used if exact background color would be
     *  bad contrast, e.g. popup keys popup or no border space bar */
    private val adjustedBackground: Int
    /** further brightened or darkened variant of [adjustedBackground] */
    private val doubleAdjustedBackground: Int
    /** brightened or darkened variant of [keyText] */
    private val adjustedKeyText: Int

    private val backgroundFilter: ColorFilter
    private val adjustedBackgroundFilter: ColorFilter
    private val keyTextFilter: ColorFilter
    private val accentColorFilter: ColorFilter
    /** color filter for the white action key icons in material theme, switches to gray if necessary for contrast */
    private val actionKeyIconColorFilter: ColorFilter?

    private val backgroundStateList: ColorStateList
    private val keyStateList: ColorStateList
    private val functionalKeyStateList: ColorStateList
    private val actionKeyStateList: ColorStateList
    private val spaceBarStateList: ColorStateList
    private val adjustedBackgroundStateList: ColorStateList
    private val stripBackgroundList: ColorStateList
    private val toolbarKeyStateList = activatedStateList(
        keyText,
        if (isBrightColor(keyText)) darken(darken(keyText))
        else brighten(brighten(keyText))
    )

    /** darkened variant of [accent] because the accent color is always light for dynamic colors */
    private val adjustedAccent: Int = darken(accent)
    /** further darkened variant of [adjustedAccent] */
    private val doubleAdjustedAccent: Int = darken(adjustedAccent)

    /** darkened variant of [functionalKey] used in day mode */
    private val adjustedFunctionalKey: Int = darken(functionalKey)
    /** further darkened variant of [adjustedFunctionalKey] */
    private val doubleAdjustedFunctionalKey: Int = darken(adjustedFunctionalKey)
    /** [functionalKey] when pressed; the night one is dark, so it brightens */
    private val pressedFunctionalKey: Int = if (!isNight) doubleAdjustedFunctionalKey else brighten(functionalKey)

    /** brightened variant of the lighter night surface, the keys unless [swapped] */
    private val adjustedKeyBackground: Int = brighten(if (swapped) background else keyBackground)
    /** day pressed letter key, darkened from the darker day surface, the background unless [swapped] */
    private val pressedKey: Int = darken(if (swapped) keyBackground else background)
    /** further brightened variant of [adjustedKeyBackground] */
    private val doubleAdjustedKeyBackground: Int = brighten(adjustedKeyBackground)
    private var backgroundSetupDone = false

    init {
        accentColorFilter = colorFilter(doubleAdjustedAccent)

        if (themeStyle == STYLE_HOLO && keyboardBackground == null) {
            val darkerBackground = adjustLuminosityAndKeepAlpha(background, -0.2f)
            navBar = darkerBackground
            keyboardBackground = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(background, darkerBackground))
            backgroundSetupDone = true
        } else {
            navBar = background
        }

        // todo (idea): make better use of the states?
        //  could also use / create StateListDrawables in colors (though that's a style than a color...)
        //  this would better allow choosing e.g. cornered/rounded drawables for poup keys or moreSuggestions
        backgroundFilter = colorFilter(background)
        adjustedKeyText = brightenOrDarken(keyText, true)

        if (isDarkColor(background)) {
            adjustedBackground = brighten(background)
            doubleAdjustedBackground = brighten(adjustedBackground)
        } else {
            adjustedBackground = darken(background)
            doubleAdjustedBackground = darken(adjustedBackground)
        }
        adjustedBackgroundStateList =
            if (themeStyle == STYLE_HOLO) {
                pressedStateList(accent, adjustedBackground)
            } else if (isNight) {
                if (hasKeyBorders) pressedStateList(doubleAdjustedAccent, keyBackground)
                else pressedStateList(adjustedAccent, adjustedKeyBackground)
            } else {
                pressedStateList(accent, Color.WHITE)
            }

        val stripBackground = if (keyboardBackground == null && !hasKeyBorders) {
            if (isDarkColor(background)) 0x16ffffff else 0x11000000
        } else {
            Color.TRANSPARENT
        }
        val pressedStripElementBackground = if (keyboardBackground == null) adjustedBackground
        else if (isDarkColor(background)) 0x22ffffff else 0x11000000
        stripBackgroundList = pressedStateList(pressedStripElementBackground, stripBackground)

        adjustedBackgroundFilter =
            if (themeStyle == STYLE_HOLO) colorFilter(adjustedBackground)
            else colorFilter(keyBackground)

        if (hasKeyBorders) {
            backgroundStateList =
                if (!isNight) pressedStateList(adjustedFunctionalKey, background)
                else pressedStateList(adjustedKeyBackground, background)

            keyStateList =
                if (!isNight) pressedStateList(pressedKey, keyBackground)
                else pressedStateList(adjustedKeyBackground, keyBackground)

            functionalKeyStateList = pressedStateList(pressedFunctionalKey, functionalKey)

            actionKeyStateList = functionalKeyStateList

            spaceBarStateList =
                if (themeStyle == STYLE_HOLO) pressedStateList(spaceBar, spaceBar)
                else keyStateList

        } else {
            // need to set color to background if key borders are disabled, or there will be ugly keys
            backgroundStateList =
                if (!isNight) pressedStateList(adjustedFunctionalKey, background)
                else pressedStateList(adjustedKeyBackground, background)

            keyStateList =
                if (!isNight) pressedStateList(adjustedFunctionalKey, Color.TRANSPARENT)
                else pressedStateList(functionalKey, Color.TRANSPARENT)

            functionalKeyStateList =
                if (themeStyle == STYLE_HOLO) pressedStateList(functionalKey, Color.TRANSPARENT)
                else keyStateList

            actionKeyStateList =
                if (themeStyle == STYLE_HOLO) pressedStateList(accent, Color.TRANSPARENT)
                else pressedStateList(pressedFunctionalKey, functionalKey)

            spaceBarStateList =
                if (!isNight) pressedStateList(gesture, adjustedFunctionalKey)
                else pressedStateList(adjustedKeyBackground, spaceBar)
        }
        keyTextFilter = colorFilter(keyText)

        actionKeyIconColorFilter = when {
            themeStyle == STYLE_HOLO -> keyTextFilter
            else -> colorFilter(functionalKeyText)
        }
    }

    override fun get(color: ColorType): Int = when (color) {
        TOOL_BAR_KEY_ENABLED_BACKGROUND, EMOJI_CATEGORY_SELECTED, CLIPBOARD_PIN -> accent
        ACTION_KEY_BACKGROUND -> functionalKey
        SHIFT_KEY_ICON -> if (accentShiftedIcon) accent else keyText
        AUTOFILL_BACKGROUND_CHIP, GESTURE_PREVIEW, POPUP_KEYS_BACKGROUND, MORE_SUGGESTIONS_BACKGROUND, KEY_PREVIEW_BACKGROUND -> adjustedBackground
        TOOL_BAR_EXPAND_KEY_BACKGROUND -> if (!isNight) accent else doubleAdjustedBackground
        GESTURE_TRAIL -> gesture
        KEY_TEXT, SUGGESTION_AUTO_CORRECT, REMOVE_SUGGESTION_ICON, EMOJI_KEY_TEXT, KEY_PREVIEW_TEXT, POPUP_KEY_TEXT,
            KEY_ICON, POPUP_KEY_ICON, ONE_HANDED_MODE_BUTTON, EMOJI_CATEGORY, TOOL_BAR_KEY,
            EMOJI_SEARCH_TEXT, CLIPBOARD_SUGGESTION_ICON -> keyText
        KEY_HINT_TEXT -> keyHintText
        SPACE_BAR_TEXT -> spaceBarText
        FUNCTIONAL_KEY_BACKGROUND, EMOJI_SEARCH_BACKGROUND -> functionalKey
        FUNCTIONAL_KEY_TEXT, ACTION_KEY_ICON -> functionalKeyText
        SPACE_BAR_BACKGROUND -> spaceBar
        MORE_SUGGESTIONS_WORD_BACKGROUND, MAIN_BACKGROUND -> background
        KEY_BACKGROUND -> keyBackground
        ACTION_KEY_POPUP_KEYS_BACKGROUND -> if (themeStyle == STYLE_HOLO) adjustedBackground else functionalKey
        STRIP_BACKGROUND -> if (!hasKeyBorders && themeStyle == STYLE_MATERIAL) adjustedBackground else background
        CLIPBOARD_SUGGESTION_BACKGROUND -> keyBackground
        NAVIGATION_BAR -> navBar
        MORE_SUGGESTIONS_HINT, SUGGESTED_WORD, SUGGESTION_TYPED_WORD, SUGGESTION_VALID_WORD -> adjustedKeyText
        TOOL_BAR_EXPAND_KEY -> Color.WHITE
    }

    override fun setColor(drawable: Drawable, color: ColorType) {
        val colorStateList = when (color) {
            MORE_SUGGESTIONS_WORD_BACKGROUND -> backgroundStateList
            KEY_BACKGROUND -> keyStateList
            FUNCTIONAL_KEY_BACKGROUND -> functionalKeyStateList
            ACTION_KEY_BACKGROUND -> actionKeyStateList
            SPACE_BAR_BACKGROUND -> spaceBarStateList
            POPUP_KEYS_BACKGROUND -> adjustedBackgroundStateList
            STRIP_BACKGROUND -> stripBackgroundList
            ACTION_KEY_POPUP_KEYS_BACKGROUND -> if (themeStyle == STYLE_HOLO) adjustedBackgroundStateList
                else actionKeyStateList
            TOOL_BAR_KEY -> toolbarKeyStateList
            else -> null // use color filter
        }
        if (colorStateList == null) {
            drawable.colorFilter = getColorFilter(color)
            return
        }
        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.MULTIPLY)
        DrawableCompat.setTintList(drawable, colorStateList)
    }

    override fun setColor(view: ImageView, color: ColorType) {
        if (color == TOOL_BAR_KEY) {
            setColor(view.drawable, color)
            return
        }
        view.colorFilter = getColorFilter(color)
    }

    private fun getColorFilter(color: ColorType): ColorFilter? = when (color) {
        EMOJI_CATEGORY_SELECTED, CLIPBOARD_PIN -> accentColorFilter
        SHIFT_KEY_ICON -> if (accentShiftedIcon) accentColorFilter else keyTextFilter
        REMOVE_SUGGESTION_ICON, EMOJI_CATEGORY, KEY_TEXT,
            KEY_ICON, ONE_HANDED_MODE_BUTTON, TOOL_BAR_KEY, TOOL_BAR_EXPAND_KEY -> keyTextFilter
        KEY_PREVIEW_BACKGROUND -> adjustedBackgroundFilter
        ACTION_KEY_ICON -> actionKeyIconColorFilter
        else -> colorFilter(get(color))
    }

    override fun setBackground(view: View, color: ColorType) {
        if (view.background == null)
            view.setBackgroundColor(Color.WHITE) // set white to make the color filters work
        when (color) {
            KEY_PREVIEW_BACKGROUND -> view.background.colorFilter = adjustedBackgroundFilter
            FUNCTIONAL_KEY_BACKGROUND, KEY_BACKGROUND, MORE_SUGGESTIONS_WORD_BACKGROUND, SPACE_BAR_BACKGROUND, STRIP_BACKGROUND, CLIPBOARD_SUGGESTION_BACKGROUND -> setColor(view.background, color)
            ONE_HANDED_MODE_BUTTON -> setColor(view.background, if (keyboardBackground == null) MAIN_BACKGROUND else STRIP_BACKGROUND)
            MORE_SUGGESTIONS_BACKGROUND -> view.background.colorFilter = backgroundFilter
            POPUP_KEYS_BACKGROUND ->
                if (themeStyle != STYLE_HOLO)
                    setColor(view.background, POPUP_KEYS_BACKGROUND)
                else view.background.colorFilter = adjustedBackgroundFilter
            MAIN_BACKGROUND -> {
                if (keyboardBackground != null) {
                    if (!backgroundSetupDone) {
                        keyboardBackground = keyboardBackground!!.toBitmap(view.width, view.height).toDrawable(view.context.resources)
                        backgroundSetupDone = true
                    }
                    view.background = keyboardBackground
                } else {
                    view.background.colorFilter = backgroundFilter
                }
            }
            else -> view.background.colorFilter = backgroundFilter
        }
    }

    companion object {
        const val THEME_LIGHT = "dwell_gboard_light"
        const val THEME_DARK = "dwell_gboard_dark"
        const val THEME_SWAPPED_LIGHT = "dwell_gboard_swapped_light"
        const val THEME_SWAPPED_DARK = "dwell_gboard_swapped_dark"
        @JvmField val THEMES = setOf(THEME_LIGHT, THEME_DARK, THEME_SWAPPED_LIGHT, THEME_SWAPPED_DARK)

        /** Entries for the color theme list, light ones only for the day slot like upstream's Light. */
        @JvmStatic
        fun availableThemes(isNight: Boolean): Array<String?> =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) emptyArray()
            else arrayOf(
                if (!isNight) THEME_LIGHT else null, THEME_DARK,
                if (!isNight) THEME_SWAPPED_LIGHT else null, THEME_SWAPPED_DARK,
            )

        /** Null below Android 12, where there is no system palette; the caller falls back. */
        @JvmStatic
        fun create(themeName: String, themeStyle: String, hasKeyBorders: Boolean, context: Context, keyboardBackground: Drawable?): Colors? =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null
            else GboardColors(
                context,
                dark = themeName == THEME_DARK || themeName == THEME_SWAPPED_DARK,
                swapped = themeName == THEME_SWAPPED_LIGHT || themeName == THEME_SWAPPED_DARK,
                themeStyle, hasKeyBorders, keyboardBackground,
            )
    }
}

private fun colorFilter(color: Int, mode: BlendModeCompat = BlendModeCompat.MODULATE): ColorFilter =
    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(color, mode)!!

private fun pressedStateList(pressed: Int, normal: Int): ColorStateList {
    val states = arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf(-android.R.attr.state_pressed))
    return ColorStateList(states, intArrayOf(pressed, normal))
}

private fun activatedStateList(activated: Int, normal: Int): ColorStateList {
    val states = arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf(-android.R.attr.state_activated))
    return ColorStateList(states, intArrayOf(activated, normal))
}
