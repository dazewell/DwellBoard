// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import android.content.Context
import android.content.SharedPreferences
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings

/** How many recent emojis are shown: the device-size default scaled by a user setting. */
object RecentEmojiCount {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 3f

    /** Upper bound for the stored list, so lowering the setting and raising it again loses nothing. */
    const val MAX_STORED = 39 * 3 // largest default (39) times MAX_SCALE

    fun get(context: Context, prefs: SharedPreferences): Int {
        val base = context.resources.getInteger(R.integer.config_emoji_keyboard_max_recents_key_count)
        val scale = runCatching { prefs.getFloat(Settings.PREF_RECENT_EMOJIS_SCALE, Defaults.PREF_RECENT_EMOJIS_SCALE) }
            .getOrDefault(Defaults.PREF_RECENT_EMOJIS_SCALE)
        return scaled(base, scale)
    }

    // NaN/out-of-range stored values fall back to the default scale
    fun scaled(base: Int, scale: Float): Int {
        val s = if (scale.isNaN()) Defaults.PREF_RECENT_EMOJIS_SCALE else scale.coerceIn(MIN_SCALE, MAX_SCALE)
        return Math.round(base * s)
    }
}
