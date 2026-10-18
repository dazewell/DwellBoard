// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.punctuation

import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.internal.KeyboardState
import helium314.keyboard.keyboard.internal.ShiftMode
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.ShadowFacilitator2
import helium314.keyboard.latin.ShadowHandler
import helium314.keyboard.latin.ShadowKeyboardSwitcher
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/** Drives KeyboardState the way a tap does, with "switch after certain punctuation" on or off. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowInputMethodService::class,
    ShadowKeyboardSwitcher::class,
    ShadowHandler::class,
    ShadowFacilitator2::class,
])
class SymbolsSwitchBackStateTest {
    private var layout = ""
    private lateinit var state: KeyboardState

    private val actions = object : KeyboardState.SwitchActions {
        override fun setAlphabetKeyboard(shiftMode: ShiftMode) { layout = "alphabet" }
        override fun setEmojiKeyboard() { layout = "emoji" }
        override fun setClipboardKeyboard() { layout = "clipboard" }
        override fun setNumpadKeyboard() { layout = "numpad" }
        override fun setDpadKeyboard() { layout = "dpad" }
        override fun setSymbolsKeyboard() { layout = "symbols" }
        override fun setSymbolsShiftedKeyboard() { layout = "symbols shifted" }
        override fun startDoubleTapShiftKeyTimer() {}
        override fun popDoubleTapShiftKeyTimer() = false
        override fun cancelDoubleTapShiftKeyTimer() {}
        override fun setOneHandedModeEnabled(enabled: Boolean) {}
        override fun switchOneHandedMode() {}
        override fun setFloatingKeyboardEnabled(enabled: Boolean) {}
    }

    private fun start(enabled: Boolean) {
        val ime = Robolectric.setupService(LatinIME::class.java)
        ime.prefs().edit { putBoolean(Settings.PREF_ABC_AFTER_SYMBOL_CHARS, enabled) }
        state = KeyboardState(actions)
        state.onLoadKeyboard(0, null, false)
    }

    private fun press(code: Int) = state.onPressKey(code, 1, 0, null)
    private fun release(code: Int) = state.onReleaseKey(code, false, 0, null)
    private fun event(code: Int) = state.onEvent(
        if (code < 0) Event.createSoftwareKeypressEvent(Event.NOT_A_CODE_POINT, code, 0, 0, 0, false)
        else Event.createEventForCodePointFromUnknownSource(code), 0, null)

    // same order as a tap: press, onEvent after input logic, release
    private fun tap(code: Int) { press(code); event(code); release(code) }

    @Test fun `default mark returns to letters`() {
        start(enabled = true)
        tap(KeyCode.SYMBOL_ALPHA)
        assertEquals("symbols", layout)
        tap('?'.code)
        assertEquals("alphabet", layout)
    }

    @Test fun `more symbols page returns too`() {
        start(enabled = true)
        tap(KeyCode.SYMBOL_ALPHA)
        tap(KeyCode.SHIFT)
        assertEquals("symbols shifted", layout)
        tap('!'.code)
        assertEquals("alphabet", layout)
    }

    @Test fun `marks outside the set and digits stay`() {
        start(enabled = true)
        tap(KeyCode.SYMBOL_ALPHA)
        for (c in "(.,:5") tap(c.code)
        assertEquals("symbols", layout)
    }

    @Test fun `numpad is unaffected`() {
        start(enabled = true)
        tap(KeyCode.NUMPAD)
        assertEquals("numpad", layout)
        tap('-'.code)
        assertEquals("numpad", layout)
    }

    @Test fun `held symbols key keeps the whole chord on symbols`() {
        start(enabled = true)
        press(KeyCode.SYMBOL_ALPHA)
        tap('?'.code)
        assertEquals("symbols", layout)
        tap('!'.code)
        assertEquals("symbols", layout)
        release(KeyCode.SYMBOL_ALPHA)
        assertEquals("alphabet", layout)
    }

    @Test fun `switch off keeps symbols`() {
        start(enabled = false)
        tap(KeyCode.SYMBOL_ALPHA)
        tap('?'.code)
        assertEquals("symbols", layout)
    }
}
