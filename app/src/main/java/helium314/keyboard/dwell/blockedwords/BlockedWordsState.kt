// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.blockedwords

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import helium314.keyboard.dwell.learnedwords.WordKey
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val BlockedWord.key get() = WordKey(locale, word)

internal class BlockedWordsState : ViewModel() {
    var words by mutableStateOf<List<BlockedWord>?>(null)
        private set
    var selected by mutableStateOf(emptySet<WordKey>())
    var readFailed by mutableStateOf(false)
        private set
    var reading by mutableStateOf(false)
        private set
    var unblocking by mutableStateOf(false)
        private set
    var unblockFailed by mutableStateOf(false)
        private set
    private var started = false

    fun loadOnce(context: Context) {
        if (started) return
        started = true
        refresh(context)
    }

    fun refresh(context: Context) {
        if (reading || unblocking) return
        reading = true
        readFailed = false
        viewModelScope.launch {
            try {
                read(context.applicationContext)
            } finally {
                reading = false
            }
        }
    }

    fun unblock(context: Context, batch: List<BlockedWord>) {
        if (reading || unblocking || batch.isEmpty()) return
        val app = context.applicationContext
        unblocking = true
        viewModelScope.launch {
            try {
                val failed = try {
                    BlockedWords.unblock(app, batch)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e("BlockedWords", "cannot unblock words", exception)
                    batch
                }
                unblockFailed = failed.isNotEmpty()
                read(app)
            } finally {
                unblocking = false
            }
        }
    }

    private suspend fun read(context: Context) {
        val loaded = BlockedWords.load(context)
        if (loaded != null) {
            val keys = loaded.mapTo(HashSet()) { it.key }
            // unblocked words leave the selection, words that failed stay selected for another try
            selected = selected intersect keys
            words = loaded
        }
        readFailed = loaded == null
    }
}
