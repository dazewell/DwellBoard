// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.capitalization

import android.text.TextUtils
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.App
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import helium314.keyboard.latin.utils.CapsModeUtils
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class EmojiCapsTest {
    private val modes = TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES

    private fun capsSentence(text: String, hasSpaceBefore: Boolean = false): Boolean {
        val sp = SpacingAndPunctuations(ApplicationProvider.getApplicationContext<App>().resources, false, Locale.ENGLISH)
        return CapsModeUtils.getCapsMode(text, modes, sp, hasSpaceBefore) and TextUtils.CAP_MODE_SENTENCES != 0
    }

    @Test fun emojiAfterSentenceEndCapitalizes() {
        assertEquals(true, capsSentence("This is a sentence. 🚀 "))
        assertEquals(true, capsSentence("Done.🚀 "))
        assertEquals(true, capsSentence("Really? 🚀🎉 "))
        assertEquals(true, capsSentence("Done. 🚀 🎉 "))
        assertEquals(true, capsSentence("Done. 🚀", hasSpaceBefore = true))
    }

    @Test fun emojiMidSentenceDoesNotCapitalize() {
        assertEquals(false, capsSentence("This is a sentence 🚀 "))
        assertEquals(false, capsSentence("so e.g. 🚀 "))
        assertEquals(false, capsSentence("word🚀 "))
    }

    @Test fun emojiSequencesAreOneEmoji() {
        // thumbs up with skin tone, family ZWJ sequence, heart with FE0F, keycap one, flag
        val emojis = listOf("👍🏽", "👨‍👩‍👧",
            "❤️", "1️⃣", "🇩🇪")
        for (e in emojis) {
            assertEquals(true, capsSentence("Done. $e "), e)
            assertEquals(false, capsSentence("done $e "), e)
        }
    }

    @Test fun emojiAtParagraphStartCapitalizes() {
        assertEquals(true, capsSentence("🚀 "))
        assertEquals(true, capsSentence("first line\n🚀 "))
    }

    @Test fun noEmojiUnchanged() {
        assertEquals(true, capsSentence("Done. "))
        assertEquals(false, capsSentence("done "))
        assertEquals(false, capsSentence("done … "))
        assertEquals(false, capsSentence("1 "))
        assertEquals(false, capsSentence("so # "))
    }

    @Test fun skipStopsAtNonEmoji() {
        assertEquals(5, EmojiCaps.skipTrailingEmojis("Done.", 5))
        assertEquals(5, EmojiCaps.skipTrailingEmojis("Done. 🚀", 8))
        assertEquals(7, EmojiCaps.skipTrailingEmojis("word #️", 7)) // lone modifier is no emoji
    }
}
