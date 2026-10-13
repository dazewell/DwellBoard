// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.autocap

import android.text.TextUtils
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
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Returning from symbols to letters has to respect auto-caps, e.g. after "end? " typed on the symbols layout. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowInputMethodService::class,
    ShadowKeyboardSwitcher::class,
    ShadowHandler::class,
    ShadowFacilitator2::class,
])
class SymbolsToAlphabetCapsTest {
    private val sentence = TextUtils.CAP_MODE_SENTENCES
    private var layout = ""
    private lateinit var state: KeyboardState

    private val actions = object : KeyboardState.SwitchActions {
        override fun setAlphabetKeyboard(shiftMode: ShiftMode) { layout = "alphabet $shiftMode" }
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

    @BeforeTest fun setUp() {
        Robolectric.setupService(LatinIME::class.java) // loads the settings KeyboardState reads
        state = KeyboardState(actions)
        state.onLoadKeyboard(sentence, null, false)
    }

    // same order as a tap: press, input logic with its shift update, onEvent, release
    // InputLogic requests a shift update for text and shift, but not for layout switch keys
    private fun tap(code: Int, autoCapsAfter: Int) {
        state.onPressKey(code, 1, autoCapsAfter, null)
        if (code >= 0 || code == KeyCode.SHIFT)
            state.onUpdateShiftState(autoCapsAfter, null)
        val event = if (code < 0) Event.createSoftwareKeypressEvent(Event.NOT_A_CODE_POINT, code, 0, 0, 0, false)
            else Event.createEventForCodePointFromUnknownSource(code)
        state.onEvent(event, autoCapsAfter, null)
        state.onReleaseKey(code, false, autoCapsAfter, null)
    }

    private fun typeWord() = "end".forEach { tap(it.code, 0) }

    @Test fun `switching back after an auto-spaced question mark shifts`() {
        for (backKey in listOf(KeyCode.SYMBOL_ALPHA, KeyCode.ALPHA)) {
            setUp()
            typeWord()
            tap(KeyCode.SYMBOL_ALPHA, 0)
            assertEquals("symbols", layout)
            tap('?'.code, sentence) // the space is inserted with the question mark, so no space key switches back
            assertEquals("symbols", layout)
            tap(backKey, sentence)
            assertEquals("alphabet AUTOMATIC", layout, "back key $backKey")
        }
    }

    @Test fun `switching back mid-sentence stays lowercase`() {
        typeWord()
        tap(KeyCode.SYMBOL_ALPHA, 0)
        tap('5'.code, 0)
        tap(KeyCode.SYMBOL_ALPHA, 0)
        assertEquals("alphabet UNSHIFT", layout)
    }

    @Test fun `manual shift survives a trip to symbols`() {
        typeWord()
        tap(KeyCode.SHIFT, 0)
        assertEquals("alphabet MANUAL", layout)
        tap(KeyCode.SYMBOL_ALPHA, 0)
        tap(KeyCode.SYMBOL_ALPHA, 0)
        assertEquals("alphabet MANUAL", layout)
    }
}
