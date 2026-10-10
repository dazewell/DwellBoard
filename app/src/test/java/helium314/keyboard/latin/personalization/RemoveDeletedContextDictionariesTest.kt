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
class RemoveDeletedContextDictionariesTest {
    @Test fun onlyDictionariesOfMissingContextsAreRemoved() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        fun dict(name: String) = File(ctx.filesDir, "$name.dict").also { File(it, "$name.dict.body").apply { parentFile!!.mkdirs(); writeText("x") } }
        val default = dict("UserHistoryDictionary.en")
        val kept = dict("UserHistoryDictionary.ctx-aaaaaaaa.en")
        val deleted = dict("UserHistoryDictionary.ctx-bbbbbbbb.en")
        val deleted2 = dict("UserHistoryDictionary.ctx-bbbbbbbb.de-CH")

        PersonalizationHelper.removeDictionariesOfDeletedContexts(ctx, setOf("aaaaaaaa"))

        assertEquals(listOf(true, true, false, false), listOf(default, kept, deleted, deleted2).map { it.exists() })
    }
}
