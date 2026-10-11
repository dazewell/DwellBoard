// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import helium314.keyboard.dwell.learnedwords.LearnedWord
import helium314.keyboard.dwell.learnedwords.LearnedWords
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.settings.LearningContexts
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import kotlinx.coroutines.launch
import java.util.Locale

private sealed interface LearnedWordsItem {
    data object Header : LearnedWordsItem
    data class Language(val locale: Locale) : LearnedWordsItem
    data class Word(val word: LearnedWord) : LearnedWordsItem
}

// the default context has an empty id, which can't be a route segment. Context ids are hex, so "default" can't collide.
private const val DEFAULT_ROUTE_ID = "default"
fun learnedWordsRouteId(id: String) = id.ifEmpty { DEFAULT_ROUTE_ID }
fun learnedWordsContextId(routeId: String?) = if (routeId == null || routeId == DEFAULT_ROUTE_ID) LearningContexts.DEFAULT else routeId

/** Lists the learned words of the learning context [id], so unwanted ones can be selected and deleted. */
@Composable
fun LearnedWordsScreen(id: String, onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val resources = LocalResources.current
    val prefs = ctx.prefs()
    val scope = rememberCoroutineScope()
    val isDefault = id == LearningContexts.DEFAULT
    val contextName = if (isDefault) stringResource(R.string.dwell_learning_context_default)
        else LearningContexts.get(prefs, id)?.name
    if (contextName == null) { // context deleted (or invalid id)
        LaunchedEffect(Unit) { onClickBack() }
        return
    }
    var loadCount by remember { mutableIntStateOf(0) } // incremented to reload after deleting
    var words by remember { mutableStateOf<List<LearnedWord>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var byFrequency by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<LearnedWord>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    LaunchedEffect(loadCount) {
        val loaded = LearnedWords.load(ctx, id)
        failed = loaded == null
        words = loaded.orEmpty()
        selected = emptySet()
    }
    val loadedWords = words
    // languages ordered by name, words alphabetically or most frequent first
    val sorted = remember(loadedWords, byFrequency) {
        loadedWords.orEmpty()
            .groupBy { it.locale }
            .toSortedMap(compareBy { it.localizedDisplayName(resources) })
            .mapValues { (_, list) ->
                if (byFrequency) list.sortedWith(compareByDescending<LearnedWord> { it.probability }.thenByDescending { it.count })
                else list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.word })
            }
    }
    val lastTerm = remember { arrayOf("") } // the search term, so "select all" selects what is shown
    fun visible(term: String) = sorted.mapValues { (_, list) -> list.filter { it.word.contains(term, true) } }
    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(stringResource(R.string.dwell_learned_words_title)) },
        menu = listOf(
            stringResource(if (byFrequency) R.string.dwell_learned_words_sort_alphabetical else R.string.dwell_learned_words_sort_frequency) to
                { byFrequency = !byFrequency },
            stringResource(R.string.dwell_learned_words_select_all) to { selected = visible(lastTerm[0]).values.flatten().toSet() },
            stringResource(R.string.dwell_learned_words_select_none) to { selected = emptySet() },
        ),
        filteredItems = { term ->
            lastTerm[0] = term
            val items = mutableListOf<LearnedWordsItem>(LearnedWordsItem.Header)
            visible(term).forEach { (locale, list) ->
                if (list.isEmpty()) return@forEach
                items += LearnedWordsItem.Language(locale)
                list.mapTo(items) { LearnedWordsItem.Word(it) }
            }
            items
        },
        itemContent = { item ->
            when (item) {
                LearnedWordsItem.Header -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    val all = loadedWords
                    Text(
                        when {
                            all == null -> stringResource(R.string.dwell_learned_words_loading)
                            failed -> stringResource(R.string.dwell_learned_words_failed)
                            selected.isNotEmpty() -> pluralStringResource(R.plurals.dwell_learned_words_selected, selected.size, selected.size)
                            all.isEmpty() -> stringResource(R.string.dwell_learned_words_empty, contextName)
                            else -> pluralStringResource(R.plurals.dwell_learned_words_count, all.size, contextName, all.size)
                        },
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selected.isNotEmpty())
                        TextButton({ showDeleteDialog = true }) { Text(stringResource(R.string.delete)) }
                }
                is LearnedWordsItem.Language -> Text(
                    item.locale.localizedDisplayName(resources),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                is LearnedWordsItem.Word -> {
                    val checked = item.word in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(checked, role = Role.Checkbox) {
                                selected = if (it) selected + item.word else selected - item.word
                            }
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                    ) {
                        Checkbox(checked, null)
                        Text(item.word.word, Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    )
    if (showDeleteDialog) {
        val toDelete = selected
        ConfirmationDialog(
            onDismissRequest = { showDeleteDialog = false },
            onConfirmed = {
                // the context may have been deleted elsewhere, deleting words would bring its files back
                if (!isDefault && LearningContexts.get(prefs, id) == null) onClickBack()
                else scope.launch {
                    LearnedWords.delete(ctx, id, toDelete)
                    loadCount++
                }
            },
            title = { Text(stringResource(R.string.dwell_learned_words_delete)) },
            content = {
                Text(pluralStringResource(R.plurals.dwell_learned_words_delete_message, toDelete.size, toDelete.size, contextName))
            },
            confirmButtonText = stringResource(R.string.delete),
        )
    }
}
