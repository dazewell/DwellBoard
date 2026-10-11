// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.personalization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ClearContextDictionariesTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private fun dict(name: String) = File(ctx.filesDir, "$name.dict").also { File(it, "$name.dict.body").apply { parentFile!!.mkdirs(); writeText("x") } }

    @Test fun clearingDefaultKeepsCustomContexts() {
        val default = dict("UserHistoryDictionary.en")
        val default2 = dict("UserHistoryDictionary.de-CH")
        val custom = dict("UserHistoryDictionary.ctx-aaaaaaaa.en")

        PersonalizationHelper.clearUserHistoryDictionaries(ctx, "")

        assertEquals(listOf(false, false, true), listOf(default, default2, custom).map { it.exists() })
    }

    @Test fun clearingCustomKeepsDefaultAndOtherContexts() {
        val default = dict("UserHistoryDictionary.en")
        val cleared = dict("UserHistoryDictionary.ctx-aaaaaaaa.en")
        val other = dict("UserHistoryDictionary.ctx-bbbbbbbb.en")

        PersonalizationHelper.clearUserHistoryDictionaries(ctx, "aaaaaaaa")

        assertEquals(listOf(true, false, true), listOf(default, cleared, other).map { it.exists() })
    }
}
