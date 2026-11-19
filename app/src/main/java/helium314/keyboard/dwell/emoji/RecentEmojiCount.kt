// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import android.content.Context
import android.content.SharedPreferences
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import kotlin.math.roundToInt

/** User control over the emoji grid: how many rows of recents are kept and how many columns a page has. */
object RecentEmojiCount {
    const val MAX_ROWS = 20
    const val MIN_COLUMNS = 4
    const val MAX_COLUMNS = 12

    /** Upper bound for the stored list, so lowering the setting and raising it again loses nothing. */
    const val MAX_STORED = MAX_ROWS * MAX_COLUMNS

    /** The device-size default number of recents. */
    fun baseCount(context: Context) = context.resources.getInteger(R.integer.config_emoji_keyboard_max_recents_key_count)

    /** 0 means the device default, otherwise 1..MAX_ROWS */
    fun rows(stored: Int) = if (stored <= 0) 0 else stored.coerceAtMost(MAX_ROWS)

    /** 0 means automatic, otherwise MIN_COLUMNS..MAX_COLUMNS */
    fun columns(stored: Int) = if (stored <= 0) 0 else stored.coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    /** The device default count as whole rows of [columns]: never less than one row. */
    fun defaultRows(base: Int, columns: Int) = if (columns <= 0) 0 else (base / columns.toFloat()).roundToInt().coerceAtLeast(1)

    /**
     * How many recents the grid keeps: the chosen rows (or the default count rounded to whole rows) times the
     * columns the layout really has, which already excludes split-keyboard spacers. Capped by what is stored.
     */
    fun maxKeys(rows: Int, base: Int, columns: Int): Int {
        if (columns <= 0) return base
        val wanted = if (rows > 0) rows else defaultRows(base, columns)
        return wanted.coerceAtMost(MAX_STORED / columns).coerceAtLeast(1) * columns
    }

    @JvmStatic
    fun maxKeys(prefs: SharedPreferences, base: Int, columns: Int) = maxKeys(storedRows(prefs), base, columns)

    fun storedRows(prefs: SharedPreferences) = rows(
        runCatching { prefs.getInt(Settings.PREF_RECENT_EMOJI_ROWS, Defaults.PREF_RECENT_EMOJI_ROWS) }
            .getOrDefault(Defaults.PREF_RECENT_EMOJI_ROWS)
    )

    /**
     * Rewrites stored values the settings UI would otherwise show unchanged although the keyboard applies something
     * else: a wrong type is dropped, an out-of-range value is clamped to what the keyboard uses.
     */
    fun normalizeStored(prefs: SharedPreferences) {
        normalizeKey(prefs, Settings.PREF_RECENT_EMOJI_ROWS) { rows(it).takeIf { r -> r > 0 } }
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

    /** Columns the automatic layout last produced, shown in settings; 0 until an emoji page was built. */
    @Volatile
    var lastAutoColumns = 0
        private set

    @JvmStatic
    fun reportAutoColumns(columns: Int) {
        if (columns > 0) lastAutoColumns = columns
    }

    /** The column count the user chose: 0 for automatic, otherwise MIN_COLUMNS..MAX_COLUMNS. */
    @JvmStatic
    fun chosenColumns(prefs: SharedPreferences): Int {
        val stored = runCatching { prefs.getInt(Settings.PREF_EMOJI_COLUMNS, Defaults.PREF_EMOJI_COLUMNS) }
            .getOrDefault(Defaults.PREF_EMOJI_COLUMNS)
        return columns(stored)
    }
}
