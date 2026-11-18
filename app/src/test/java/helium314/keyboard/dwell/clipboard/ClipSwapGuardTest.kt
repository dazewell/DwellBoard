// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipSwapGuardTest {
    private val guard = ClipSwapGuard()
    private fun text(s: String) = ClipData.newPlainText("", s)
    private fun image() = ClipData(ClipDescription("", arrayOf("image/png")), ClipData.Item(Uri.parse("content://clips/1")))

    @Test fun nothingIsASwapBeforeBegin() {
        assertFalse(guard.isSwap(0, image()))
    }

    @Test fun swapAndRestoreAreRecognised() {
        guard.begin(1000, image(), text("copied earlier"))
        assertTrue(guard.isSwap(1100, image()))
        assertTrue(guard.isSwap(1600, text("copied earlier")))
    }

    @Test fun userCopyDuringSwapIsNotASwap() {
        guard.begin(1000, image(), text("copied earlier"))
        assertFalse(guard.isSwap(1200, text("something new")))
    }

    @Test fun windowExpires() {
        guard.begin(1000, image(), text("copied earlier"))
        assertFalse(guard.isSwap(1000 + ClipSwapGuard.WINDOW_MILLIS, text("copied earlier")))
    }

    @Test fun missingClipsAreFine() {
        guard.begin(1000, image(), null)
        assertTrue(guard.isSwap(1100, image()))
        assertFalse(guard.isSwap(1100, null))
    }
}
