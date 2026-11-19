// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.WithSmallTitle
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.preferences.Preference
import kotlin.math.roundToInt

/** One setting for the recents grid: a dialog with a rows and a columns slider, like the keyboard scale dialogs. */
@Composable
fun RecentEmojiGridPreference(name: String, onConfirmed: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    // recompose the summary when either value changes
    (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()?.value
    RecentEmojiCount.normalizeStored(prefs)
    val base = RecentEmojiCount.baseCount(ctx)
    val storedRows = RecentEmojiCount.storedRows(prefs)
    val storedColumns = RecentEmojiCount.chosenColumns(prefs)

    var showDialog by rememberSaveable { mutableStateOf(false) }
    Preference(
        name = name,
        onClick = { showDialog = true },
        description = stringResource(R.string.dwell_recent_emojis_grid, rowsText(storedRows, storedColumns, base), columnsText(storedColumns))
    )
    if (!showDialog) return

    var rows by rememberSaveable { mutableIntStateOf(storedRows) }
    var columns by rememberSaveable { mutableIntStateOf(storedColumns) }
    ThreeButtonAlertDialog(
        onDismissRequest = { showDialog = false },
        onConfirmed = {
            prefs.edit {
                if (rows > 0) putInt(Settings.PREF_RECENT_EMOJI_ROWS, rows) else remove(Settings.PREF_RECENT_EMOJI_ROWS)
                if (columns > 0) putInt(Settings.PREF_EMOJI_COLUMNS, columns) else remove(Settings.PREF_EMOJI_COLUMNS)
            }
            onConfirmed()
        },
        title = { Text(name) },
        scrollContent = true, // two slider sections don't fit a short landscape or split-screen window
        content = {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                Column {
                    // position 0 is the device default
                    IntSlider(stringResource(R.string.dwell_recent_emoji_rows), rows, 0, RecentEmojiCount.MAX_ROWS,
                        rowsText(rows, columns, base)) { rows = it }
                    // the position below MIN_COLUMNS is automatic, stored as 0
                    val autoPosition = RecentEmojiCount.MIN_COLUMNS - 1
                    IntSlider(stringResource(R.string.dwell_emoji_columns), if (columns == 0) autoPosition else columns,
                        autoPosition, RecentEmojiCount.MAX_COLUMNS, columnsText(columns)) { columns = if (it <= autoPosition) 0 else it }
                }
            }
        },
    )
}

@Composable
private fun IntSlider(title: String, value: Int, min: Int, max: Int, valueText: String, onChange: (Int) -> Unit) {
    WithSmallTitle(title) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = max - min - 1,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(valueText)
            TextButton({ onChange(0) }) { Text(stringResource(R.string.button_default)) }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// the default shows the rows it gives with the known column count; a split keyboard has fewer columns, so more rows
@Composable
private fun rowsText(rows: Int, columns: Int, base: Int): String {
    if (rows > 0) return rows.toString()
    val known = if (columns > 0) columns else RecentEmojiCount.lastAutoColumns
    return if (known > 0) stringResource(R.string.dwell_recent_emoji_rows_default_count, RecentEmojiCount.defaultRows(base, known))
    else stringResource(R.string.button_default)
}

@Composable
private fun columnsText(columns: Int) = when {
    columns > 0 -> columns.toString()
    RecentEmojiCount.lastAutoColumns > 0 -> stringResource(R.string.dwell_emoji_columns_auto_count, RecentEmojiCount.lastAutoColumns)
    else -> stringResource(R.string.dwell_emoji_columns_auto)
}
