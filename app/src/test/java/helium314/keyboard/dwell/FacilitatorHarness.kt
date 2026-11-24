// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import helium314.keyboard.latin.DictionaryFacilitatorImpl
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.ExpandableBinaryDictionary
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.suggestions.SuggestedWordInfo
import kotlinx.coroutines.Job
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import java.util.Locale
import java.util.function.BooleanSupplier

/**
 * Drives the real [DictionaryFacilitatorImpl] learning code with a fake main dictionary and a mocked user history,
 * since the native dictionaries can't load in a unit test. Its private state is reached by reflection on purpose:
 * when upstream renames or reshapes it, these tests fail and point at the code the fork's hooks depend on.
 */
class FacilitatorHarness(private val context: Context, mainWords: Set<String>, val withContext: Boolean = false) {
    data class Learned(val word: String, val valid: Boolean, val previousWord: String?, val stillUsed: BooleanSupplier)

    val facilitator = DictionaryFacilitatorImpl()
    val learned: MutableList<Learned> = java.util.Collections.synchronizedList(mutableListOf())
    val history: ExpandableBinaryDictionary = mockHistory()
    val main = FakeMainDictionary(mainWords)

    init {
        setGroups(newGroup(main, history))
    }

    /** Adds without the 25 s wait, so [awaitLearned] sees the word the job writes. */
    fun addNow() = setField("unlearnDuration", 0L)

    fun add(word: String, ngramContext: NgramContext, wasAutoCapitalized: Boolean = false, picked: Boolean = false) =
        facilitator.addToUserHistory(word, wasAutoCapitalized, ngramContext, 0L, false, picked)

    fun awaitLearned(count: Int): List<Learned> {
        val until = System.currentTimeMillis() + 5000
        while (learned.size < count && System.currentTimeMillis() < until) Thread.sleep(5)
        return learned.toList()
    }

    /** Typed words whose delayed add is still waiting, keyed as unlearning looks them up. */
    @Suppress("UNCHECKED_CAST")
    fun waiting(): Map<String, Job> = synchronized(field("wordsWaitingForHistory") as Any) {
        HashMap(field("wordsWaitingForHistory") as Map<String, Job>)
    }

    fun cancelWaiting() = waiting().values.forEach { it.cancel() }

    /** Replaces the dictionaries like a learning-context switch does. */
    fun switchTo(newHistory: ExpandableBinaryDictionary) = setGroups(newGroup(main, newHistory))

    fun newGroup(mainDict: Dictionary?, historyDict: ExpandableBinaryDictionary?, locale: Locale = Locale.ENGLISH): Any {
        val group = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        val constructor = group.getDeclaredConstructor(Locale::class.java, Dictionary::class.java, Map::class.java, Context::class.java)
        constructor.isAccessible = true
        val subDicts = if (historyDict == null) emptyMap() else mapOf(Dictionary.TYPE_USER_HISTORY to historyDict)
        return constructor.newInstance(locale, mainDict, subDicts, if (withContext) context else null)
    }

    fun setGroups(vararg groups: Any) = setField("dictionaryGroups", groups.toList())

    fun mockHistory(): ExpandableBinaryDictionary {
        val dict = Mockito.mock(ExpandableBinaryDictionary::class.java)
        Mockito.`when`(dict.getFrequency(anyString())).thenReturn(Dictionary.NOT_A_PROBABILITY)
        Mockito.doAnswer {
            val ngram = it.getArgument<NgramContext>(2)
            val previous = if (ngram.prevWordCount == 0 || ngram.isNthPrevWordBeginningOfSentence(1)) null
                else ngram.getNthPrevWord(1)?.toString()
            learned += Learned(it.getArgument(3), it.getArgument(4), previous, it.getArgument(0))
            null
        }.`when`(dict).updateEntriesForWordLater(any(), any(), any(), anyString(), anyBoolean(), anyInt())
        return dict
    }

    private fun field(name: String): Any? = DictionaryFacilitatorImpl::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(facilitator)

    private fun setField(name: String, value: Any) = DictionaryFacilitatorImpl::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.set(facilitator, value)

    companion object {
        /** Loads settings the way the keyboard does for a plain text field, after the test set its prefs. */
        fun loadSettings(context: Context) {
            Settings.init(context)
            val editorInfo = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
            Settings.getInstance().loadSettings(context, Locale.ENGLISH, InputAttributes(editorInfo, false, context.packageName))
        }

        fun previous(word: String) = NgramContext(NgramContext.WordInfo(word))
    }
}

/** A main dictionary that knows exactly these words, case-sensitively like the native lookup of a stored form. */
class FakeMainDictionary(private val words: Set<String>) : Dictionary(TYPE_MAIN, Locale.ENGLISH) {
    override fun getSuggestions(composedData: ComposedData?, ngramContext: NgramContext?, proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion?, sessionId: Int, weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?) = ArrayList<SuggestedWordInfo>()

    override fun isInDictionary(word: String) = word in words
    override fun getFrequency(word: String) = if (word in words) 100 else NOT_A_PROBABILITY
}
