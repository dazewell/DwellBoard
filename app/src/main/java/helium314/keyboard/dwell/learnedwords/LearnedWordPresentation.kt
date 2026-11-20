// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learnedwords

import java.util.Locale

internal data class WordKey(val locale: Locale, val spelling: String) {
    val listKey get() = "word:${locale.toLanguageTag()}:$spelling"
}

internal val LearnedWord.key get() = WordKey(locale, word)

internal data class WordFamily(val anchor: LearnedWord, val members: List<LearnedWord>)

private val nounStem = Regex("[а-яё]{4,}[кгх]")
private val verbStem = Regex("[а-яё]{3,}[бвгджзклмнпрстфхцчшщ]")
private val repeatedLetter = Regex("(.)\\1{2,}")
private val russianVowel = Regex("[аеёиоуыэюя]")
private val nounEndings = listOf("а", "е", "у", "и", "ом", "ам", "ами", "ах", "ов")
private val pastVerbEndings = listOf("ал", "ала", "ало", "али", "ался", "алась", "алось", "ались")
private val otherVerbEndings = listOf("ать", "аться", "ай", "айся", "айте", "айтесь", "аю", "аешь", "ает", "ают", "и", "ись", "у")

/** Display-only grouping by bounded Russian endings and evidence from several learned forms. */
internal fun relatedWordFamilies(words: List<LearnedWord>): List<WordFamily> {
    val byKey = words.associateBy { it.key }
    val grouped = HashMap<WordKey, WordFamily>()
    val used = HashSet<WordKey>()

    fun variants(stem: WordKey, endings: List<String>) = endings.mapNotNull { ending ->
        val key = stem.copy(spelling = stem.spelling + ending)
        byKey[key]?.takeUnless { key in used }
    }

    fun addFamily(members: List<LearnedWord>) {
        val anchor = members.first()
        grouped[anchor.key] = WordFamily(anchor, members)
        members.mapTo(used) { it.key }
    }

    val russianWords = words.filter { it.locale.language == "ru" }
    val verbStems = russianWords.asSequence().mapNotNull { word ->
        val ending = pastVerbEndings.firstOrNull { word.word.endsWith(it) } ?: return@mapNotNull null
        val stem = word.word.dropLast(ending.length)
        if (stem.isPlausibleStem(verbStem)) WordKey(word.locale, stem) else null
    }.distinct().sortedWith(compareBy<WordKey> { it.spelling.length }
        .thenBy { it.spelling }
        .thenBy { it.locale.toLanguageTag() })
    for (stem in verbStems) {
        val pastForms = variants(stem, pastVerbEndings)
        if (pastForms.size < 3) continue
        val otherForms = variants(stem, otherVerbEndings)
        // A dense verb family can also absorb the nearby plural noun form.
        val nouns = if (pastForms.size >= 4 && otherForms.isNotEmpty()) variants(stem, listOf("ов")) else emptyList()
        addFamily(pastForms + otherForms + nouns)
    }

    // Prefer the shortest actual noun anchor if candidates overlap.
    for (anchor in russianWords.sortedBy { it.word.length }) {
        if (anchor.key in used || !anchor.word.isPlausibleStem(nounStem)) continue
        val forms = variants(anchor.key, nounEndings)
        if (forms.size >= 2) addFamily(listOf(anchor) + forms)
    }
    return words.mapNotNull { grouped[it.key] ?: if (it.key in used) null else WordFamily(it, listOf(it)) }
}

private fun String.isPlausibleStem(pattern: Regex) =
    pattern.matches(this) && russianVowel.containsMatchIn(this) && !repeatedLetter.containsMatchIn(this)

internal fun selectWordRange(
    baseline: Set<WordKey>,
    rows: List<WordKey>,
    anchor: Int,
    end: Int,
): Set<WordKey> = baseline + rows.subList(minOf(anchor, end), maxOf(anchor, end) + 1)
