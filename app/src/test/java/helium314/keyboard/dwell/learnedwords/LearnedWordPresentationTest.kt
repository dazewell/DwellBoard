// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learnedwords

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LearnedWordPresentationTest {
    private val russian = Locale.forLanguageTag("ru")
    private val english = Locale.ENGLISH

    private fun word(spelling: String, locale: Locale = russian) = LearnedWord(locale, spelling, 20, 3)

    @Test
    fun groupsOnlyConfidentRussianFormsWithALearnedAnchor() {
        val family = listOf(word("душик"), word("душика"), word("душике"))
        val others = listOf(word("душище"), word("аааак"), word("аааака"), word("ааааке"), word("🐳"), word("Душик"))
        val grouped = relatedWordFamilies(family + others)

        assertEquals(family, grouped.single { it.anchor == family.first() }.members)
        assertEquals(others, grouped.filter { it.members.size == 1 }.map { it.anchor })
        assertTrue(relatedWordFamilies(family.drop(1)).all { it.members.size == 1 })
        assertTrue(relatedWordFamilies(family.take(2)).all { it.members.size == 1 })
        assertTrue(relatedWordFamilies(family.map { word(it.word, english) }).all { it.members.size == 1 })
    }

    @Test
    fun identityIgnoresMutableStatisticsWhilePreservingLocaleAndSpelling() {
        val word = word("душик")
        assertEquals(word.key, word.copy(probability = 4, count = 1, inDictionary = true).key)
        assertNotEquals(word.key, word.copy(locale = english).key)
        assertNotEquals(word.key, word.copy(word = "Душик").key)
    }

    @Test
    fun reversingDragRetractsItsRangeAndPreservesEarlierSelections() {
        val rows = listOf(word("one", english), word("two", english), word("три"), word("четыре"), word("пять"))
            .map { it.key }
        val hidden = word("hidden", english).key
        val baseline = setOf(rows[4], hidden)
        assertEquals(baseline + rows.subList(1, 5), selectWordRange(baseline, rows, anchor = 1, end = 4))
        val shrunk = selectWordRange(baseline, rows, anchor = 1, end = 2)
        assertFalse(rows[3] in shrunk)
        assertEquals(baseline + rows.subList(1, 3), shrunk)
        assertEquals(baseline + rows.subList(0, 2), selectWordRange(baseline, rows, anchor = 1, end = 0))
    }
}
