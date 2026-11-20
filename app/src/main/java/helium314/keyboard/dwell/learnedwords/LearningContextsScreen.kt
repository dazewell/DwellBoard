// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learnedwords

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import helium314.keyboard.latin.AppsManager
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.LearningContext
import helium314.keyboard.latin.settings.LearningContexts
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.dialogs.TextInputDialog
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_ROUTE_ID = "default"
internal fun contextRouteId(id: String) = id.ifEmpty { DEFAULT_ROUTE_ID }
internal fun contextIdFromRoute(routeId: String?) =
    if (routeId == null || routeId == DEFAULT_ROUTE_ID) LearningContexts.DEFAULT else routeId

@Composable
private fun defaultContext() =
    LearningContext(LearningContexts.DEFAULT, stringResource(R.string.dwell_learning_context_default))

@Composable
private fun appCount(size: Int) = pluralStringResource(R.plurals.dwell_learning_context_app_count, size, size)

@Composable
fun LearningContextsScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val contexts = listOf(defaultContext()) + rememberLearningContexts(prefs)
    var creating by rememberSaveable { mutableStateOf(false) }
    val copy: ContextCopy = viewModel()
    LaunchedEffect(copy.createdId) {
        copy.createdId?.let { id ->
            copy.consumeResult()
            creating = false
            SettingsDestination.navigateTo(SettingsDestination.LearningContext + contextRouteId(id))
        }
    }
    ContextScaffold(stringResource(R.string.dwell_learning_contexts_title), onClickBack) { insets ->
        LazyColumn(contentPadding = insets, modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    stringResource(R.string.dwell_learning_contexts_description),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(contexts, key = { it.id }) { entry ->
                ContextAction(
                    entry.name,
                    if (entry.id == LearningContexts.DEFAULT) stringResource(R.string.dwell_learning_context_default_summary)
                    else appCount(entry.packages.size),
                ) { SettingsDestination.navigateTo(SettingsDestination.LearningContext + contextRouteId(entry.id)) }
            }
            item {
                Button(onClick = { creating = true }, Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.dwell_learning_context_add))
                }
            }
        }
    }
    if (creating) CreateContextDialog(contexts, copy, { creating = false }) { id ->
        creating = false
        SettingsDestination.navigateTo(SettingsDestination.LearningContext + contextRouteId(id))
    }
}

internal class ContextCopy : ViewModel() {
    var copying by mutableStateOf(false)
        private set
    var createdId by mutableStateOf<String?>(null)
        private set

    fun start(context: Context, name: String, sourceId: String) {
        if (copying) return
        copying = true
        viewModelScope.launch {
            createdId = LearningContexts.createFrom(context.applicationContext, name, sourceId).id
        }
    }

    fun consumeResult() {
        createdId = null
        copying = false
    }
}

@Composable
private fun CreateContextDialog(
    contexts: List<LearningContext>, copy: ContextCopy, onDismiss: () -> Unit, onCreated: (String) -> Unit,
) {
    val ctx = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var sourceId by rememberSaveable { mutableStateOf<String?>(null) }
    val source = contexts.firstOrNull { it.id == sourceId }
    var choosingSource by remember { mutableStateOf(false) }
    val copying = copy.copying
    val empty = stringResource(R.string.dwell_learning_context_start_empty)
    ThreeButtonAlertDialog(
        onDismissRequest = { if (!copy.copying) onDismiss() },
        onConfirmed = {
            if (source == null) onCreated(LearningContexts.create(ctx.prefs(), name).id)
            else copy.start(ctx, name, source.id)
        },
        checkOk = { name.isNotBlank() && !copying },
        title = { Text(stringResource(R.string.dwell_learning_context_add)) },
        confirmButtonText = stringResource(R.string.dwell_learning_context_create),
        scrollContent = true,
        properties = DialogProperties(dismissOnBackPress = !copying, dismissOnClickOutside = !copying),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    name, { name = it }, Modifier.fillMaxWidth(), enabled = !copying, singleLine = true,
                    label = { Text(stringResource(R.string.dwell_learning_context_name)) },
                )
                Text(stringResource(R.string.dwell_learning_context_start_from))
                Column {
                    OutlinedButton(onClick = { choosingSource = true }, enabled = !copying) {
                        Text(source?.let { stringResource(R.string.dwell_learning_context_copy_of, it.name) } ?: empty)
                    }
                    DropdownMenu(choosingSource, { choosingSource = false }) {
                        DropdownMenuItem(text = { Text(empty) }, onClick = { sourceId = null; choosingSource = false })
                        contexts.forEach {
                            DropdownMenuItem(text = { Text(it.name) }, onClick = { sourceId = it.id; choosingSource = false })
                        }
                    }
                }
                if (source != null) Text(stringResource(R.string.dwell_learning_context_copy_summary))
                if (copying) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.dwell_learning_context_copying))
                }
            }
        },
    )
}

@Composable
fun LearningContextScreen(id: String, onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val contexts = rememberLearningContexts(prefs)
    val isDefault = id == LearningContexts.DEFAULT
    val current = if (isDefault) defaultContext() else contexts.firstOrNull { it.id == id }
    if (current == null) {
        LaunchedEffect(id) { onClickBack() }
        return
    }
    LaunchedEffect(id) {
        Log.i("LearningContexts", "DWELL_SMOKE_learning-contexts-redesign BEGIN home_to_words ${BuildConfig.VERSION_NAME} ${BuildConfig.APPLICATION_ID} default=$isDefault")
    }
    var picker by rememberSaveable(id) { mutableStateOf(false) }
    var action by rememberSaveable(id) { mutableStateOf<String?>(null) }
    if (picker) {
        BackHandler { picker = false }
        ContextAppsScreen(current, contexts) { picker = false }
        return
    }
    val preview = if (current.packages.isEmpty()) null else rememberContextApps(current.packages)
    ContextScaffold(stringResource(R.string.dwell_learning_contexts_title), onClickBack) { insets ->
        LazyColumn(contentPadding = insets, modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(current.name, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                    if (!isDefault) TextButton(onClick = { action = "rename" }) {
                        Text(stringResource(R.string.dwell_learning_context_rename_short))
                    }
                }
            }
            item {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.dwell_learning_context_apps),
                                Modifier.weight(1f).semantics { heading() },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (!isDefault) Text(appCount(current.packages.size), style = MaterialTheme.typography.labelLarge)
                        }
                        when {
                            isDefault -> Text(stringResource(R.string.dwell_learning_context_default_summary))
                            current.packages.isEmpty() -> Text(stringResource(R.string.dwell_learning_context_no_apps))
                            preview?.loading == true -> Text(stringResource(R.string.dwell_learning_context_apps_loading))
                            else -> {
                                preview?.apps.orEmpty().take(PREVIEW_SIZE).forEach { app ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AppIcon(app)
                                        Text(app.name)
                                    }
                                }
                                val extra = current.packages.size - PREVIEW_SIZE
                                if (extra > 0) Text(pluralStringResource(R.plurals.dwell_learning_context_more_apps, extra, extra))
                            }
                        }
                        if (!isDefault) {
                            if (current.packages.isEmpty()) Button(onClick = { picker = true }) {
                                Text(stringResource(R.string.dwell_learning_context_choose_apps))
                            } else TextButton(onClick = { picker = true }) {
                                Text(stringResource(R.string.dwell_learning_context_manage_apps))
                            }
                        }
                    }
                }
            }
            item {
                ContextAction(
                    stringResource(R.string.dwell_learned_words_title),
                    stringResource(R.string.dwell_learned_words_summary),
                ) {
                    Log.i("LearningContexts", "DWELL_SMOKE_learning-contexts-redesign PATH open_words preview_loading=${preview?.loading == true}")
                    SettingsDestination.navigateTo(SettingsDestination.LearnedWords + contextRouteId(id))
                }
                ContextAction(
                    stringResource(R.string.dwell_learning_context_clear),
                    stringResource(R.string.dwell_learning_context_clear_summary, current.name),
                ) { action = "clear" }
                if (!isDefault) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    TextButton(onClick = { action = "delete" }, Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(stringResource(R.string.dwell_learning_context_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    when (action) {
        "rename" -> if (!isDefault) TextInputDialog(
            onDismissRequest = { action = null },
            onConfirmed = { if (it.isNotBlank()) LearningContexts.rename(prefs, id, it) },
            title = { Text(stringResource(R.string.dwell_learning_context_rename)) },
            textInputLabel = { Text(stringResource(R.string.dwell_learning_context_name)) },
            initialText = current.name,
        )
        "clear" -> ClearLearnedDataDialog(id, current.name) { action = null }
        "delete" -> if (!isDefault) ThreeButtonAlertDialog(
            onDismissRequest = { action = null },
            onConfirmed = { LearningContexts.delete(ctx, id) },
            confirmButtonText = stringResource(R.string.delete),
            title = { Text(stringResource(R.string.dwell_learning_context_delete)) },
            content = { Text(stringResource(R.string.dwell_learning_context_delete_message, current.name)) },
            scrollContent = true,
        )
    }
}

@Composable
private fun ContextAction(title: String, summary: String, onClick: () -> Unit) {
    Surface(onClick = onClick) {
        ListItem(headlineContent = { Text(title) }, supportingContent = { Text(summary) }, trailingContent = { NextScreenIcon() })
    }
}

private const val PREVIEW_SIZE = 3
private data class ContextApp(val packageName: String, val name: String = packageName, val icon: ImageBitmap? = null)
private data class AppLoad(val apps: List<ContextApp> = emptyList(), val loading: Boolean = true, val failed: Boolean = false)

@Composable
private fun rememberContextApps(preview: Set<String>? = null, retry: Int = 0): AppLoad {
    val ctx = LocalContext.current
    val iconSize = with(LocalDensity.current) { 32.dp.roundToPx() }
    var result by remember(preview, retry, iconSize) { mutableStateOf(AppLoad()) }
    LaunchedEffect(ctx, preview, retry, iconSize) {
        result = withContext(Dispatchers.IO) {
            try {
                val apps = if (preview == null) {
                    AppsManager(ctx).getPackagesWithNameAndIcon().map { (pkg, name, icon) ->
                        ContextApp(pkg, name, icon?.toBitmap(iconSize, iconSize)?.asImageBitmap())
                    }
                } else {
                    val pm = ctx.packageManager
                    preview.sorted().take(PREVIEW_SIZE).map { pkg ->
                        val info = try {
                            @Suppress("DEPRECATION")
                            pm.getApplicationInfo(pkg, 0)
                        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                            null
                        }
                        if (info == null) ContextApp(pkg)
                        else ContextApp(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info)
                            .toBitmap(iconSize, iconSize).asImageBitmap())
                    }
                }
                AppLoad(apps.sortedBy { it.name.lowercase() }, loading = false)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                AppLoad(preview.orEmpty().sorted().take(PREVIEW_SIZE).map { ContextApp(it) }, loading = false, failed = true)
            }
        }
    }
    return result
}

@Composable
private fun AppIcon(app: ContextApp) {
    app.icon?.let { Image(it, null, Modifier.size(32.dp)) }
}

private data class AppMove(val app: ContextApp, val owner: LearningContext)

@Composable
private fun ContextAppsScreen(current: LearningContext, contexts: List<LearningContext>, onBack: () -> Unit) {
    val prefs = LocalContext.current.prefs()
    var query by rememberSaveable { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var move by remember { mutableStateOf<AppMove?>(null) }
    val loaded = rememberContextApps(retry = retry)
    val id = current.id
    val term = query.trim()
    // Take a new section snapshot only on search changes. Switches and ownership text stay live.
    var sectionPackages by rememberSaveable { mutableStateOf(ArrayList(current.packages)) }
    val sections = remember(loaded.apps, sectionPackages, term) {
        val known = loaded.apps.associateBy { it.packageName }
        val all = loaded.apps + sectionPackages.filterNot(known::containsKey).map { ContextApp(it) }
        val matching = all.filter { it.name.contains(term, true) || it.packageName.contains(term, true) }
        val assignedKeys = sectionPackages.toHashSet()
        matching.partition { it.packageName in assignedKeys }
    }
    val duplicatedNames = remember(loaded.apps) {
        loaded.apps.groupingBy { it.name.lowercase() }.eachCount().filterValues { it > 1 }.keys
    }

    fun changeAssignment(app: ContextApp, confirmedOwner: String? = null) {
        val latest = LearningContexts.getAll(prefs)
        val target = latest.firstOrNull { it.id == id } ?: return
        val pkg = app.packageName
        if (pkg in target.packages) {
            if (confirmedOwner == null) LearningContexts.setPackages(prefs, id, target.packages - pkg)
            return
        }
        val owner = latest.firstOrNull { pkg in it.packages }
        if (owner != null && owner.id != confirmedOwner) move = AppMove(app, owner)
        else LearningContexts.setPackages(prefs, id, target.packages + pkg)
    }

    ContextScaffold(stringResource(R.string.dwell_learning_context_manage_apps), onBack) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Text(
                current.name, Modifier.padding(horizontal = 16.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            ContextSearch(query, { query = it; sectionPackages = ArrayList(current.packages) },
                stringResource(R.string.dwell_learning_context_search_apps))
            Text(appCount(current.packages.size), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            LazyColumn(Modifier.weight(1f)) {
                if (loaded.loading) item { Text(stringResource(R.string.dwell_learning_context_apps_loading), Modifier.padding(16.dp)) }
                if (loaded.failed) item {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.dwell_learning_context_apps_failed))
                        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.dwell_context_retry)) }
                    }
                }
                if (sections.first.isEmpty() && sections.second.isEmpty() && !loaded.loading && !loaded.failed) item {
                    Text(
                        stringResource(if (term.isEmpty()) R.string.dwell_learning_context_no_available_apps
                            else R.string.dwell_learning_context_no_matching_apps),
                        Modifier.padding(16.dp),
                    )
                }
                listOf(R.string.dwell_learning_context_assigned to sections.first,
                    R.string.dwell_learning_context_other_apps to sections.second).forEach { (headingId, apps) ->
                    if (apps.isNotEmpty()) {
                        item(key = headingId) {
                            Text(
                                stringResource(headingId), Modifier.padding(16.dp).semantics { heading() },
                                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(apps, key = { it.packageName }) { app ->
                            val selected = app.packageName in current.packages
                            val owner = contexts.firstOrNull { it.id != id && app.packageName in it.packages }
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .toggleable(selected, role = Role.Switch) { changeAssignment(app) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppIcon(app)
                                Column(Modifier.weight(1f)) {
                                    Text(app.name, style = MaterialTheme.typography.bodyLarge)
                                    if (app.name != app.packageName && (term.isNotEmpty() || app.name.lowercase() in duplicatedNames)) {
                                        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (owner != null && !selected) Text(
                                        stringResource(R.string.dwell_learning_context_app_in_other, owner.name),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = selected, onCheckedChange = null)
                            }
                        }
                    }
                }
            }
        }
    }
    move?.let { pending ->
        // Clear before handling confirmation so a newly discovered owner can get its own prompt.
        ThreeButtonAlertDialog(
            onDismissRequest = { if (move == pending) move = null },
            onConfirmed = { move = null; changeAssignment(pending.app, pending.owner.id) },
            confirmButtonText = stringResource(R.string.dwell_learning_context_move),
            title = { Text(stringResource(R.string.dwell_learning_context_move)) },
            content = { Text(stringResource(R.string.dwell_learning_context_move_message, pending.app.name, pending.owner.name, current.name)) },
            scrollContent = true,
        )
    }
}

@Composable
private fun ClearLearnedDataDialog(id: String, name: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var clearWords by rememberSaveable { mutableStateOf(true) }
    var clearEmojis by rememberSaveable { mutableStateOf(true) }
    ThreeButtonAlertDialog(
        onDismissRequest = onDone,
        onConfirmed = { LearningContexts.clearData(ctx, id, clearWords, clearEmojis) },
        checkOk = { clearWords || clearEmojis },
        confirmButtonText = stringResource(R.string.dwell_learning_context_clear_action),
        title = { Text(stringResource(R.string.dwell_learning_context_clear)) },
        scrollContent = true,
        content = {
            Column {
                Text(stringResource(R.string.dwell_learning_context_clear_message, name))
                ClearOption(stringResource(R.string.dwell_learning_context_clear_words), clearWords) { clearWords = it }
                ClearOption(stringResource(R.string.dwell_learning_context_clear_emojis), clearEmojis) { clearEmojis = it }
            }
        },
    )
}

@Composable
private fun ClearOption(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    ) {
        Checkbox(checked, null)
        Text(text, Modifier.padding(start = 8.dp))
    }
}
