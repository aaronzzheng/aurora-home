package home.aurora

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

/**
 * A clock that's just big type, e.g. a thin "9:42" or a serif one. With
 * `lcd`, the digits sit in a pressed-in gray box like an old PC clock.
 */
open class PlainClock(c: Context, sp: Float, color: Int, font: Typeface?, shadow: Boolean, lcd: Boolean) : TextView(c), ClockFace {

    init {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        typeface = font
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        if (shadow) setShadowLayer(dp(16f).toFloat(), 0f, 0f, 0x66000000)
        if (lcd) {
            background = HomeTheme.Bevel(c, 0xFFC0C0C0.toInt(), true)
            setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
        } else {
            letterSpacing = -0.02f
        }
    }

    override fun setTime(hhmm: String) {
        val s = hhmm.trim()
        val t = s.substring(0, s.length - 2) + ":" + s.substring(s.length - 2)
        if (!t.contentEquals(text)) text = t
    }

    private fun dp(v: Float): Int {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics))
    }
}
