// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import android.content.Context
import android.content.SharedPreferences
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings

/** User control over the emoji grid: how many recents are kept and how many columns a page has. */
object RecentEmojiCount {
    const val MAX_FACTOR = 3
    const val MIN_COLUMNS = 4
    const val MAX_COLUMNS = 12

    /** Upper bound for the stored list, so lowering the setting and raising it again loses nothing. */
    const val MAX_STORED = 39 * MAX_FACTOR // largest device default (39) times MAX_FACTOR

    /** The device-size default number of recents. */
    fun baseCount(context: Context) = context.resources.getInteger(R.integer.config_emoji_keyboard_max_recents_key_count)

    fun get(context: Context, prefs: SharedPreferences): Int {
        val stored = runCatching { prefs.getInt(Settings.PREF_RECENT_EMOJIS_COUNT, Defaults.PREF_RECENT_EMOJIS_COUNT) }
            .getOrDefault(Defaults.PREF_RECENT_EMOJIS_COUNT)
        return count(baseCount(context), stored)
    }

    // 0, absent or out-of-range stored values fall back to the nearest valid count
    fun count(base: Int, stored: Int) = if (stored <= 0) base else stored.coerceIn(base, base * MAX_FACTOR)

    /** 0 means automatic, otherwise MIN_COLUMNS..MAX_COLUMNS */
    fun columns(stored: Int) = if (stored <= 0) 0 else stored.coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    /**
     * Rewrites stored values the settings UI would otherwise show unchanged although the keyboard applies something
     * else: a wrong type is dropped, an out-of-range value is clamped to what the keyboard uses.
     */
    fun normalizeStored(prefs: SharedPreferences, base: Int) {
        normalizeKey(prefs, Settings.PREF_RECENT_EMOJIS_COUNT) { if (it <= 0) null else count(base, it) }
        normalizeKey(prefs, Settings.PREF_EMOJI_COLUMNS) { columns(it) }
    }

    // normalize returns the value to keep, or null to drop the key (so the default applies)
    private fun normalizeKey(prefs: SharedPreferences, key: String, normalize: (Int) -> Int?) {
        if (!prefs.contains(key)) return
        val stored = runCatching { prefs.getInt(key, 0) }.getOrNull()
        val fixed = stored?.let(normalize)
        if (fixed == null) prefs.edit().remove(key).apply()
        else if (fixed != stored) prefs.edit().putInt(key, fixed).apply()
    }

    /** Replaces [autoScale] when the user chose a column count; [stepWidth] is the unscaled width of one key slot. */
    @JvmStatic
    fun widthScale(prefs: SharedPreferences, baseWidth: Int, stepWidth: Int, autoScale: Float): Float {
        val stored = runCatching { prefs.getInt(Settings.PREF_EMOJI_COLUMNS, Defaults.PREF_EMOJI_COLUMNS) }
            .getOrDefault(Defaults.PREF_EMOJI_COLUMNS)
        val columns = columns(stored)
        return if (columns == 0 || stepWidth <= 0) autoScale else baseWidth.toFloat() / columns / stepWidth
    }
}
