// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.swipesensitivity

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.WithSmallTitle
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.preferences.Preference

/** Swipe sensitivity with a slider per swipe direction: left selects, right takes the selection back. */
@Composable
fun SwipeSensitivityPreference(setting: Setting, rightKey: String, leftDefault: Int, rightDefault: Int) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val left = SwipeSensitivity.read(prefs, setting.key, leftDefault)
    val right = SwipeSensitivity.read(prefs, rightKey, rightDefault)
    var showDialog by rememberSaveable { mutableStateOf(false) }
    Preference(
        name = setting.title,
        onClick = { showDialog = true },
        description = "${stringResource(R.string.left)} $left%, ${stringResource(R.string.right)} $right%",
    )
    if (showDialog)
        SwipeSensitivityDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(setting.title) },
            leftKey = setting.key,
            rightKey = rightKey,
            leftDefault = leftDefault,
            rightDefault = rightDefault,
            prefs = prefs,
        )
}

// like KeyboardScaleDialog, but for the two Int swipe-sensitivity settings: one slider per swipe
// direction, written only when the dialog is confirmed
@Composable
private fun SwipeSensitivityDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    leftKey: String,
    rightKey: String,
    leftDefault: Int,
    rightDefault: Int,
    prefs: SharedPreferences,
) {
    val done = remember { mutableMapOf<String, () -> Unit>() }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { done.values.forEach { it() } },
        title = title,
        content = {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyLarge
            ) {
                val state = rememberScrollState()
                Column(Modifier.verticalScroll(state)) {
                    DirectionSlider(stringResource(R.string.left), leftKey, leftDefault, prefs, done)
                    DirectionSlider(stringResource(R.string.right), rightKey, rightDefault, prefs, done)
                }
            }
        },
    )
}

@Composable
private fun DirectionSlider(
    label: String,
    key: String,
    default: Int,
    prefs: SharedPreferences,
    done: MutableMap<String, () -> Unit>,
) {
    var position by remember { mutableFloatStateOf(SwipeSensitivity.read(prefs, key, default).toFloat()) }
    done[key] = {
        if (position.toInt() == default) prefs.edit { remove(key) }
        else prefs.edit { putInt(key, position.toInt()) }
    }
    WithSmallTitle(label) {
        Slider(
            value = position,
            onValueChange = { position = it },
            valueRange = SwipeSensitivity.MIN.toFloat()..SwipeSensitivity.MAX.toFloat(),
            steps = (SwipeSensitivity.MAX - SwipeSensitivity.MIN) / STEP - 1,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${position.toInt()}%")
            TextButton({ position = default.toFloat() }) { Text(stringResource(R.string.button_default)) }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// percent between slider positions, as in the single-slider predecessor
private const val STEP = 5
