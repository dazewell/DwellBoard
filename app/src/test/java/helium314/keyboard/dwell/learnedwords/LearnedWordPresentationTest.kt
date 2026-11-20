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
    fun groupsDenseRussianVerbClustersAndKeepsExactMembers() {
        val forms = listOf("вжикал", "вжикала", "вжикалась", "вжикали", "вжикало",
            "вжикалось", "вжикался", "вжикись", "вжиков", "вжику").map { word(it) }
        val unrelated = listOf(word("вжикание"), word("вжикало", english), word("Вжикала"))
        val grouped = relatedWordFamilies((forms + unrelated).reversed())

        val family = grouped.single { it.members.size > 1 }
        assertEquals(forms.first(), family.anchor)
        assertEquals(forms.toSet(), family.members.toSet())
        assertEquals(unrelated.toSet(), grouped.filter { it.members.size == 1 }.map { it.anchor }.toSet())
    }

    @Test
    fun leavesSparseVerbLookalikesAndRepeatedLettersSeparate() {
        val entries = listOf("канал", "канала", "каналов", "ааабал", "ааабала", "ааабали").map { word(it) }
        assertTrue(relatedWordFamilies(entries).all { it.members.size == 1 })
    }

    @Test
    fun overlappingVerbFamiliesHaveStableMembership() {
        val entries = listOf("вжикал", "вжикала", "вжикало", "вжикали",
            "вжикалал", "вжикалала", "вжикалали").map { word(it) }
        fun families(words: List<LearnedWord>) =
            relatedWordFamilies(words).associate { it.anchor.word to it.members.map { member -> member.word }.toSet() }

        val grouped = families(entries)
        assertTrue("вжикали" in grouped.getValue("вжикал"))
        assertEquals(grouped, families(entries.reversed()))
    }

    @Test
    fun groupsAdjectiveAdverbAndCorroboratingVerbForms() {
        val forms = listOf("обалдеешь", "обалдел", "обалденная", "обалденно", "обалденного",
            "обалденное", "обалденной", "обалденном", "обалденные", "обалденный",
            "обалденными", "обалденных").map { word(it) }
        val lookalikes = listOf(word("обалдение"), word("обалденный", english), word("Обалденная"))
        val grouped = relatedWordFamilies((forms + lookalikes).reversed())

        val family = grouped.single { it.members.size > 1 }
        assertEquals("обалденный", family.anchor.word)
        assertEquals(forms.toSet(), family.members.toSet())
        assertEquals(lookalikes.toSet(), grouped.filter { it.members.size == 1 }.map { it.anchor }.toSet())
    }

    @Test
    fun recognizesOtherVerbAndNounPatternsAndRejectsSparseAdjectives() {
        fun family(vararg spellings: String) =
            relatedWordFamilies(spellings.map { word(it) }).single().members.map { it.word }.toSet()

        assertEquals(setOf("проверил", "проверила", "проверилось"), family("проверил", "проверила", "проверилось"))
        assertEquals(setOf("старт", "старта", "старту", "стартом"), family("старт", "старта", "старту", "стартом"))
        val sparse = listOf("маска", "маской", "масками", "обалденно", "обалденная", "обалдел").map { word(it) }
        assertTrue(relatedWordFamilies(sparse).all { it.members.size == 1 })
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
