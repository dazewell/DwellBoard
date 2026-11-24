// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.emoji

import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.emoji.EmojiSearchActivity
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** #emoji-space-abc and #emoji-search-clear through upstream's real `KeyboardState.onEvent`, not the helpers alone. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowInputMethodService::class,
    ShadowKeyboardSwitcher::class,
    ShadowHandler::class,
    ShadowFacilitator2::class,
])
class EmojiKeyboardStateTest {
    private var layout = ""
    private lateinit var state: KeyboardState
    private lateinit var latinIME: LatinIME

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

    @BeforeTest fun setUp() {
        latinIME = Robolectric.setupService(LatinIME::class.java) // loads the settings KeyboardState reads
        latinIME.prefs().edit { clear() }
        state = KeyboardState(actions)
        state.onLoadKeyboard(0, null, false)
    }

    private fun tap(code: Int) {
        state.onPressKey(code, 1, 0, null)
        val event = if (code < 0) Event.createSoftwareKeypressEvent(Event.NOT_A_CODE_POINT, code, 0, 0, 0, false)
            else Event.createEventForCodePointFromUnknownSource(code)
        state.onEvent(event, 0, null)
        state.onReleaseKey(code, false, 0, null)
    }

    private fun pickEmojiThenSpace() {
        tap(KeyCode.EMOJI)
        assertEquals("emoji", layout)
        tap(KeyCode.MULTIPLE_CODE_POINTS) // an emoji pick
        tap(' '.code)
    }

    @Test fun `space after an emoji returns to letters with the option`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_ABC_AFTER_EMOJI_SPACE, true) }
        pickEmojiThenSpace()
        assertEquals("alphabet", layout)
    }

    @Test fun `space after an emoji stays on emojis without the option`() {
        pickEmojiThenSpace()
        assertEquals("emoji", layout)
    }

    @Test fun `leaving the emoji keyboard by hand clears the search, the automatic ABC does not`() {
        for (key in listOf(KeyCode.ALPHA, KeyCode.EMOJI)) {
            tap(KeyCode.EMOJI)
            EmojiSearchActivity.searchText = "cat"
            EmojiSearchReset.setAutomaticAlpha(true)
            try { tap(KeyCode.ALPHA) } finally { EmojiSearchReset.setAutomaticAlpha(false) }
            assertEquals("cat", EmojiSearchActivity.searchText)

            tap(KeyCode.EMOJI)
            tap(key)
            assertEquals("alphabet", layout, "key $key")
            assertEquals("", EmojiSearchActivity.searchText, "key $key")
        }
    }
}
