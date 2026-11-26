// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.learning

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/**
 * "Show learned and blocked words": a word floats up from the suggestion strip and fades, in the key preview colours,
 * once a word no dictionary knows becomes suggestable from the learned history, and when removing a suggestion puts
 * a word on the block list. Default learning delays the write so a correction can cancel it, and a new word typed once
 * is stored without a count, so there the learned popup comes with the second use.
 */
object WordPopup {
    private const val DURATION_MS = 1200L
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    // Counts input starts, on the main thread (not in gesture data gathering mode, whose facilitator has no hook). A default-mode write lands 25 s after typing, possibly in another app,
    // a password field or a screen share; a popup only shows in the input session its word was typed in.
    @PublishedApi internal var session = 0

    fun onStartInput() { session++ }

    /** What the history write runs if the word became suggestable, or null when the setting is off or [isKnown]. */
    inline fun callbackFor(word: String, isKnown: () -> Boolean): Runnable? {
        if (Settings.getValues()?.mShowWordPopups != true || isKnown()) return null
        val typedIn = session
        return Runnable { post(word, typedIn) }
    }

    /** The history reports a word that is not suggestable yet, or absent, with a frequency of 0 or less. */
    @JvmStatic
    fun becameSuggestable(before: Int, after: Int): Boolean = before <= 0 && after > 0

    fun post(word: String, typedIn: Int) {
        handler.post {
            if (shouldShow(typedIn, session, Settings.getValues()?.let { it.mShowWordPopups && !it.mIncognitoModeEnabled } == true))
                show(word, R.string.dwell_learned_word_popup)
        }
    }

    // main thread only: a word is blocked from a bin tap in the suggestion strip
    private var blockPending = false

    /** A word was just added to the block list. Unlike learning this is immediate, in the field the user is in. */
    fun onBlocked(word: String) {
        // one removal blocks the word in every active language whose dictionary has it; one popup is enough
        if (blockPending || Settings.getValues()?.mShowWordPopups != true) return
        blockPending = true
        // posted so the strip has redrawn without the removed word first
        handler.post { blockPending = false; show(word, R.string.dwell_blocked_word_popup) }
    }

    /** The field the word was typed in is still the current one, and it may show learned words. */
    fun shouldShow(typedIn: Int, current: Int, allowedNow: Boolean): Boolean = allowedNow && typedIn == current

    private fun show(word: String, format: Int) {
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
        val text = context.getString(format, word)
        view.text = text
        KeyboardTypeface.applyToTextView(view, text, android.graphics.Typeface.DEFAULT)
        view.setPadding((12 * density).toInt(), (4 * density).toInt(), (12 * density).toInt(), (4 * density).toInt())
        view.isClickable = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val stripPos = IntArray(2).also { strip.getLocationInWindow(it) }
        val contentPos = IntArray(2).also { content.getLocationInWindow(it) }
        // left, not the default start: the IME window is right-to-left in an RTL system locale, and x counts from the left
        content.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.LEFT))
        view.x = (stripPos[0] - contentPos[0] + (strip.width - view.measuredWidth) / 2).toFloat()
        view.y = (stripPos[1] - contentPos[1] + (strip.height - view.measuredHeight) / 2).toFloat()
        val remove = Runnable { (view.parent as? ViewGroup)?.removeView(view) }
        // with animations off (accessibility, battery saver) an animator ends before the first frame and would remove
        // the popup unseen, so it just stays for the same time
        if (!animatorsEnabled(context)) {
            handler.postDelayed(remove, DURATION_MS)
            return
        }
        // a short rise keeps the word near the strip it came from rather than drifting over the app
        view.animate()
            .translationYBy(-strip.height * 0.6f)
            .alpha(0f)
            .setDuration(DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction(remove)
            .start()
    }

    private fun animatorsEnabled(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ValueAnimator.areAnimatorsEnabled()
        else android.provider.Settings.Global.getFloat(context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
}
