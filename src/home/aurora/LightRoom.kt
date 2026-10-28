package home.aurora

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Date
import kotlin.math.min

/**
 * Light: a bright, quiet room in the morning sun (a photograph).  Sheer
 * curtains, white panelled walls, light falling across the floor.  Over it,
 * frosted white glass: the time on a pane at the top left, the weather at the
 * top right, the apps as banners that lift toward you with a real shadow, and
 * the everyday controls on a long frosted bar.  Cozy (after dark) is the same
 * home with the same layout.
 */
class LightRoom(h: HomeActivity, t: HomeTheme) : Room(h, t) {

    private val light = Room.tf("sans-serif-light", Typeface.NORMAL)
    private val regular = Room.tf("sans-serif", Typeface.NORMAL)
    private val medium = Room.tf("sans-serif-medium", Typeface.NORMAL)
    private val clockText = TextView(h)
    private var photo: Bitmap? = null
    private var blur: Bitmap? = null

    override fun build() {
        // the photographs come off the main thread (cached after the first time); the room fills in when they land
        photo = plate(R.drawable.light_room, true) { b -> photo = b; repaintAll() }
        blur = plate(R.drawable.light_room_blur, false) { b -> blur = b; repaintAll() }

        art { c -> studio(c) }

        // time and date on the frosted pane, top left
        text(clockText, 60f, INK, light)
        clockText.letterSpacing = -0.02f
        clock = ClockFace { s -> clockText.text = Room.time(s) }
        place(clockText, CLOCK.left + 20, CLOCK.top + 14, CLOCK.width() - 30, -1f)
        text(date, 13f, INK, medium)
        date.maxLines = 1
        place(date, CLOCK.left + 23, CLOCK.top + 92, CLOCK.width() - 36, -1f)
        text(week, 10.5f, SOFT, regular)
        week.maxLines = 1
        place(week, CLOCK.left + 23, CLOCK.top + 112, CLOCK.width() - 36, -1f)

        // the weather pane, top right: it hides with the weather setting
        val wx = Pane(home, this, WEATHER)
        wx.orientation = LinearLayout.HORIZONTAL
        wx.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        wx.setPadding(px(18f).toInt(), 0, px(20f).toInt(), 0)
        text(sky, 10.5f, SOFT, regular)
        sky.gravity = Gravity.END
        sky.maxLines = 2
        sky.setLineSpacing(px(2f), 1f)
        sky.setPadding(0, 0, px(12f).toInt(), 0)
        text(temp, 36f, INK, light)
        wx.addView(sky, LinearLayout.LayoutParams(0, -2, 1f))
        wx.addView(temp)
        wx.elevation = px(3f)
        wx.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, o: Outline) { o.setRoundRect(0, 0, view.width, view.height, px(PANE_R)); o.alpha = 0.5f }
        }
        weatherBox = wx
        place(wx, WEATHER.left, WEATHER.top, WEATHER.width(), WEATHER.height())

        // the everyday controls on the frosted bar
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
            val v = app(a, X0 + i * (IW + GAP), ROW_Y, IW, IH + 26) { c, w, _, f -> banner(c, w, f, art, if (isBack) "Back to " + art.label else art.label) }
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

    /** Banners cast a soft shadow on the room, deeper when they lift. */
    private fun outline(v: View) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, o: Outline) {
                o.setRoundRect(0, 0, view.width, Math.round(px(IH)), px(RAD))
                o.alpha = 0.6f
            }
        }
        v.elevation = px(2.5f)
    }

    private fun lift(v: View, s: Float) {
        v.pivotX = v.width / 2f
        v.pivotY = if (v in appViews) px(IH / 2) else v.height / 2f
        v.animate().scaleX(s).scaleY(s).translationZ(px(if (v in appViews) 16f else 4f)).setDuration(240).setInterpolator(DecelerateInterpolator(2f)).start()
        v.setOnFocusChangeListener { x, has -> if (!has) x.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(200).start() }
    }

    override fun tick(now: Date, hhmm: String) {
        super.tick(now, hhmm)
        date.text = Room.fmt("EEEE, MMMM d", now)
    }

    /** Back from an app: the room brightens out of a soft white, like eyes meeting the morning. */
    override fun onReturn() {
        val veil = View(home)
        veil.setBackgroundColor(0xFFFFFFFF.toInt())
        stage.addView(veil, FrameLayout.LayoutParams(-1, -1))
        veil.animate().alpha(0f).setDuration(420).withEndAction { stage.removeView(veil) }.start()
    }

    // ------------------------------------------------------------------ painted once

    private fun studio(c: Canvas) {
        val p = Room.paint()
        val photo = photo ?: return   // nothing until the photograph is in
        c.drawBitmap(photo, null, RectF(0f, 0f, W.toFloat(), H.toFloat()), p)
        // a breath of extra daylight from the window, and a gentle floor of shade for the apps
        p.alpha = 255; p.shader = LinearGradient(0f, 300f, 0f, H.toFloat(), 0x00F4EFE8, 0x40D8CFC4, Shader.TileMode.CLAMP)
        c.drawRect(0f, 300f, W.toFloat(), H.toFloat(), p)
        // a breath of morning light spilling from the window
        val m = android.graphics.Matrix()
        m.setScale(300f, 330f)
        m.postTranslate(540f, 210f)
        val bloom = android.graphics.RadialGradient(0f, 0f, 1f, intArrayOf(0x4DFFFDF8, 0x1AFFFDF8, 0x00FFFDF8), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        bloom.setLocalMatrix(m)
        p.alpha = 255; p.shader = bloom
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null
        holidayProps(c)
        frost(c, CLOCK, PANE_R, true)
        frost(c, BAR, BAR_R, true)
    }

    /** Frosted white glass: the room blurred behind it, a soft shadow below and a lit top edge. */
    private fun frost(c: Canvas, r: RectF, rad: Float, shadow: Boolean) {
        if (shadow) {
            val sh = Room.paint()
            sh.color = 0x2E3A2A1A
            sh.maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
            c.drawRoundRect(RectF(r.left + 6, r.top + 10, r.right - 6, r.bottom + 10), rad, rad, sh)
        }
        val p = Room.paint()
        val clip = Path()
        clip.addRoundRect(r, rad, rad, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        blur?.let { c.drawBitmap(it, null, RectF(0f, 0f, W.toFloat(), H.toFloat()), p) }
        p.color = 0xA6FFFFFF.toInt()
        c.drawRect(r, p)
        p.alpha = 255; p.shader = LinearGradient(0f, r.top, 0f, r.bottom, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        p.shader = null
        c.restore()
        val e = Room.paint()
        e.style = Paint.Style.STROKE
        e.strokeWidth = 1f
        e.alpha = 255; e.shader = LinearGradient(0f, r.top, 0f, r.bottom, 0xFFFFFFFF.toInt(), 0x59FFFFFF, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(r.left + 0.5f, r.top + 0.5f, r.right - 0.5f, r.bottom - 0.5f), rad, rad, e)
    }

    private fun holidayProps(c: Canvas) {
        if (holiday == null) return
        when (holiday) {
            "halloween" -> {
                Room.pumpkin(c, 862f, BAR.top + 1, 15f)
                Room.pumpkin(c, 900f, BAR.top + 1, 10f)
                Room.bat(c, 470f, 70f, 11f, 0xFF2A2630.toInt())
                Room.bat(c, 520f, 104f, 7f, 0xFF2A2630.toInt())
                Room.bat(c, 440f, 128f, 6f, 0xFF2A2630.toInt())
            }
            "christmas" -> {
                Room.garland(c, -10f, 970f, 4f, 22f, true)
                Room.wreath(c, 820f, 200f, 24f)
            }
            "newyear" -> Room.bunting(c, 360f, 720f, 4f, 24f, NEWYEAR)
            else -> Room.bunting(c, 360f, 720f, 4f, 24f, JULY4)
        }
    }

    // ------------------------------------------------------------------ apps and controls

    private fun banner(c: Canvas, w: Float, on: Boolean, art: AppArt, name: String) {
        val r = RectF(0f, 0f, w, IH)
        val clip = Path()
        clip.addRoundRect(r, RAD, RAD, Path.Direction.CW)
        val p = Room.paint()
        c.save()
        c.clipPath(clip)
        art.fill(c, r)
        p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, IH * 0.55f, if (on) 0x40FFFFFF else 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        p.shader = null
        c.restore()
        p.style = Paint.Style.STROKE
        p.strokeWidth = if (on) 2f else 0.8f
        p.color = if (on) 0xFFFFFFFF.toInt() else 0x66FFFFFF
        c.drawRoundRect(RectF(0.5f, 0.5f, w - 0.5f, IH - 0.5f), RAD, RAD, p)
        if (on) {
            p.strokeWidth = 1f
            p.color = 0x47000000
            c.drawRoundRect(RectF(-1f, -1f, w + 1, IH + 1), RAD + 1, RAD + 1, p)
            caption(c, name, w)
        }
    }

    private fun caption(c: Canvas, name: String, w: Float) {
        val t = Room.textPaint(11.5f, INK, medium)
        t.setShadowLayer(5f, 0f, 0f, 0xE6FFFFFF.toInt())
        Room.centerText(c, Room.fit(name, t, w + 24), w / 2, IH + 20f, t)
    }

    /** The last banner: every app, on frosted glass. */
    private fun allApps(c: Canvas, w: Float, on: Boolean, x: Float) {
        val r = RectF(0f, 0f, w, IH)
        val clip = Path()
        clip.addRoundRect(r, RAD, RAD, Path.Direction.CW)
        val p = Room.paint()
        c.save()
        c.clipPath(clip)
        glass(c, r, x, ROW_Y)
        p.color = if (on) 0xF2FFFFFF.toInt() else 0xB3FFFFFF.toInt()
        c.drawRect(r, p)
        c.restore()
        p.style = Paint.Style.STROKE
        p.strokeWidth = if (on) 2f else 1f
        p.color = 0xFFFFFFFF.toInt()
        c.drawRoundRect(RectF(0.5f, 0.5f, w - 0.5f, IH - 0.5f), RAD, RAD, p)
        Room.icon(c, ALL, w / 2, IH / 2 - 9, 22f, INK, 1.8f)
        val t = Room.textPaint(11.5f, INK, medium)
        Room.centerText(c, "All apps", w / 2, IH / 2 + 20, t)
    }

    /** A control on the frosted bar: a dark pill when chosen. */
    private fun button(c: Canvas, w: Float, h: Float, on: Boolean, action: Int) {
        val p = Room.paint()
        if (on) {
            p.color = INK
            p.setShadowLayer(8f, 0f, 3f, 0x40000000)
            c.drawRoundRect(RectF(0f, 0f, w, h), h / 2, h / 2, p)
            p.clearShadowLayer()
        }
        val ink = if (on) 0xFFFFFFFF.toInt() else INK
        val t = Room.textPaint(12.5f, ink, medium)
        val s = Room.fit(label(action), t, w - 44)
        val tw = t.measureText(s)
        val x0 = (w - (tw + 26)) / 2
        Room.icon(c, action, x0 + 8, h / 2, 15f, ink, 1.6f)
        c.drawText(s, x0 + 26, h / 2 + 4.4f, t)
    }

    /** The blurred room behind a piece of glass at design position (x, y). */
    private fun glass(c: Canvas, r: RectF, x: Float, y: Float) {
        val b = blur ?: return
        val sx = b.width / W.toFloat()
        val sy = b.height / H.toFloat()
        val src = Rect((x * sx).toInt(), (y * sy).toInt(), ((x + r.width()) * sx).toInt(), ((y + r.height()) * sy).toInt())
        c.drawBitmap(b, src, r, Room.paint())
    }

    /** The weather pane: frosted glass drawn behind its text, so it hides along with it. */
    private class Pane(ctx: Context, private val room: LightRoom, private val at: RectF) : LinearLayout(ctx) {
        init { setWillNotDraw(false) }
        override fun onDraw(c: Canvas) {
            val k = room.k
            c.save()
            c.scale(k, k)
            val r = RectF(0f, 0f, width / k, height / k)
            val clip = Path()
            clip.addRoundRect(r, PANE_R, PANE_R, Path.Direction.CW)
            c.clipPath(clip)
            room.glass(c, r, at.left, at.top)
            val p = Room.paint()
            p.color = 0xA6FFFFFF.toInt()
            c.drawRect(r, p)
            p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, r.bottom, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawRect(r, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1f
            p.color = 0xE6FFFFFF.toInt()
            c.drawRoundRect(RectF(0.5f, 0.5f, r.right - 0.5f, r.bottom - 0.5f), PANE_R, PANE_R, p)
            c.restore()
        }
    }

    companion object {
        private const val INK = 0xFF1F2126.toInt()
        private const val SOFT = 0xFF5C606A.toInt()
        private const val X0 = 46f
        private const val ROW_Y = 338f
        private const val IW = 132f
        private const val IH = 74f
        private const val GAP = 18f
        private const val RAD = 9f
        private const val SLOTS = 6
        private const val PANE_R = 20f
        private val CLOCK = RectF(X0 - 10, 28f, 300f, 166f)
        private val WEATHER = RectF(680f, 28f, 960f - X0 + 10, 98f)
        private val BAR = RectF(X0, 462f, 960f - X0, 512f)
        private const val BAR_R = 25f
        private val CW = (BAR.width() - 16 - 5 * 6) / 6
    }
}
