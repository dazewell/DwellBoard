// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.personalization

import helium314.keyboard.settings.preferences.backupFilePatterns
import java.io.File
import java.util.Locale
import org.junit.Assume.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** User history dictionaries of learning contexts must be picked up by backup, like the default one. */
class LearningContextBackupTest {
    // backupFilePatterns is built with File.separator, which is not a valid regex on a Windows host JVM (Android uses "/")
    @BeforeTest fun onlyWithUnixSeparator() = assumeTrue(File.separatorChar == '/')

    private fun backedUp(path: String) = backupFilePatterns.any { path.matches(it) }

    private fun dictFiles(learningContext: String, locale: Locale): List<String> {
        val name = UserHistoryDictionary.getUserHistoryDictName(locale, learningContext) + ".dict"
        // version 4 dictionaries are directories containing files named like the directory
        return listOf("body", "header").map { "$name${File.separator}$name.$it" }
    }

    @Test fun defaultAndContextDictionariesAreBackedUp() {
        for (id in listOf("", "ab12cd34")) for (locale in listOf(Locale.ENGLISH, Locale.forLanguageTag("de-CH")))
            dictFiles(id, locale).forEach { assertTrue(backedUp(it), "$it should be backed up") }
    }

    @Test fun contextDictionaryNamesAreDistinctFromDefault() {
        val default = UserHistoryDictionary.getUserHistoryDictName(Locale.ENGLISH, "")
        val context = UserHistoryDictionary.getUserHistoryDictName(Locale.ENGLISH, "ab12cd34")
        assertEquals("UserHistoryDictionary.en", default)
        assertTrue(context != default && context.startsWith(UserHistoryDictionary.NAME))
    }
}
