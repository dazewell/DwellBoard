// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.contexts

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.LearningContexts
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.prefs
import kotlin.math.min

/**
 * Small dot in the top-end corner of the toolbar expand [key], shown while the app uses a learning context.
 *
 * The key is mirrored when the toolbar is expanded, and a mirrored key does not redraw its overlay, so the dot is drawn
 * on the overlay of the key's parent, which is never mirrored, at the key's position.
 */
class ContextIndicatorDot(private val key: View, colors: Colors) : Drawable() {
    private val density = key.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND) or -0x1000000 // ignore alpha, like the pinned key indicator
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.get(ColorType.STRIP_BACKGROUND) or -0x1000000 }
    private val host = key.parent as ViewGroup

    var shown = false
        private set

    init {
        host.overlay.add(this)
    }

    /** Sets the listener of the key. Long-clicking is only enabled while the dot is shown, so the key is unchanged otherwise. */
    fun setLongClickListener(listener: View.OnLongClickListener) {
        key.setOnLongClickListener(listener)
        key.isLongClickable = shown
    }

    /** Updates the dot to the current learning context and settings. Cheap, as it is called whenever suggestions change. */
    fun refresh() {
        val sv = Settings.getValues()
        // same conditions as for showing the expand key with the arrow icon
        update(!sv.mIncognitoModeEnabled && sv.mToolbarMode == ToolbarMode.EXPANDABLE && LearningContexts.current != LearningContexts.DEFAULT)
    }

    private fun update(show: Boolean) {
        if (show == shown) return
        shown = show
        key.isLongClickable = show
        host.invalidate()
    }

    /** Shows the name of the current learning context. Returns false if there is nothing to show. */
    fun showName(): Boolean {
        if (!shown) return false
        val name = LearningContexts.get(key.context.prefs(), LearningContexts.current)?.name ?: return false
        KeyboardSwitcher.getInstance().showToast(key.context.getString(R.string.dwell_learning_context_toast, name), true)
        return true
    }

    override fun draw(canvas: Canvas) {
        if (!shown || !key.isVisible) return
        val ringWidth = RING_DP * density
        val radius = min(key.width, key.height) * DIAMETER_FRACTION / 2f
        val atRight = key.layoutDirection != View.LAYOUT_DIRECTION_RTL
        val cx = if (atRight) key.right - radius - ringWidth else key.left + radius + ringWidth
        val cy = key.top + radius + ringWidth
        canvas.drawCircle(cx, cy, radius + ringWidth, ring)
        canvas.drawCircle(cx, cy, radius, fill)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val DIAMETER_FRACTION = 0.22f
        const val RING_DP = 1.5f
    }
}
