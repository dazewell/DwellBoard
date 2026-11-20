// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learnedwords

import android.content.SharedPreferences
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.LearningContext
import helium314.keyboard.latin.settings.LearningContexts
import helium314.keyboard.latin.utils.BackButton
import helium314.keyboard.latin.utils.CloseIcon
import helium314.keyboard.latin.utils.SearchIcon

@Composable
internal fun rememberLearningContexts(prefs: SharedPreferences): List<LearningContext> {
    val contexts = remember(prefs) { mutableStateOf(LearningContexts.getAll(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == LearningContexts.PREF_LEARNING_CONTEXTS) {
                contexts.value = LearningContexts.getAll(prefs)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        contexts.value = LearningContexts.getAll(prefs)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return contexts.value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContextScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(onBack) }, actions = actions) },
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
internal fun ContextSearch(query: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = { SearchIcon() },
        trailingIcon = if (query.isEmpty()) null else {
            { IconButton(onClick = { onChange("") }) { CloseIcon(R.string.dwell_context_clear_search) } }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}
