// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.dynamicswap

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
 * Upstream's DynamicColors (latin/common/Colors.kt) with the keyboard background and key colors
 * swapped: white keys on a tinted background become tinted keys on a white background.
 * Light and dark are separate themes, so [dark] replaces upstream's system night check and
 * either can sit in the day or night slot.
 *
 * Kept as a copy so upstream's class stays untouched. When upstream changes DynamicColors,
 * diff it against this file and carry the change over; the only intended differences are the
 * class header, [dark], the swapped getBackground / getKeyBackground, adjustedKeyBackground
 * derived from background, and the companion.
 */
@RequiresApi(Build.VERSION_CODES.S)
class SwappedDynamicColors(context: Context, dark: Boolean, override val themeStyle: String, override val hasKeyBorders: Boolean, private var keyboardBackground: Drawable? = null) : Colors {

    private val isNight = dark

    private val accent = getAccent(context)
    private val gesture = getGesture(context)
    private val background = getBackground(context)
    private val keyBackground = getKeyBackground(context)
    private val functionalKey = getFunctionalKey(context)
    private val spaceBar = getKeyBackground(context)
    private val keyText = getKeyText(context)
    private val keyHintText = getKeyHintText(context)
    private val spaceBarText = getSpaceBarText(context)

    private fun getAccent(context: Context) = if (isNight) ContextCompat.getColor(context, android.R.color.system_accent1_100)
        else ContextCompat.getColor(context, android.R.color.system_accent1_200)
    private fun getGesture(context: Context) = if (isNight) accent
        else ContextCompat.getColor(context, android.R.color.system_accent1_600)
    // swapped: upstream has background neutral1_900 / _50 and keys neutral1_800 / _0
    private fun getBackground(context: Context) = if (isNight) ContextCompat.getColor(context, android.R.color.system_neutral1_800)
        else ContextCompat.getColor(context, android.R.color.system_neutral1_0)
    private fun getKeyBackground(context: Context) = if (isNight) ContextCompat.getColor(context, android.R.color.system_neutral1_900)
        else ContextCompat.getColor(context, android.R.color.system_neutral1_50)
    private fun getFunctionalKey(context: Context) = if (isNight) ContextCompat.getColor(context, android.R.color.system_accent2_300)
        else ContextCompat.getColor(context, android.R.color.system_accent2_100)
    private fun getKeyText(context: Context) = if (isNight) ContextCompat.getColor(context, android.R.color.system_neutral1_50)
        else ContextCompat.getColor(context, android.R.color.system_accent3_900)
    private fun getKeyHintText(context: Context) = if (isNight) keyText
        else ContextCompat.getColor(context, android.R.color.system_accent3_700)
    private fun getSpaceBarText(context: Context) = if (isNight) ColorUtils.setAlphaComponent(ContextCompat.getColor(context, android.R.color.system_neutral1_50), 127)
        else ColorUtils.setAlphaComponent(ContextCompat.getColor(context, android.R.color.system_accent3_700), 127)

    override fun haveColorsChanged(context: Context) =
        accent != getAccent(context)
                || gesture != getGesture(context)
                || background != getBackground(context)
                || keyBackground != getKeyBackground(context)
                || functionalKey != getFunctionalKey(context)
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

    /** brightened variant of [keyBackground] used in night mode */
    // swapped: from background, which now holds upstream's lighter night key color; the darker keys
    // would put pressed keys and functional keys at the background's own shade
    private val adjustedKeyBackground: Int = brighten(background)
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
                if (!isNight) pressedStateList(adjustedBackground, keyBackground)
                else pressedStateList(adjustedKeyBackground, keyBackground)

            functionalKeyStateList =
                if (!isNight) pressedStateList(doubleAdjustedFunctionalKey, functionalKey)
                else pressedStateList(functionalKey, doubleAdjustedKeyBackground)

            actionKeyStateList =
                if (!isNight) pressedStateList(gesture, accent)
                else pressedStateList(doubleAdjustedAccent, accent)

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
                else if (!isNight) pressedStateList(gesture, accent)
                else pressedStateList(doubleAdjustedAccent, accent)

            spaceBarStateList =
                if (!isNight) pressedStateList(gesture, adjustedFunctionalKey)
                else pressedStateList(adjustedKeyBackground, spaceBar)
        }
        keyTextFilter = colorFilter(keyText)

        actionKeyIconColorFilter = when {
            themeStyle == STYLE_HOLO -> keyTextFilter
            // the white icon may not have enough contrast, and can't be adjusted by the user
            isBrightColor(accent) -> colorFilter(Color.DKGRAY)
            else -> null
        }
    }

    override fun get(color: ColorType): Int = when (color) {
        TOOL_BAR_KEY_ENABLED_BACKGROUND, EMOJI_CATEGORY_SELECTED, ACTION_KEY_BACKGROUND, CLIPBOARD_PIN -> accent
        SHIFT_KEY_ICON -> if (accentShiftedIcon) accent else keyText
        AUTOFILL_BACKGROUND_CHIP, GESTURE_PREVIEW, POPUP_KEYS_BACKGROUND, MORE_SUGGESTIONS_BACKGROUND, KEY_PREVIEW_BACKGROUND -> adjustedBackground
        TOOL_BAR_EXPAND_KEY_BACKGROUND -> if (!isNight) accent else doubleAdjustedBackground
        GESTURE_TRAIL -> gesture
        KEY_TEXT, SUGGESTION_AUTO_CORRECT, REMOVE_SUGGESTION_ICON, EMOJI_KEY_TEXT, KEY_PREVIEW_TEXT, POPUP_KEY_TEXT,
            KEY_ICON, POPUP_KEY_ICON, ONE_HANDED_MODE_BUTTON, EMOJI_CATEGORY, TOOL_BAR_KEY, FUNCTIONAL_KEY_TEXT,
            EMOJI_SEARCH_TEXT, CLIPBOARD_SUGGESTION_ICON -> keyText
        KEY_HINT_TEXT -> keyHintText
        SPACE_BAR_TEXT -> spaceBarText
        FUNCTIONAL_KEY_BACKGROUND, EMOJI_SEARCH_BACKGROUND -> if (!isNight) functionalKey else doubleAdjustedKeyBackground
        SPACE_BAR_BACKGROUND -> spaceBar
        MORE_SUGGESTIONS_WORD_BACKGROUND, MAIN_BACKGROUND -> background
        KEY_BACKGROUND -> keyBackground
        ACTION_KEY_POPUP_KEYS_BACKGROUND -> if (themeStyle == STYLE_HOLO) adjustedBackground else accent
        STRIP_BACKGROUND -> if (!hasKeyBorders && themeStyle == STYLE_MATERIAL) adjustedBackground else background
        CLIPBOARD_SUGGESTION_BACKGROUND -> keyBackground
        NAVIGATION_BAR -> navBar
        MORE_SUGGESTIONS_HINT, SUGGESTED_WORD, SUGGESTION_TYPED_WORD, SUGGESTION_VALID_WORD -> adjustedKeyText
        ACTION_KEY_ICON, TOOL_BAR_EXPAND_KEY -> Color.WHITE
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
        const val THEME_LIGHT = "dwell_dynamic_swapped_light"
        const val THEME_DARK = "dwell_dynamic_swapped_dark"

        /** Entries for the color theme list, light only for the day slot like upstream's Light. */
        @JvmStatic
        fun availableThemes(isNight: Boolean): Array<String?> =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) emptyArray()
            else arrayOf(if (!isNight) THEME_LIGHT else null, THEME_DARK)

        /** Null below Android 12, where there is no system palette; the caller falls back. */
        @JvmStatic
        fun create(themeName: String, themeStyle: String, hasKeyBorders: Boolean, context: Context, keyboardBackground: Drawable?): Colors? =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null
            else SwappedDynamicColors(context, themeName == THEME_DARK, themeStyle, hasKeyBorders, keyboardBackground)
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
