// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learnedwords

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.settings.LearningContexts
import helium314.keyboard.latin.utils.CloseIcon
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import kotlinx.coroutines.CancellationException
import java.util.Locale

private val alphabetically = compareBy<LearnedWord, String>(String.CASE_INSENSITIVE_ORDER) { it.word }
    .thenBy { it.word }
private val byUsage = compareByDescending<LearnedWord> { it.probability }.thenByDescending { it.count }

private data class VisibleFamily(val family: WordFamily, val matches: List<LearnedWord>) {
    val isGroup get() = family.members.size > 1
    val key get() = family.anchor.key.listKey
    val bestMatch get() = matches.first()
}

private data class LanguageWords(val locale: Locale, val families: List<VisibleFamily>)

@Composable
fun LearnedWordsScreen(id: String, onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val resources = LocalResources.current
    val prefs = ctx.prefs()
    val contexts = rememberLearningContexts(prefs)
    val isDefault = id == LearningContexts.DEFAULT
    val contextName = if (isDefault) stringResource(R.string.dwell_learning_context_default)
        else contexts.firstOrNull { it.id == id }?.name
    if (contextName == null) {
        LaunchedEffect(id) {
            Log.i("LearningContexts", "DWELL_SMOKE_learning-contexts-redesign FORBIDDEN context_missing")
            onClickBack()
        }
        return
    }

    var query by rememberSaveable(id) { mutableStateOf("") }
    var frequentFirst by rememberSaveable(id) { mutableStateOf(false) }
    var allWords by rememberSaveable(id) { mutableStateOf(false) }
    var related by rememberSaveable(id) { mutableStateOf(true) }
    var expanded by remember(id, query) { mutableStateOf(emptyMap<String, Boolean>()) }
    var selected by remember(id) { mutableStateOf(emptySet<WordKey>()) }

    var retry by remember(id) { mutableIntStateOf(0) }
    var words by remember(id) { mutableStateOf<List<LearnedWord>?>(null) }
    var readFailed by remember(id) { mutableStateOf(false) }
    var reading by remember(id) { mutableStateOf(true) }
    var deletePrompt by remember(id) { mutableStateOf<Set<WordKey>?>(null) }
    var pendingDelete by remember(id) { mutableStateOf<List<LearnedWord>?>(null) }
    var attempted by remember(id) { mutableStateOf(emptySet<WordKey>()) }
    var deleteFailed by remember(id) { mutableStateOf(false) }
    var saveFailed by remember(id) { mutableStateOf(false) }
    val deleting = pendingDelete != null
    val busy = reading || deleting
    LaunchedEffect(id, retry) {
        val batch = pendingDelete
        if (batch != null) {
            // A removed context must not get its dictionaries created by a stale confirmation.
            if (!isDefault && LearningContexts.get(prefs, id) == null) {
                onClickBack()
                return@LaunchedEffect
            }
            attempted = batch.mapTo(HashSet()) { it.key }
            saveFailed = try {
                !LearnedWords.delete(ctx, id, batch)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e("LearnedWords", "cannot delete learned words", exception)
                true
            }
        }
        val loaded = try {
            LearnedWords.load(ctx, id)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e("LearnedWords", "cannot load learned words", exception)
            null
        }
        if (loaded != null) {
            val keys = loaded.mapTo(HashSet()) { it.key }
            val remaining = attempted intersect keys
            selected = (selected intersect keys) + remaining
            deleteFailed = remaining.isNotEmpty()
            attempted = emptySet()
            words = loaded
        }
        Log.i("LearningContexts", "DWELL_SMOKE_learning-contexts-redesign END words_read=${loaded != null}")
        readFailed = loaded == null
        reading = false
        if (batch != null) pendingDelete = null
    }

    val knownWords = words
    val wordsByKey = remember(knownWords) { knownWords.orEmpty().associateBy { it.key } }
    val families = remember(knownWords, allWords, related) {
        knownWords.orEmpty().filter { allWords || !it.inDictionary }.groupBy { it.locale }.mapValues { (_, entries) ->
            if (related) relatedWordFamilies(entries) else entries.map { WordFamily(it, listOf(it)) }
        }
    }
    val term = query.trim()
    val results = remember(families, term, frequentFirst, resources) {
        val memberOrder = if (frequentFirst) byUsage.then(alphabetically) else alphabetically
        families.mapNotNull { (locale, groups) ->
            val matching = groups.mapNotNull { family ->
                val matches = family.members.filter { it.word.contains(term, ignoreCase = true) }
                if (matches.isEmpty()) null else VisibleFamily(family, matches.sortedWith(memberOrder))
            }
            val order = if (frequentFirst)
                compareBy<VisibleFamily, LearnedWord>(byUsage) { it.bestMatch }
                    .thenBy(alphabetically) { it.family.anchor }
            else compareBy(alphabetically) { group: VisibleFamily -> group.family.anchor }
            if (matching.isEmpty()) null else LanguageWords(locale, matching.sortedWith(order))
        }.sortedWith(compareBy<LanguageWords> { it.locale.localizedDisplayName(resources) }.thenBy { it.locale.toLanguageTag() })
    }
    val resultKeys = remember(results) {
        results.flatMap { it.families }.flatMap { it.matches }.mapTo(HashSet()) { it.key }
    }
    fun isExpanded(family: VisibleFamily) = expanded[family.key] ?: term.isNotEmpty()
    fun memberRows(family: VisibleFamily): List<LearnedWord> =
        if (!family.isGroup || isExpanded(family)) family.matches else emptyList()

    val dragRows = remember(results, expanded, term) {
        results.flatMap { section -> section.families.flatMap(::memberRows) }.map { it.key }
    }
    val list = rememberLazyListState()
    val canDelete = selected.isNotEmpty() && !busy && !readFailed
    val menuLabel = stringResource(R.string.dwell_learned_words_selection_menu)

    fun goBack() {
        if (deleting) return
        if (selected.isNotEmpty()) selected = emptySet()
        else onClickBack()
    }
    BackHandler(enabled = selected.isNotEmpty() || deleting) { goBack() }

    ContextScaffold(
        title = stringResource(R.string.dwell_learned_words_title),
        onBack = ::goBack,
        actions = {
            WordActions(
                menuLabel,
                listOf(
                    WordAction(stringResource(R.string.dwell_learned_words_select_all), resultKeys.isNotEmpty() && !busy && !readFailed) {
                        selected = selected + resultKeys
                    },
                    WordAction(stringResource(R.string.dwell_learned_words_select_none), selected.isNotEmpty() && !deleting) {
                        selected = emptySet()
                    },
                    WordAction(stringResource(R.string.dwell_learned_words_delete_selected), canDelete) { deletePrompt = selected },
                ),
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) SelectionBar(
                count = selected.size,
                hidden = selected.count { it !in resultKeys },
                canDelete = canDelete,
                canClear = !deleting,
                onClear = { selected = emptySet() },
                onDelete = { deletePrompt = selected },
            )
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
            Text(
                contextName, Modifier.padding(horizontal = 16.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            ContextSearch(query, { query = it }, stringResource(R.string.dwell_learned_words_search))
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WordViewChoice(
                    R.string.dwell_learned_words_type, R.string.dwell_learned_words_new, R.string.dwell_learned_words_all,
                    allWords, { allWords = it },
                )
                WordViewChoice(
                    R.string.dwell_learned_words_order, R.string.dwell_learned_words_alphabetical, R.string.dwell_learned_words_frequency,
                    frequentFirst, { frequentFirst = it },
                )
                WordViewChoice(
                    R.string.dwell_learned_words_grouping, R.string.dwell_learned_words_individual, R.string.dwell_learned_words_related,
                    related, { related = it },
                )
            }
            if (deleting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            val rangeModifier = if (busy || readFailed || dragRows.isEmpty()) Modifier
                else wordRangeDrag(list, dragRows, selected) { selected = it }
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().then(rangeModifier), state = list) {
                when {
                    readFailed -> item {
                        WordStatus(stringResource(R.string.dwell_learned_words_failed), R.string.dwell_context_retry) {
                            readFailed = false
                            reading = true
                            retry++
                        }
                    }
                    reading || knownWords == null -> item { WordStatus(stringResource(R.string.dwell_learned_words_loading)) }
                    knownWords.isEmpty() -> item { WordStatus(stringResource(R.string.dwell_learned_words_empty, contextName)) }
                    families.isEmpty() -> item {
                        WordStatus(stringResource(R.string.dwell_learned_words_none_new, contextName),
                            R.string.dwell_learned_words_show_dictionary) { allWords = true }
                    }
                    results.isEmpty() -> item {
                        WordStatus(stringResource(R.string.dwell_learned_words_no_matches), R.string.dwell_context_clear_search) { query = "" }
                    }
                }
                if (saveFailed && !readFailed && !reading) item(key = "save-failed") {
                    WordStatus(stringResource(R.string.dwell_learned_words_save_failed))
                }
                if (deleteFailed && !readFailed && !reading) item(key = "delete-failed") {
                    WordStatus(stringResource(R.string.dwell_learned_words_delete_failed))
                }
                if (!readFailed && !reading) results.forEach { section ->
                    languageHeading(section.locale)
                    section.families.forEach { family ->
                        if (family.isGroup) item(key = "group:${family.key}") {
                            WordFamilyRow(
                                family, isExpanded(family), family.matches.count { it.key in selected }, deleting,
                                onToggle = { expanded = expanded + (family.key to !isExpanded(family)) },
                                onSelect = {
                                    expanded = expanded + (family.key to true)
                                    selected = selected + family.matches.map { it.key }
                                },
                            )
                        }
                        memberRows(family).forEach { word ->
                            item(key = word.key.listKey) {
                                val checked = word.key in selected
                                WordRow(word, checked, family.isGroup, deleting, {
                                    selected = if (checked) selected - word.key else selected + word.key
                                }) { deletePrompt = setOf(word.key) }
                            }
                        }
                    }
                }
            }
        }
    }

    deletePrompt?.let { keys ->
        val exactWords = keys.mapNotNull { wordsByKey[it] }.sortedWith(
            compareBy<LearnedWord> { it.locale.localizedDisplayName(resources) }.then(alphabetically),
        )
        DeleteWordsDialog(
            contextName, exactWords,
            onDismiss = { deletePrompt = null },
            onDelete = { pendingDelete = exactWords; retry++ },
        )
    }
}

@Composable
private fun WordStatus(message: String, action: Int? = null, onAction: () -> Unit = {}) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) TextButton(onClick = onAction) { Text(stringResource(action)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.languageHeading(locale: Locale) {
    stickyHeader(key = "language:${locale.toLanguageTag()}") {
        Text(
            locale.localizedDisplayName(LocalResources.current),
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SelectionBar(count: Int, hidden: Int, canDelete: Boolean, canClear: Boolean, onClear: () -> Unit, onDelete: () -> Unit) {
    val selected = pluralStringResource(R.plurals.dwell_learned_words_selected, count, count)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onClear, enabled = canClear) { CloseIcon(R.string.dwell_learned_words_select_none) }
            Text(
                if (hidden == 0) selected else stringResource(R.string.dwell_learned_words_selection_hidden, selected, hidden),
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelLarge,
            )
            FilledTonalButton(
                onClick = onDelete, enabled = canDelete,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) { Text(stringResource(R.string.delete)) }
        }
    }
}

@Composable
private fun WordViewChoice(description: Int, first: Int, second: Int, secondSelected: Boolean, onChange: (Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(description)
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(stringResource(if (secondSelected) second else first)) },
            trailingIcon = { Icon(painterResource(R.drawable.ic_dpad_down), null) },
            modifier = Modifier.semantics {
                stateDescription = label
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(label, Modifier.padding(12.dp).semantics { heading() }, style = MaterialTheme.typography.labelMedium)
            DropdownMenuItem(text = { Text(stringResource(first)) }, onClick = { open = false; onChange(false) })
            DropdownMenuItem(text = { Text(stringResource(second)) }, onClick = { open = false; onChange(true) })
        }
    }
}

private data class WordAction(val label: String, val enabled: Boolean = true, val action: () -> Unit)

@Composable
private fun WordActions(description: String, actions: List<WordAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = actions.any { it.enabled }) {
            Icon(painterResource(R.drawable.ic_dwell_more_vert), description)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    enabled = action.enabled,
                    onClick = { open = false; action.action() },
                )
            }
        }
    }
}

@Composable
private fun WordFamilyRow(
    family: VisibleFamily, expanded: Boolean, selected: Int, deleting: Boolean,
    onToggle: () -> Unit, onSelect: () -> Unit,
) {
    val word = family.family.anchor.word
    val count = family.matches.size
    val select = pluralStringResource(
        if (count == family.family.members.size) R.plurals.dwell_learned_words_select_forms
        else R.plurals.dwell_learned_words_select_matching_forms,
        count, count,
    )
    val toggle = stringResource(if (expanded) R.string.dwell_learned_words_collapse_forms else R.string.dwell_learned_words_expand_forms)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClickLabel = toggle, onClick = onToggle)
                .semantics {
                    customActions = if (deleting) emptyList() else listOf(CustomAccessibilityAction(select) { onSelect(); true })
                }.padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_dpad_down), null, Modifier.rotate(if (expanded) 180f else 0f))
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(word, style = MaterialTheme.typography.bodyLarge)
                val forms = if (count == family.family.members.size)
                    pluralStringResource(R.plurals.dwell_learned_words_forms, count, count)
                    else pluralStringResource(R.plurals.dwell_learned_words_matching_forms, count, count)
                Text(forms, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (selected != 0) Text(
                    pluralStringResource(R.plurals.dwell_learned_words_selected, selected, selected),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        WordActions(stringResource(R.string.dwell_learned_words_row_actions, word), listOf(WordAction(select, !deleting, onSelect)))
    }
}

@Composable
private fun WordRow(word: LearnedWord, checked: Boolean, indented: Boolean, deleting: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    val select = stringResource(if (checked) R.string.dwell_learned_words_deselect else R.string.dwell_learned_words_select)
    val delete = stringResource(R.string.dwell_learned_words_delete_word)
    Row(
        Modifier.fillMaxWidth()
            .background(if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .padding(start = if (indented) 36.dp else 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp)
                .toggleable(checked, enabled = !deleting, role = Role.Checkbox) { onToggle() }
                .semantics {
                    customActions = if (deleting) emptyList() else listOf(CustomAccessibilityAction(delete) { onDelete(); true })
                }.padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked, onCheckedChange = null, enabled = !deleting)
            Text(word.word, Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodyLarge)
        }
        WordActions(
            stringResource(R.string.dwell_learned_words_row_actions, word.word),
            listOf(WordAction(select, !deleting, onToggle), WordAction(delete, !deleting, onDelete)),
        )
    }
}

@Composable
private fun DeleteWordsDialog(contextName: String, words: List<LearnedWord>, onDismiss: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    ThreeButtonAlertDialog(
        onDismissRequest = onDismiss,
        onConfirmed = onDelete,
        confirmButtonText = stringResource(R.string.delete),
        checkOk = { words.isNotEmpty() },
        title = { Text(stringResource(R.string.dwell_learned_words_delete)) },
        content = {
            Column {
                Text(pluralStringResource(R.plurals.dwell_learned_words_delete_message, words.size, words.size, contextName))
                TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) R.string.dwell_learned_words_hide_affected else R.string.dwell_learned_words_show_affected))
                }
                if (expanded) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                        words.groupBy { it.locale }.forEach { (locale, entries) ->
                            item {
                                Text(locale.localizedDisplayName(resources), Modifier.padding(vertical = 6.dp).semantics { heading() },
                                    style = MaterialTheme.typography.titleSmall)
                            }
                            items(entries.size, key = { entries[it].key.listKey }) { index ->
                                Text(entries[index].word, Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
                            }
                        }
                    }
                }
            }
        },
        scrollContent = true,
    )
}
