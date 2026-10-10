// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import helium314.keyboard.latin.AppsManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.LearningContext
import helium314.keyboard.latin.settings.LearningContexts
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.TextInputDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lists the learning contexts. The default context is always first, followed by user-created contexts. */
@Composable
fun LearningContextsScreen(onClickBack: () -> Unit) {
    val prefs = LocalContext.current.prefs()
    var contexts by remember { mutableStateOf(LearningContexts.getAll(prefs)) }
    var showAddDialog by remember { mutableStateOf(false) }
    // items: null is the "add" row, the default context is represented by a context with empty id
    val defaultContext = LearningContext(LearningContexts.DEFAULT, stringResource(R.string.dwell_learning_context_default))
    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(stringResource(R.string.dwell_learning_contexts_title)) },
        filteredItems = { term ->
            if (term.isBlank()) listOf(null, defaultContext) + contexts
            else contexts.filter { it.name.contains(term, true) }
        },
        itemContent = { item ->
            if (item == null) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.dwell_learning_contexts_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddDialog = true }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(stringResource(R.string.dwell_learning_context_add))
                        Icon(painterResource(R.drawable.ic_plus), stringResource(R.string.dwell_learning_context_add))
                    }
                }
            } else {
                val isDefault = item.id == LearningContexts.DEFAULT
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isDefault) Modifier
                            else Modifier.clickable { SettingsDestination.navigateTo(SettingsDestination.LearningContext + item.id) }
                        )
                        .padding(vertical = 6.dp, horizontal = 16.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name)
                        Text(
                            if (isDefault) stringResource(R.string.dwell_learning_context_default_summary)
                            else pluralStringResource(R.plurals.dwell_learning_context_app_count, item.packages.size, item.packages.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isDefault) NextScreenIcon()
                }
            }
        }
    )
    if (showAddDialog) {
        TextInputDialog(
            onDismissRequest = { showAddDialog = false },
            onConfirmed = {
                val new = LearningContexts.create(prefs, it)
                contexts = LearningContexts.getAll(prefs)
                SettingsDestination.navigateTo(SettingsDestination.LearningContext + new.id)
            },
            title = { Text(stringResource(R.string.dwell_learning_context_add)) },
            textInputLabel = { Text(stringResource(R.string.dwell_learning_context_name)) },
        )
    }
}

private data class AppInfo(val packageName: String, val name: String, val icon: android.graphics.drawable.Drawable?)

/** Allows renaming / deleting the context with the given [id], and selecting which apps use it. */
@Composable
fun LearningContextScreen(id: String, onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    var context by remember { mutableStateOf(LearningContexts.get(prefs, id)) }
    var apps by remember { mutableStateOf(emptyList<AppInfo>()) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            AppsManager(ctx).getPackagesWithNameAndIcon()
                .map { AppInfo(it.first, it.second, it.third) }
                .sortedBy { it.name.lowercase() }
        }
    }
    val current = context
    if (current == null) { // deleted (or invalid id)
        LaunchedEffect(Unit) { onClickBack() }
        return
    }
    val otherContexts = LearningContexts.getAll(prefs).filter { it.id != id }
    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(current.name) },
        menu = listOf(
            stringResource(R.string.dwell_learning_context_rename) to { showRenameDialog = true },
            stringResource(R.string.dwell_learning_context_delete) to { showDeleteDialog = true },
        ),
        filteredItems = { term ->
            // null is the header
            if (term.isBlank()) listOf<AppInfo?>(null) + apps
            else apps.filter { it.name.contains(term, true) || it.packageName.contains(term, true) }
        },
        itemContent = { app ->
            if (app == null) {
                Text(
                    pluralStringResource(R.plurals.dwell_learning_context_app_count, current.packages.size, current.packages.size),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val selected = app.packageName in current.packages
                val otherContext = otherContexts.firstOrNull { app.packageName in it.packages }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val packages = if (selected) current.packages - app.packageName else current.packages + app.packageName
                            LearningContexts.setPackages(prefs, id, packages)
                            context = LearningContexts.get(prefs, id)
                        }
                        .padding(vertical = 6.dp, horizontal = 16.dp)
                ) {
                    Box(Modifier.size(32.dp)) {
                        if (app.icon != null) {
                            val px = 32.dpToPx(LocalResources.current)
                            Image(app.icon.toBitmap(px, px).asImageBitmap(), app.name)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(app.name)
                        Text(
                            if (otherContext != null && !selected) stringResource(R.string.dwell_learning_context_app_in_other, otherContext.name)
                            else app.packageName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = selected, onCheckedChange = null)
                }
            }
        }
    )
    if (showRenameDialog) {
        TextInputDialog(
            onDismissRequest = { showRenameDialog = false },
            onConfirmed = {
                LearningContexts.rename(prefs, id, it)
                context = LearningContexts.get(prefs, id)
            },
            title = { Text(stringResource(R.string.dwell_learning_context_rename)) },
            textInputLabel = { Text(stringResource(R.string.dwell_learning_context_name)) },
            initialText = current.name,
        )
    }
    if (showDeleteDialog) {
        ConfirmationDialog(
            onDismissRequest = { showDeleteDialog = false },
            onConfirmed = {
                LearningContexts.delete(ctx, id)
                context = null
            },
            title = { Text(stringResource(R.string.dwell_learning_context_delete)) },
            content = { Text(stringResource(R.string.dwell_learning_context_delete_message, current.name)) },
        )
    }
}
