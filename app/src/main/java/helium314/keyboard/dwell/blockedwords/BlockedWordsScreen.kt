// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.blockedwords

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import helium314.keyboard.dwell.learnedwords.ContextScaffold
import helium314.keyboard.dwell.learnedwords.ContextSearch
import helium314.keyboard.dwell.learnedwords.SelectionBar
import helium314.keyboard.dwell.learnedwords.WordAction
import helium314.keyboard.dwell.learnedwords.WordActions
import helium314.keyboard.dwell.learnedwords.WordKey
import helium314.keyboard.dwell.learnedwords.WordRow
import helium314.keyboard.dwell.learnedwords.WordStatus
import helium314.keyboard.dwell.learnedwords.languageHeading
import helium314.keyboard.dwell.learnedwords.wordRangeDrag
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import java.util.Locale

private val alphabetically = compareBy<BlockedWord, String>(String.CASE_INSENSITIVE_ORDER) { it.word }.thenBy { it.word }

private data class LanguageWords(val locale: Locale, val words: List<BlockedWord>)

@Composable
fun BlockedWordsScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val resources = LocalResources.current
    var query by rememberSaveable { mutableStateOf("") }
    val state: BlockedWordsState = viewModel(key = "blocked-words")
    var unblockPrompt by remember { mutableStateOf<Set<WordKey>?>(null) }
    LaunchedEffect(Unit) { state.loadOnce(ctx) }

    val selected = state.selected
    val unblocking = state.unblocking
    val readFailed = state.readFailed
    val busy = state.reading || unblocking
    val knownWords = state.words
    val wordsByKey = remember(knownWords) { knownWords.orEmpty().associateBy { it.key } }
    val term = query.trim()
    val results = remember(knownWords, term, resources) {
        knownWords.orEmpty().filter { it.word.contains(term, ignoreCase = true) }.groupBy { it.locale }
            .map { (locale, words) -> LanguageWords(locale, words.sortedWith(alphabetically)) }
            .sortedWith(compareBy<LanguageWords> { it.locale.localizedDisplayName(resources) }.thenBy { it.locale.toLanguageTag() })
    }
    val rows = remember(results) { results.flatMap { section -> section.words.map { it.key } } }
    val resultKeys = remember(rows) { rows.toHashSet() }
    val list = rememberLazyListState()
    val canUnblock = selected.isNotEmpty() && !busy && !readFailed
    val unblockText = stringResource(R.string.dwell_blocked_words_unblock)

    fun goBack() {
        if (unblocking) return
        if (selected.isNotEmpty()) state.selected = emptySet()
        else onClickBack()
    }
    BackHandler(enabled = selected.isNotEmpty() || unblocking) { goBack() }

    ContextScaffold(
        title = stringResource(R.string.dwell_blocked_words_title),
        onBack = ::goBack,
        actions = {
            WordActions(
                stringResource(R.string.dwell_learned_words_selection_menu),
                listOf(
                    WordAction(stringResource(R.string.dwell_learned_words_select_all), resultKeys.isNotEmpty() && !busy && !readFailed) {
                        state.selected = state.selected + resultKeys
                    },
                    WordAction(stringResource(R.string.dwell_learned_words_select_none), selected.isNotEmpty() && !unblocking) {
                        state.selected = emptySet()
                    },
                    WordAction(stringResource(R.string.dwell_blocked_words_unblock_selected), canUnblock) { unblockPrompt = selected },
                ),
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) SelectionBar(
                count = selected.size,
                hidden = selected.count { it !in resultKeys },
                canDelete = canUnblock,
                canClear = !unblocking,
                onClear = { state.selected = emptySet() },
                onDelete = { unblockPrompt = selected },
                actionText = unblockText,
                destructive = false,
            )
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
            ContextSearch(query, { query = it }, stringResource(R.string.dwell_blocked_words_search))
            if (unblocking) LinearProgressIndicator(Modifier.fillMaxWidth())
            val rangeModifier = if (busy || readFailed || rows.isEmpty()) Modifier
                else wordRangeDrag(list, rows, selected) { state.selected = it }
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().then(rangeModifier), state = list) {
                when {
                    readFailed -> item {
                        WordStatus(stringResource(R.string.dwell_blocked_words_failed), R.string.dwell_context_retry) { state.refresh(ctx) }
                    }
                    state.reading || knownWords == null -> item { WordStatus(stringResource(R.string.dwell_blocked_words_loading)) }
                    knownWords.isEmpty() -> item { WordStatus(stringResource(R.string.dwell_blocked_words_empty)) }
                    results.isEmpty() -> item {
                        WordStatus(stringResource(R.string.dwell_learned_words_no_matches), R.string.dwell_context_clear_search) { query = "" }
                    }
                }
                if (state.unblockFailed && !readFailed && !state.reading) item(key = "unblock-failed") {
                    WordStatus(stringResource(R.string.dwell_blocked_words_unblock_failed))
                }
                if (!readFailed && !state.reading) results.forEach { section ->
                    languageHeading(section.locale)
                    section.words.forEach { word ->
                        item(key = word.key.listKey) {
                            WordRow(word.word, word.key in selected, false, unblocking, onToggle = {
                                state.selected = if (word.key in state.selected) state.selected - word.key else state.selected + word.key
                            }, delete = unblockText) { unblockPrompt = setOf(word.key) }
                        }
                    }
                }
            }
        }
    }

    unblockPrompt?.let { keys ->
        val words = keys.mapNotNull { wordsByKey[it] }.sortedWith(
            compareBy<BlockedWord> { it.locale.localizedDisplayName(resources) }.thenBy { it.locale.toLanguageTag() }.then(alphabetically),
        )
        UnblockWordsDialog(words, onDismiss = { unblockPrompt = null }, onUnblock = { state.unblock(ctx, words) })
    }
}

@Composable
private fun UnblockWordsDialog(words: List<BlockedWord>, onDismiss: () -> Unit, onUnblock: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    ThreeButtonAlertDialog(
        onDismissRequest = onDismiss,
        onConfirmed = onUnblock,
        confirmButtonText = stringResource(R.string.dwell_blocked_words_unblock),
        checkOk = { words.isNotEmpty() },
        title = { Text(stringResource(R.string.dwell_blocked_words_unblock_title)) },
        content = {
            Column {
                Text(pluralStringResource(R.plurals.dwell_blocked_words_unblock_message, words.size, words.size))
                TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) R.string.dwell_learned_words_hide_affected else R.string.dwell_learned_words_show_affected))
                }
                if (expanded) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                        words.groupBy { it.locale }.forEach { (locale, entries) ->
                            item(key = "language:${locale.toLanguageTag()}") {
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
