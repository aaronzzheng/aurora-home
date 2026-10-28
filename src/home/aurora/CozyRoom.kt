package home.aurora

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Date
import kotlin.math.min

/**
 * Cozy: an evening in front of the fire.  The whole screen is a real open
 * fire (a photograph), its light breathing slowly over the room.  The time is
 * set in warm serif type in the dark of the hearth; the apps are lined up below
 * like pictures catching the firelight — the chosen one lifts and glows — and
 * the everyday controls sit on a bar of smoked glass that shows the fire
 * through it.  Light (by day) is the same home with the same layout.
 */
class CozyRoom(h: HomeActivity, t: HomeTheme) : Room(h, t) {

    private val serif = Room.tf("serif", Typeface.NORMAL)
    private val serifBold = Room.tf("serif", Typeface.BOLD)
    private val clockText = TextView(h)
    private var photo: Bitmap? = null
    private var blur: Bitmap? = null
    private lateinit var glow: View
    private var flicker: ObjectAnimator? = null

    override fun build() {
        // the photographs come off the main thread (cached after the first time); the room fills in when they land
        photo = plate(R.drawable.cozy_fire, true) { b -> photo = b; repaintAll() }
        blur = plate(R.drawable.cozy_fire_blur, false) { b -> blur = b; repaintAll() }

        art { c -> hearth(c) }

        // the fire's light on the room: one soft glow whose strength drifts
        glow = Glow(home)
        place(glow, 120f, 0f, 720f, 430f)

        // time and date in the dark, top left
        text(clockText, 66f, CREAM, serif)
        clockText.setShadowLayer(px(14f), 0f, px(2f), 0xCC000000.toInt())
        clockText.letterSpacing = -0.01f
        clock = ClockFace { s -> clockText.text = Room.time(s) }
        place(clockText, X0 - 3, 26f, 340f, -1f)
        text(date, 11.5f, AMBER, serifBold)
        date.letterSpacing = 0.18f
        date.setShadowLayer(px(8f), 0f, px(1f), 0xCC000000.toInt())
        place(date, X0, 112f, 400f, -1f)
        text(week, 10.5f, 0xB3FFE9C9.toInt(), serif)
        week.setShadowLayer(px(8f), 0f, px(1f), 0xCC000000.toInt())
        place(week, X0, 132f, 400f, -1f)

        // weather, top right
        val wx = LinearLayout(home)
        wx.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        text(sky, 10.5f, 0xCCFFE9C9.toInt(), serif)
        sky.gravity = Gravity.END
        sky.maxLines = 2
        sky.setLineSpacing(px(2f), 1f)
        sky.setPadding(0, 0, px(10f).toInt(), 0)
        text(temp, 34f, CREAM, serif)
        for (v in arrayOf(sky, temp)) v.setShadowLayer(px(10f), 0f, px(1f), 0xE6000000.toInt())
        wx.addView(sky)
        wx.addView(temp)
        weatherBox = wx
        place(wx, 500f, 34f, 960f - X0 - 500f, -1f)

        // the everyday controls, on the smoked glass bar
        val order = intArrayOf(GAMES, INPUTS, LIVE, TIMER, SLEEP, SETTINGS)
        for (i in order.indices) {
            val a = order[i]
            val x = BAR.left + 8 + i * (CW + 6)
            control(a, x, BAR.top + 7, CW, BAR.height() - 14) { c, w, hh, f -> button(c, w, hh, f, a) }
        }
    }

    override fun setApps(apps: List<Apps.App>, back: Apps.App?) {
        clearApps()
        val n = min(apps.size, SLOTS - 1)
        for (i in 0 until n) {
            val a = apps[i]
            val art = artOf(a)
            val isBack = back != null && i == 0
            val v = app(a, X0 + i * (IW + GAP), ROW_Y, IW, IH + 26) { c, w, _, f -> picture(c, w, f, art, if (isBack) "Back to " + art.label else art.label) }
            outline(v)
        }
        val ax = X0 + n * (IW + GAP)
        val all = Widget(home, this) { c, w, _, f -> allApps(c, w, f, ax) }
        all.contentDescription = "All apps"
        all.setOnClickListener { v -> home.act(ALL, v) }
        all.hook = Hook { has -> if (has) { home.actionFocused("All apps"); lift(all, 1.1f) } }
        appViews.add(all)
        place(all, ax, ROW_Y, IW, IH + 26)
        outline(all)
    }

    override fun onAppFocus(a: Apps.App, v: Widget) { lift(v, 1.1f) }

    override fun onActionFocus(action: Int, v: Widget) { lift(v, 1.05f) }

    override fun label(action: Int): String {
        val s = super.label(action)
        return if (action == SLEEP && s == "Sleep timer") "Sleep" else s
    }

    private fun outline(v: View) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, o: Outline) {
                o.setRoundRect(0, 0, view.width, Math.round(px(IH)), px(RAD))
                o.alpha = 0.95f
            }
        }
    }

    private fun lift(v: View, s: Float) {
        v.pivotX = v.width / 2f
        v.pivotY = if (v in appViews) px(IH / 2) else v.height / 2f
        v.animate().scaleX(s).scaleY(s).translationZ(px(10f)).setDuration(260).setInterpolator(DecelerateInterpolator(2f)).start()
        v.setOnFocusChangeListener { x, has -> if (!has) x.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(200).start() }
    }

    override fun tick(now: Date, hhmm: String) {
        super.tick(now, hhmm)
    }

    override fun start() {
        // firelight: an uneven, slow breathing, never a redraw of the picture
        val fl = ObjectAnimator.ofFloat(glow, View.ALPHA, 0.55f, 0.9f, 0.7f, 1f, 0.62f, 0.85f, 0.55f)
        fl.duration = 5200
        fl.interpolator = LinearInterpolator()
        fl.repeatCount = ValueAnimator.INFINITE
        flicker = fl
        fl.start()
    }

    override fun stop() { flicker?.cancel() }

    /** Back from an app: the room comes up out of the dark, as eyes adjust to the firelight. */
    override fun onReturn() {
        stage.alpha = 0.15f
        stage.animate().alpha(1f).setDuration(650).setInterpolator(DecelerateInterpolator(1.5f)).start()
    }

    // ------------------------------------------------------------------ painted once

    private fun hearth(c: Canvas) {
        val p = Room.paint()
        val photo = photo ?: return   // nothing until the photograph is in
        c.drawBitmap(photo, null, RectF(0f, 0f, W.toFloat(), H.toFloat()), p)
        // the dark of the room around the hearth, deeper where the text sits (soft-edged pools)
        shade(c, 150f, 90f, 420f, 230f, 0xC0080402.toInt())
        shade(c, 860f, 70f, 380f, 160f, 0xB3080402.toInt())
        p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, 170f, 0x66080402, 0x00080402, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), 170f, p)
        p.shader = LinearGradient(0f, 250f, 0f, H.toFloat(), intArrayOf(0x00080402, 0x99080402.toInt(), 0xF20A0503.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 250f, W.toFloat(), H.toFloat(), p)
        p.shader = RadialGradient(W / 2f, 230f, 640f, 0x00000000, 0x8C000000.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null

        holidayProps(c)

        // smoked glass for the controls: the fire blurred through dark glass
        val r = BAR
        val sh = Room.paint()
        sh.color = 0x99000000.toInt()
        sh.maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
        c.drawRoundRect(RectF(r.left + 4, r.top + 8, r.right - 4, r.bottom + 10), BAR_R, BAR_R, sh)
        val clip = Path()
        clip.addRoundRect(r, BAR_R, BAR_R, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        blur?.let { c.drawBitmap(it, null, RectF(0f, 0f, W.toFloat(), H.toFloat()), p) }
        p.color = 0xB31A0E07.toInt()
        c.drawRect(r, p)
        p.alpha = 255; p.shader = LinearGradient(0f, r.top, 0f, r.top + r.height() * 0.55f, 0x1FFFD9A0, 0x00FFD9A0, Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        p.shader = null
        c.restore()
        val e = Room.paint()
        e.style = Paint.Style.STROKE
        e.strokeWidth = 0.8f
        e.alpha = 255; e.shader = LinearGradient(0f, r.top, 0f, r.bottom, 0x66FFC88A, 0x14FFC88A, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(r.left + 0.4f, r.top + 0.4f, r.right - 0.4f, r.bottom - 0.4f), BAR_R, BAR_R, e)
    }

    /** A soft elliptical pool of shadow. */
    private fun shade(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        val g = RadialGradient(0f, 0f, 1f, intArrayOf(color, Room.alpha(color, 0.55f), color and 0xFFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        val m = android.graphics.Matrix()
        m.setScale(rx, ry)
        m.postTranslate(cx, cy)
        g.setLocalMatrix(m)
        val p = Room.paint()
        p.alpha = 255; p.shader = g
        c.drawRect(cx - rx, cy - ry, cx + rx, cy + ry, p)
    }

    private fun holidayProps(c: Canvas) {
        if (holiday == null) return
        when (holiday) {
            "halloween" -> {
                Room.pumpkin(c, 862f, BAR.top - 1, 15f)
                Room.pumpkin(c, 900f, BAR.top - 1, 10f)
                Room.bat(c, 640f, 56f, 18f, 0xF0050302.toInt())
                Room.bat(c, 700f, 92f, 12f, 0xF0050302.toInt())
                Room.bat(c, 590f, 104f, 9f, 0xF0050302.toInt())
            }
            "christmas" -> {
                Room.garland(c, -10f, 970f, 6f, 22f, true)
                stocking(c, 300f, 18f, 0xFFB8352E.toInt())
                stocking(c, 352f, 22f, 0xFF2F6A3C.toInt())
            }
            "newyear" -> Room.bunting(c, 300f, 980f, 6f, 26f, NEWYEAR)
            else -> Room.bunting(c, 300f, 980f, 6f, 26f, JULY4)
        }
    }

    // ------------------------------------------------------------------ apps and controls

    /** An app's banner as a picture in firelight: dim until chosen, then it lifts and glows. */
    private fun picture(c: Canvas, w: Float, on: Boolean, art: AppArt, name: String) {
        val r = RectF(0f, 0f, w, IH)
        val p = Room.paint()
        if (on) {
            p.color = 0xFF1A0E07.toInt()
            p.setShadowLayer(16f, 0f, 0f, 0xB3FF9A3A.toInt())
            c.drawRoundRect(r, RAD, RAD, p)
            p.clearShadowLayer()
        }
        val clip = Path()
        clip.addRoundRect(r, RAD, RAD, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        art.fill(c, r)
        // firelight from below; the rest of the room is in shadow
        p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, IH, if (on) 0x00000000 else 0x59000000, if (on) 0x26FF8A2A else 0x8C0A0503.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        p.shader = LinearGradient(0f, 0f, 0f, IH * 0.5f, if (on) 0x33FFF1D8 else 0x14FFF1D8, 0x00FFF1D8, Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        p.shader = null
        c.restore()
        p.style = Paint.Style.STROKE
        p.strokeWidth = if (on) 1.6f else 0.8f
        p.color = if (on) 0xFFFFC27A.toInt() else 0x33FFD9A0
        c.drawRoundRect(RectF(0.6f, 0.6f, w - 0.6f, IH - 0.6f), RAD, RAD, p)
        if (on) {
            val t = Room.textPaint(11.5f, CREAM, serif)
            t.setShadowLayer(6f, 0f, 1f, 0xE6000000.toInt())
            Room.centerText(c, Room.fit(name, t, w + 24), w / 2, IH + 20f, t)
        }
    }

    /** The last picture: every app, on smoked glass. */
    private fun allApps(c: Canvas, w: Float, on: Boolean, x: Float) {
        val r = RectF(0f, 0f, w, IH)
        val p = Room.paint()
        if (on) {
            p.color = 0xFF1A0E07.toInt()
            p.setShadowLayer(16f, 0f, 0f, 0xB3FF9A3A.toInt())
            c.drawRoundRect(r, RAD, RAD, p)
            p.clearShadowLayer()
        }
        val clip = Path()
        clip.addRoundRect(r, RAD, RAD, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        glass(c, r, x, ROW_Y)
        p.color = if (on) 0x8C3A2010.toInt() else 0xB3140A05.toInt()
        c.drawRect(r, p)
        c.restore()
        p.style = Paint.Style.STROKE
        p.strokeWidth = if (on) 1.6f else 0.8f
        p.color = if (on) 0xFFFFC27A.toInt() else 0x4DFFD9A0
        c.drawRoundRect(RectF(0.6f, 0.6f, w - 0.6f, IH - 0.6f), RAD, RAD, p)
        Room.icon(c, ALL, w / 2, IH / 2 - 9, 22f, if (on) 0xFFFFC27A.toInt() else CREAM, 1.8f)
        val t = Room.textPaint(11f, CREAM, serif)
        Room.centerText(c, "All apps", w / 2, IH / 2 + 20, t)
    }

    /** A control on the glass bar: an amber-lit tab when chosen. */
    private fun button(c: Canvas, w: Float, h: Float, on: Boolean, action: Int) {
        val r = RectF(0f, 0f, w, h)
        val p = Room.paint()
        if (on) {
            p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, h, 0xFFFFD08A.toInt(), 0xFFE8963E.toInt(), Shader.TileMode.CLAMP)
            p.setShadowLayer(12f, 0f, 0f, 0x99FF9A3A.toInt())
            c.drawRoundRect(r, h / 2, h / 2, p)
            p.clearShadowLayer()
            p.shader = LinearGradient(0f, 0f, 0f, h * 0.5f, 0x59FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(2f, 1f, w - 2, h * 0.55f), h / 2, h / 2, p)
            p.shader = null
        }
        val ink = if (on) 0xFF2A140A.toInt() else CREAM
        val t = Room.textPaint(12.5f, ink, if (on) serifBold else serif)
        val s = Room.fit(label(action), t, w - 44)
        val tw = t.measureText(s)
        val x0 = (w - (tw + 26)) / 2
        Room.icon(c, action, x0 + 8, h / 2, 15f, if (on) ink else AMBER, 1.5f)
        c.drawText(s, x0 + 26, h / 2 + 4.4f, t)
    }

    /** The blurred fire behind a piece of glass at design position (x, y). */
    private fun glass(c: Canvas, r: RectF, x: Float, y: Float) {
        val b = blur ?: return
        val sx = b.width / W.toFloat()
        val sy = b.height / H.toFloat()
        val src = Rect((x * sx).toInt(), (y * sy).toInt(), ((x + r.width()) * sx).toInt(), ((y + r.height()) * sy).toInt())
        c.drawBitmap(b, src, r, Room.paint())
    }

    /** The fire's light, a soft warm bloom; only its alpha changes, so nothing is repainted. */
    private class Glow(ctx: Context) : View(ctx) {
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun hasOverlappingRendering(): Boolean = false
        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val p = Room.paint()
            val g = RadialGradient(0f, 0f, 1f, intArrayOf(0x40FF9A3A, 0x1FFF7A20, 0x00FF7A20), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            val m = android.graphics.Matrix()
            m.setScale(w / 2, h / 2)
            m.postTranslate(w / 2, h / 2)
            g.setLocalMatrix(m)
            p.alpha = 255; p.shader = g
            c.drawRect(0f, 0f, w, h, p)
        }
    }

    companion object {
        private const val CREAM = 0xFFFFEBD0.toInt()
        private const val AMBER = 0xFFFFB766.toInt()
        private const val X0 = 46f
        private const val ROW_Y = 338f
        private const val IW = 132f
        private const val IH = 74f
        private const val GAP = 18f
        private const val RAD = 9f
        private const val SLOTS = 6
        private val BAR = RectF(X0, 462f, 960f - X0, 512f)
        private const val BAR_R = 25f
        private val CW = (BAR.width() - 16 - 5 * 6) / 6

        private fun stocking(c: Canvas, x: Float, y: Float, color: Int) {
            val p = Room.paint()
            p.color = 0xFFF3EEE4.toInt()
            c.drawRect(x - 12, y, x + 12, y + 10, p)
            p.color = color
            val s = Path()
            s.moveTo(x - 10, y + 10); s.lineTo(x + 10, y + 10); s.lineTo(x + 10, y + 40)
            s.quadTo(x + 14, y + 56, x - 4, y + 56); s.lineTo(x - 18, y + 56); s.quadTo(x - 26, y + 44, x - 10, y + 40); s.close()
            c.drawPath(s, p)
        }
    }
}
