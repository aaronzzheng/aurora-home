package home.aurora

import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.View

/**
 * Hong Kong style split-flap clock. Each digit sits on a dark card split across
 * the middle; when it changes, the top half of the old digit falls forward on
 * its hinge, then the bottom half of the new digit drops into place.
 *
 * @param heightDp card height; everything else scales from it.
 */
open class FlipClock(c: Context, heightDp: Float) : View(c), ClockFace {

    private val cardW: Float
    private val cardH: Float
    private val gap: Float
    private val groupGap: Float
    private val radius: Float
    private val card = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val seam = Paint()
    private val shade = Paint()
    private val dots = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pin = Paint(Paint.ANTI_ALIAS_FLAG)
    private val camera = Camera()
    private val m = Matrix()
    private val r = RectF()
    private val shown = charArrayOf(' ', ' ', ' ', ' ')
    private val prev = charArrayOf(' ', ' ', ' ', ' ')
    private val flipAt = LongArray(4)
    private var cardColors = intArrayOf(0xFF26282F.toInt(), 0xFF1A1C22.toInt(), 0xFF141519.toInt(), 0xFF0E0F12.toInt())

    init {
        cardH = dp(heightDp)
        cardW = cardH * 0.7f
        gap = cardH * 0.06f
        groupGap = cardH * 0.32f
        radius = cardH * 0.08f
        text.color = 0xFFF4F4F2.toInt()
        text.textAlign = Paint.Align.CENTER
        text.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        text.textSize = cardH * 0.72f
        seam.color = 0xFF050506.toInt()
        dots.color = 0xE6FFFFFF.toInt()
        pin.color = 0xFF2A2C33.toInt()
        card.setShadowLayer(cardH * 0.12f, 0f, cardH * 0.05f, 0x99000000.toInt())
    }

    /** Card colors (top to bottom, four stops), digit color and font, and an optional glow. */
    fun setLook(cards: IntArray, digits: Int, font: Typeface?, glow: Int) {
        cardColors = cards
        text.color = digits
        text.typeface = font
        if (glow != 0) text.setShadowLayer(cardH * 0.12f, 0f, 0f, glow)
        val lightCards = (cards[0] and 0xFF) > 0x80
        dots.color = if (lightCards) 0xF2FFFFFF.toInt() else 0xE6FFFFFF.toInt()
        seam.color = if (lightCards) 0x33000000 else 0xFF050506.toInt()
        pin.color = if (lightCards) 0xFFB9B2A0.toInt() else 0xFF2A2C33.toInt()
        if (lightCards) card.setShadowLayer(cardH * 0.12f, 0f, cardH * 0.05f, 0x66000000)
        invalidate()
    }

    /** Show this time, e.g. "0942" or " 942". Changed digits flip. */
    override fun setTime(hhmm: String) {
        val now = SystemClock.uptimeMillis()
        for (i in 0 until 4) {
            val ch = hhmm[i]
            if (ch != shown[i]) {
                prev[i] = if (shown[i] == ' ' && flipAt[i] == 0L) ch else shown[i]
                shown[i] = ch
                flipAt[i] = now + i * 70L   // a slight ripple across the digits
            }
        }
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension((cardW * 4 + gap * 2 + groupGap + cardH * 0.1f).toInt(), (cardH + cardH * 0.12f).toInt())
    }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        var anim = false
        var x = 0f
        val top = cardH * 0.02f
        for (i in 0 until 4) {
            if (i == 2) {
                val cx = x + groupGap / 2 - gap / 2
                val r0 = cardH * 0.055f
                c.drawCircle(cx, top + cardH * 0.34f, r0, dots)
                c.drawCircle(cx, top + cardH * 0.66f, r0, dots)
                x += groupGap - gap
            }
            val t = if (flipAt[i] == 0L) 1f else Math.max(0f, Math.min(1f, (now - flipAt[i]) / FLIP_MS.toFloat()))
            if (t < 1f) anim = true
            drawCard(c, x, top, prev[i], shown[i], t)
            x += cardW + gap
        }
        if (anim) postInvalidateOnAnimation()
    }

    private fun drawCard(c: Canvas, x: Float, y: Float, from: Char, to: Char, t: Float) {
        val mid = y + cardH / 2
        val flipping = t < 1f && from != to
        // Static halves: new top always; old bottom until the flap lands.
        half(c, x, y, to, true, 0f)
        half(c, x, y, if (flipping) from else to, false, 0f)
        if (flipping) {
            var e = if (t < 0.5f) t / 0.5f else (t - 0.5f) / 0.5f
            e = e * e * (3 - 2 * e)
            if (t < 0.5f) half(c, x, y, from, true, -90f * e)           // old top falls forward
            else half(c, x, y, to, false, 90f * (1 - e))                // new bottom lands
        }
        // seam and hinge pins
        c.drawRect(x, mid - cardH * 0.012f, x + cardW, mid + cardH * 0.012f, seam)
        c.drawRect(x - cardH * 0.02f, mid - cardH * 0.035f, x + cardH * 0.025f, mid + cardH * 0.035f, pin)
        c.drawRect(x + cardW - cardH * 0.025f, mid - cardH * 0.035f, x + cardW + cardH * 0.02f, mid + cardH * 0.035f, pin)
    }

    /** One half card; `angle` rotates it around the middle hinge (X axis). */
    private fun half(c: Canvas, x: Float, y: Float, ch: Char, upper: Boolean, angle: Float) {
        val mid = y + cardH / 2
        c.save()
        if (angle != 0f) {
            camera.save()
            camera.setLocation(0f, 0f, -14f)   // inches; further away = gentler perspective
            camera.rotateX(angle)
            camera.getMatrix(m)
            camera.restore()
            m.preTranslate(-(x + cardW / 2), -mid)
            m.postTranslate(x + cardW / 2, mid)
            c.concat(m)
        }
        if (upper) c.clipRect(x - 1, y - 1, x + cardW + 1, mid)
        else c.clipRect(x - 1, mid, x + cardW + 1, y + cardH + 1)
        r.set(x, y, x + cardW, y + cardH)
        card.shader = LinearGradient(0f, y, 0f, y + cardH,
                cardColors,
                floatArrayOf(0f, 0.49f, 0.51f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, radius, radius, card)
        if (ch != ' ') c.drawText(ch.toString(), x + cardW / 2, mid - (text.descent() + text.ascent()) / 2, text)
        if (angle != 0f) {   // darken as it turns away from the light
            shade.color = (Math.min(1f, Math.abs(angle) / 90f) * 150).toInt() shl 24
            c.drawRect(r, shade)
        }
        c.restore()
    }

    private fun dp(v: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    }

    companion object {
        private const val FLIP_MS = 620L
    }
}
