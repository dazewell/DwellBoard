// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learning

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/**
 * "Show learned words": once a word no dictionary knows becomes suggestable from the learned history, it floats up
 * from the suggestion strip and fades, styled like the key preview. Default learning delays the write so a correction
 * can cancel it, and a new word typed once is stored without a count, so there the popup comes with the second use.
 */
object LearnedWordPopup {
    private const val DURATION_MS = 1200L
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    /** What the history write runs if the word became suggestable, or null when the setting is off or [isKnown]. */
    inline fun callbackFor(word: String, isKnown: () -> Boolean): Runnable? {
        if (Settings.getValues()?.mShowLearnedWord != true || isKnown()) return null
        return Runnable { post(word) }
    }

    /** The history reports a word that is not suggestable yet, or absent, with a frequency of 0 or less. */
    @JvmStatic
    fun becameSuggestable(before: Int, after: Int): Boolean = before <= 0 && after > 0

    fun post(word: String) {
        handler.post { show(word) }
    }

    private fun show(word: String) {
        // by the time a delayed write lands the keyboard may be closed or the strip hidden; then there is nothing to show
        val root = KeyboardSwitcher.getInstance().mainKeyboardView?.rootView ?: return
        val strip = root.findViewById<View>(R.id.suggestion_strip_view) ?: return
        val content = root.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (!strip.isShown || strip.height == 0) return
        val context = strip.context
        val colors = Settings.getValues()?.mColors ?: return

        val view = TextView(context)
        val density = context.resources.displayMetrics.density
        // not the key preview drawable: its padding and minimum size also cover the key below, which leaves a tall
        // empty box around a word. A white shape takes the preview colour through the same filter the theme applies.
        view.background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 8 * density }
        colors.setBackground(view, ColorType.KEY_PREVIEW_BACKGROUND)
        view.setTextColor(colors.get(ColorType.KEY_PREVIEW_TEXT))
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.config_suggestion_text_size))
        val text = context.getString(R.string.dwell_learned_word_popup, word)
        view.text = text
        KeyboardTypeface.applyToTextView(view, text, android.graphics.Typeface.DEFAULT)
        view.setPadding((12 * density).toInt(), (4 * density).toInt(), (12 * density).toInt(), (4 * density).toInt())
        view.isClickable = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val stripPos = IntArray(2).also { strip.getLocationInWindow(it) }
        val contentPos = IntArray(2).also { content.getLocationInWindow(it) }
        content.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.x = (stripPos[0] - contentPos[0] + (strip.width - view.measuredWidth) / 2).toFloat()
        view.y = (stripPos[1] - contentPos[1] + (strip.height - view.measuredHeight) / 2).toFloat()
        // the IME window may end at the top of the strip, so the rise stays short enough not to be cut off
        view.animate()
            .translationYBy(-strip.height * 0.6f)
            .alpha(0f)
            .setDuration(DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { (view.parent as? ViewGroup)?.removeView(view) }
            .start()
    }
}
