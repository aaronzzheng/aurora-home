package home.aurora

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.widget.FrameLayout

/**
 * Frosted glass: draws the blurred wallpaper that sits behind it, a light tint,
 * and a hairline edge that's brighter at the top, then its children.
 */
open class GlassView(c: Context, private val wall: WallpaperView, private val radius: Float, tintColor: Int) : FrameLayout(c) {

    private val clip = Path()
    private val r = RectF()
    private val tint = Paint()
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val me = IntArray(2)
    private val bg = IntArray(2)
    private var style = HomeTheme.S_GLASS
    private var edgeColor = 0
    private var bevel: HomeTheme.Bevel? = null

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        tint.color = tintColor
        edge.style = Paint.Style.STROKE
        edge.strokeWidth = Math.max(1f, resources.displayMetrics.density * 0.75f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        wall.addFollower(this)
    }

    override fun onDetachedFromWindow() {
        wall.removeFollower(this)
        super.onDetachedFromWindow()
    }

    fun setTint(color: Int) {
        tint.color = color
        bevel?.set(color, false)
        invalidate()
    }

    /**
     * How the panel is drawn (see [HomeTheme]): frosted glass, a solid fill,
     * an old-Windows raised box, just an outline, or nothing. `edge` 0 keeps
     * the glass edge.
     */
    fun setStyle(style: Int, edgeC: Int) {
        this.style = style
        edgeColor = edgeC
        if (style == HomeTheme.S_BEVEL) {
            val b = HomeTheme.Bevel(context, 0xFFC0C0C0.toInt(), false)
            bevel = b
            if ((tint.color ushr 24) < 0xFF) tint.color = 0xFFC0C0C0.toInt()
            b.set(tint.color, false)
        }
        if (edgeC != 0) { edge.shader = null; edge.color = edgeC }
        invalidate()
    }

    private var plank: Paint? = null

    /** A wooden mantel shelf along the bottom for the tiles to sit on (Cozy). */
    private fun shelf(c: Canvas) {
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        val top = h - 18 * d
        val bot = h - 2 * d
        if (plank == null) plank = Paint(Paint.ANTI_ALIAS_FLAG)
        val plank = plank!!
        plank.shader = null
        plank.color = 0x80000000.toInt()   // shadow on the wall
        c.drawRect(8 * d, bot, w - 8 * d, bot + 10 * d, plank)
        plank.shader = LinearGradient(0f, top, 0f, bot, intArrayOf(0xFF9A6438.toInt(), 0xFF6E4223.toInt(), 0xFF4A2B15.toInt()),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, top, w, bot, plank)
        plank.shader = null
        plank.color = 0x66FFD9A0   // lamp light catching the front edge
        c.drawRect(0f, top, w, top + d, plank)
        plank.color = 0xFF3A2210.toInt()   // two little brackets
        c.drawRect(w * 0.12f, bot, w * 0.12f + 6 * d, bot + 14 * d, plank)
        c.drawRect(w * 0.88f - 6 * d, bot, w * 0.88f, bot + 14 * d, plank)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        r.set(0f, 0f, w.toFloat(), h.toFloat())
        clip.reset()
        clip.addRoundRect(r, radius, radius, Path.Direction.CW)
        if (edgeColor == 0) edge.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0x73FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP)
        bevel?.setBounds(0, 0, w, h)
    }

    override fun onDraw(c: Canvas) {
        if (style == HomeTheme.S_NONE) return
        if (style == HomeTheme.S_BEVEL) { bevel!!.draw(c); return }
        if (style == HomeTheme.S_SHELF) { shelf(c); return }
        if (style != HomeTheme.S_GLASS) {
            c.save()
            c.clipPath(clip)
            c.drawRect(r, tint)
            c.restore()
            if (edgeColor != 0) {
                edge.strokeWidth = Math.max(1f, resources.displayMetrics.density * (if (style == HomeTheme.S_OUTLINE) 1f else 1.5f))
                r.inset(edge.strokeWidth / 2, edge.strokeWidth / 2)
                c.drawRoundRect(r, radius, radius, edge)
                r.set(0f, 0f, width.toFloat(), height.toFloat())
            }
            return
        }
        getLocationInWindow(me)
        wall.getLocationInWindow(bg)
        val sx = scaleX
        val sy = scaleY
        val save = c.save()
        c.clipPath(clip)
        // getLocationInWindow is our scaled top-left on screen; undo our own scale
        // around the origin so one canvas unit is one screen pixel again.
        c.scale(1 / sx, 1 / sy)
        wall.drawBackdrop(c, (me[0] - bg[0]).toFloat(), (me[1] - bg[1]).toFloat())
        c.restoreToCount(save)
        c.save()
        c.clipPath(clip)
        c.drawRect(r, tint)
        c.restore()
        r.inset(edge.strokeWidth / 2, edge.strokeWidth / 2)
        c.drawRoundRect(r, radius, radius, edge)
        r.set(0f, 0f, width.toFloat(), height.toFloat())
    }
}
