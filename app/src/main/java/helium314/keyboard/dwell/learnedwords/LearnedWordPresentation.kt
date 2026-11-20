// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learnedwords

import java.util.Locale

internal data class WordKey(val locale: Locale, val spelling: String) {
    val listKey get() = "word:${locale.toLanguageTag()}:$spelling"
}

internal val LearnedWord.key get() = WordKey(locale, word)

internal data class WordFamily(val anchor: LearnedWord, val members: List<LearnedWord>)

private val russianAnchor = Regex("[а-яё]{4,}[кгх]")
private val repeatedLetter = Regex("(.)\\1{2,}")
private val russianVowel = Regex("[аеёиоуыэюя]")
private val endings = listOf("а", "е", "у", "и", "ом", "ам", "ами", "ах", "ов")

/** Small display heuristic for hard velar stems. The learned base and two distinct endings must agree. */
internal fun relatedWordFamilies(words: List<LearnedWord>): List<WordFamily> {
    val stems = words.associateBy { it.word }
    val grouped = HashMap<String, WordFamily>()
    val used = HashSet<String>()
    // Prefer the shortest actual anchor if candidates overlap.
    for (anchor in words.sortedBy { it.word.length }) {
        val spelling = anchor.word
        if (anchor.locale.language != "ru" || spelling in used || !russianAnchor.matches(spelling) ||
            !russianVowel.containsMatchIn(spelling) || repeatedLetter.containsMatchIn(spelling)) continue
        val variants = endings.mapNotNull { stems[spelling + it] }.filter { it.word !in used }
        if (variants.size < 2) continue
        val members = listOf(anchor) + variants
        grouped[spelling] = WordFamily(anchor, members)
        members.mapTo(used) { it.word }
    }
    return words.mapNotNull { grouped[it.word] ?: if (it.word in used) null else WordFamily(it, listOf(it)) }
}

internal fun selectWordRange(
    baseline: Set<WordKey>,
    rows: List<WordKey>,
    anchor: Int,
    end: Int,
): Set<WordKey> = baseline + rows.subList(minOf(anchor, end), maxOf(anchor, end) + 1)
