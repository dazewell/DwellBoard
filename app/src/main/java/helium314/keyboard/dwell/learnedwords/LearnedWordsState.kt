// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learnedwords

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import helium314.keyboard.latin.settings.LearningContexts
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The navigation entry owns a confirmed deletion until every affected locale has been processed. */
internal class LearnedWordsState : ViewModel() {
    var words by mutableStateOf<List<LearnedWord>?>(null)
        private set
    var selected by mutableStateOf(emptySet<WordKey>())
    var readFailed by mutableStateOf(false)
        private set
    var reading by mutableStateOf(false)
        private set
    var deleting by mutableStateOf(false)
        private set
    var deleteFailed by mutableStateOf(false)
        private set
    var saveFailed by mutableStateOf(false)
        private set
    private var attempted = emptySet<WordKey>()
    private var started = false

    fun loadOnce(context: Context, id: String) {
        if (started) return
        started = true
        refresh(context, id)
    }

    fun refresh(context: Context, id: String) {
        if (reading || deleting) return
        reading = true
        readFailed = false
        viewModelScope.launch {
            try {
                read(context.applicationContext, id)
            } finally {
                reading = false
            }
        }
    }

    fun delete(context: Context, id: String, batch: List<LearnedWord>) {
        if (reading || deleting || batch.isEmpty()) return
        val app = context.applicationContext
        // A stale confirmation must never recreate a deleted context's dictionaries.
        if (!contextExists(app, id)) return
        deleting = true
        viewModelScope.launch {
            try {
                attempted = attempted + batch.map { it.key }
                saveFailed = try {
                    !LearnedWords.delete(app, id, batch)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e("LearnedWords", "cannot delete learned words", exception)
                    true
                }
                read(app, id)
            } finally {
                deleting = false
            }
        }
    }

    private suspend fun read(context: Context, id: String) {
        if (!contextExists(context, id)) return
        val loaded = try {
            LearnedWords.load(context, id)
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
        readFailed = loaded == null
    }

    private fun contextExists(context: Context, id: String) =
        id == LearningContexts.DEFAULT || LearningContexts.get(context.prefs(), id) != null
}
