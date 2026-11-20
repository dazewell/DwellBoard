// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learnedwords

import java.util.Locale

internal data class WordKey(val locale: Locale, val spelling: String) {
    val listKey get() = "word:${locale.toLanguageTag()}:$spelling"
}

internal val LearnedWord.key get() = WordKey(locale, word)

internal data class WordFamily(val anchor: LearnedWord, val members: List<LearnedWord>)

private val consonantStem = Regex("[а-яё]{3,}[бвгджзклмнпрстфхцчшщ]")
private val vowelStem = Regex("[а-яё]{3,}[аеёиоуыэюя]")
private val repeatedLetter = Regex("(.)\\1{2,}")
private val russianVowel = Regex("[аеёиоуыэюя]")

private val nounEndings = listOf("а", "я", "е", "у", "ю", "и", "ы", "ом", "ем", "ам", "ям", "ами", "ями", "ах", "ях", "ов", "ев")
private val pastVerbEndings = listOf("л", "ла", "ло", "ли", "лся", "лась", "лось", "лись")
private val otherVerbEndings = listOf("ть", "ться", "й", "йся", "йте", "йтесь", "ю", "ешь", "ет", "ем", "ете", "ют")
private val aVerbAlternants = listOf("и", "ись", "у")
private val distinctiveAdjectiveEndings = listOf(
    "ый", "ий", "ая", "яя", "ое", "ее", "ые", "ие", "ого", "его", "ому", "ему", "ую", "юю", "ыми", "ими",
)
private val adjectiveEndings = distinctiveAdjectiveEndings + listOf("ой", "ей", "ом", "ем", "ым", "им", "ых", "их")

private val stemOrder = compareBy<WordKey> { it.spelling.length }.thenBy { it.spelling }.thenBy { it.locale.toLanguageTag() }

/** Display-only grouping from corroborating learned endings. Each word keeps its exact key. */
internal fun relatedWordFamilies(words: List<LearnedWord>): List<WordFamily> {
    val russianWords = words.filter { it.locale.language == "ru" }
    val byKey = russianWords.associateBy { it.key }
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

    fun stems(endings: List<String>, pattern: Regex): Sequence<WordKey> {
        val longestFirst = endings.sortedByDescending { it.length }
        return russianWords.asSequence().mapNotNull { word ->
            val ending = longestFirst.firstOrNull { word.word.endsWith(it) } ?: return@mapNotNull null
            val stem = word.word.dropLast(ending.length)
            if (stem.isPlausibleStem(pattern)) WordKey(word.locale, stem) else null
        }.distinct().sortedWith(stemOrder)
    }

    for (stem in stems(distinctiveAdjectiveEndings, consonantStem)) {
        val distinctive = variants(stem, distinctiveAdjectiveEndings)
        if (distinctive.size < 2) continue
        val adjectives = variants(stem, adjectiveEndings)
        if (adjectives.size < 3) continue
        val adverbs = variants(stem, listOf("о"))
        // A learned past form and another verb form support the link through -нн-.
        val linkedVerbs = if (stem.spelling.endsWith("нн")) {
            val base = stem.copy(spelling = stem.spelling.dropLast(2))
            val past = variants(base, pastVerbEndings)
            val other = variants(base, otherVerbEndings)
            if (base.spelling.isPlausibleStem(vowelStem) && past.isNotEmpty() && past.size + other.size >= 2)
                past + other else emptyList()
        } else emptyList()
        addFamily(adjectives + adverbs + linkedVerbs)
    }

    for (stem in stems(pastVerbEndings, vowelStem)) {
        val past = variants(stem, pastVerbEndings)
        if (past.size < 2) continue
        val other = variants(stem, otherVerbEndings)
        if (past.size + other.size < 3) continue
        val alternantStem = stem.copy(spelling = stem.spelling.dropLast(1))
        val extensions = if (stem.spelling.endsWith("а") && alternantStem.spelling.isPlausibleStem(consonantStem)) {
            val alternants = variants(alternantStem, aVerbAlternants)
            if (past.size >= 4 && (other.isNotEmpty() || alternants.isNotEmpty()))
                alternants + variants(alternantStem, listOf("ов"))
            else alternants
        } else emptyList()
        addFamily(past + other + extensions)
    }

    for (anchor in russianWords.sortedBy { it.word.length }) {
        if (anchor.key in used || !anchor.word.isPlausibleStem(consonantStem)) continue
        val forms = variants(anchor.key, nounEndings)
        // Two endings suffice for the existing velar noun pattern.
        val minimum = if (anchor.word.length >= 5 && anchor.word.last() in "кгх") 2 else 3
        if (forms.size >= minimum) addFamily(listOf(anchor) + forms)
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
