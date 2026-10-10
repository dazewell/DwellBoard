// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import helium314.keyboard.latin.settings.LearningContexts
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LearningContextsTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().prefs()

    @BeforeTest
    fun reset() {
        prefs.edit().clear().commit()
        LearningContexts.setCurrentForPackage(prefs, null)
    }

    @Test fun unassignedAppsUseDefault() {
        assertEquals(LearningContexts.DEFAULT, LearningContexts.idForPackage(prefs, "some.app"))
        assertEquals(LearningContexts.DEFAULT, LearningContexts.idForPackage(prefs, null))
    }

    @Test fun assignedAppsUseContext() {
        val a = LearningContexts.create(prefs, " work ")
        assertEquals("work", a.name)
        LearningContexts.setPackages(prefs, a.id, setOf("app.one", "app.two"))
        assertEquals(a.id, LearningContexts.idForPackage(prefs, "app.one"))
        assertEquals(LearningContexts.DEFAULT, LearningContexts.idForPackage(prefs, "app.three"))
    }

    @Test fun appIsOnlyInOneContext() {
        val a = LearningContexts.create(prefs, "a")
        val b = LearningContexts.create(prefs, "b")
        assertNotEquals(a.id, b.id)
        LearningContexts.setPackages(prefs, a.id, setOf("app.one", "app.two"))
        LearningContexts.setPackages(prefs, b.id, setOf("app.two"))
        assertEquals(setOf("app.one"), LearningContexts.get(prefs, a.id)?.packages)
        assertEquals(b.id, LearningContexts.idForPackage(prefs, "app.two"))
    }

    @Test fun contextsSurviveReload() {
        val a = LearningContexts.create(prefs, "a")
        LearningContexts.setPackages(prefs, a.id, setOf("app.one"))
        // data is read from prefs, not only from the in-memory cache
        val raw = prefs.getString(LearningContexts.PREF_LEARNING_CONTEXTS, null)
        prefs.edit().putString(LearningContexts.PREF_LEARNING_CONTEXTS, raw).commit()
        assertEquals(listOf(a.copy(packages = setOf("app.one"))), LearningContexts.getAll(prefs))
    }

    @Test fun idsThatAreNotSafeInFileNamesAreIgnored() {
        // e.g. from a crafted backup
        prefs.edit().putString(LearningContexts.PREF_LEARNING_CONTEXTS,
            """[{"id":"../../x","name":"bad","packages":["app.one"]},{"id":"ab12cd34","name":"ok","packages":["app.two"]}]""").commit()
        assertEquals(listOf("ab12cd34"), LearningContexts.getAll(prefs).map { it.id })
        assertEquals(LearningContexts.DEFAULT, LearningContexts.idForPackage(prefs, "app.one"))
        assertEquals("ab12cd34", LearningContexts.idForPackage(prefs, "app.two"))
    }

    @Test fun currentContextFollowsPackage() {
        val a = LearningContexts.create(prefs, "a")
        LearningContexts.setPackages(prefs, a.id, setOf("app.one"))
        assertTrue(LearningContexts.setCurrentForPackage(prefs, "app.one"))
        assertEquals(a.id, LearningContexts.current)
        assertFalse(LearningContexts.setCurrentForPackage(prefs, "app.one"))
        assertTrue(LearningContexts.setCurrentForPackage(prefs, "app.other"))
        assertEquals(LearningContexts.DEFAULT, LearningContexts.current)
    }

    @Test fun deleteResetsCurrentAndRemovesContext() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val a = LearningContexts.create(prefs, "a")
        LearningContexts.setPackages(prefs, a.id, setOf("app.one"))
        LearningContexts.setCurrentForPackage(prefs, "app.one")
        LearningContexts.delete(ctx, a.id)
        assertEquals(LearningContexts.DEFAULT, LearningContexts.current)
        assertTrue(LearningContexts.getAll(prefs).isEmpty())
        assertEquals(LearningContexts.DEFAULT, LearningContexts.idForPackage(prefs, "app.one"))
    }
}
