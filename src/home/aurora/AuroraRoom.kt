package home.aurora

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
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

/**
 * Aurora: a living-room home in the manner of Apple TV.  The wallpaper is a
 * full-bleed top shelf; the focused app (or control) is featured above a row
 * of rounded app icons that lift toward you with a real shadow, swing in from
 * the direction you moved and catch a single sweep of light.  The everyday
 * controls sit below as glass buttons.
 */
class AuroraRoom(h: HomeActivity, t: HomeTheme) : Room(h, t) {

    private companion object {
        const val X0 = 46f
        const val ROW_Y = 322f
        const val IW = 128f
        const val IH = 77f
        const val GAP = 20f
        const val SLOTS = 6
        const val R = 12f
        const val CTRL_Y = 452f
        const val CH = 44f
        const val CGAP = 14f
        const val INK = 0xFF1C1C1E.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        val CARD = RectF(592f, 120f, 912f, 300f)
        val ABOUT = arrayOf(
            "Chess, checkers, 2048 and more",
            "Switch to HDMI and other sources",
            "Channels from your antenna or cable",
            "A countdown or a stopwatch",
            "Turn the TV off a little later",
            "Looks, security, mornings and more",
            "Every app on this TV")
    }

    private val medium = tf("sans-serif-medium", Typeface.NORMAL)
    private val regular = tf("sans-serif", Typeface.NORMAL)
    private val light = tf("sans-serif-light", Typeface.NORMAL)
    private val clockText = TextView(h)
    private lateinit var hero: View
    private var fx: ThemeFx? = null

    // what the top shelf features
    private var heroArt: AppArt? = null
    private var heroAction = -1
    private var heroTitle = ""
    private var heroEyebrow = ""
    private var heroSub = ""

    private var shineT = 1f
    private var shine: ValueAnimator? = null
    private var lastX = Float.NaN

    override fun build() {
        // the wallpaper is the top shelf, edge to edge
        val viewport = FrameLayout(home)
        viewport.addView(home.wall, FrameLayout.LayoutParams(-1, -1))
        place(viewport, 0f, 0f, W.toFloat(), H.toFloat())
        if (theme.fx != ThemeFx.NONE || theme.wash != 0) {
            val f = ThemeFx(home, theme.fx, theme.wash)
            theme.sparks?.let { f.setFireworkColors(*it) }
            fx = f
            place(f, 0f, 0f, W.toFloat(), H.toFloat())
        }
        art { c -> shade(c) }
        hero = drawn({ c -> hero(c) }, 0f, 0f, W.toFloat(), 312f)

        // time, date and weather in the top right
        text(clockText, 34f, WHITE, light)
        clockText.gravity = Gravity.END
        clockText.setShadowLayer(px(8f), 0f, px(1f), 0x55000000)
        clock = ClockFace { s -> clockText.text = time(s) }
        place(clockText, 560f, 16f, 352f, -1f)
        text(date, 13f, 0xE6FFFFFF.toInt(), medium)
        date.gravity = Gravity.END
        date.setShadowLayer(px(6f), 0f, px(1f), 0x55000000)
        place(date, 560f, 56f, 352f, -1f)
        val wx = LinearLayout(home)
        wx.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        text(sky, 11f, 0xB3FFFFFF.toInt(), regular)
        sky.gravity = Gravity.END
        sky.maxLines = 2
        sky.setLineSpacing(px(2f), 1f)
        sky.setPadding(0, 0, px(8f).toInt(), 0)
        text(temp, 24f, WHITE, light)
        for (v in arrayOf(sky, temp)) v.setShadowLayer(px(6f), 0f, px(1f), 0x55000000)
        wx.addView(sky)
        wx.addView(temp)
        weatherBox = wx
        place(wx, 560f, 76f, 352f, -1f)

        // the everyday controls: a row of glass buttons
        val order = intArrayOf(GAMES, INPUTS, LIVE, TIMER, SLEEP, SETTINGS)
        val cw = (SLOTS * IW + (SLOTS - 1) * GAP - (order.size - 1) * CGAP) / order.size
        for (i in order.indices) {
            val a = order[i]
            val v = control(a, X0 + i * (cw + CGAP), CTRL_Y, cw, CH) { c, w, h, f -> glass(c, w, h, f, a) }
            outline(v, CH / 2, CH)
        }
    }

    override fun setApps(apps: List<Apps.App>, back: Apps.App?) {
        clearApps()
        val n = Math.min(apps.size, SLOTS - 1)
        for (i in 0 until n) {
            val a = apps[i]
            val art = artOf(a)
            val v = app(a, X0 + i * (IW + GAP), ROW_Y, IW, IH + 28) { c, w, _, f -> appIcon(c, w, f, art, art.label) }
            outline(v, R, IH)
        }
        // the last tile opens every app
        val all = Widget(home, this) { c, w, _, f -> appIcon(c, w, f, null, "All Apps") }
        all.contentDescription = "All apps"
        all.setOnClickListener { v -> home.act(ALL, v) }
        all.hook = Hook { has -> if (has) { home.actionFocused("All apps"); feature(null, ALL, "All Apps", "LIBRARY", ABOUT[ALL]); lift(all, 1.13f, IH) } }
        appViews.add(all)
        place(all, X0 + n * (IW + GAP), ROW_Y, IW, IH + 28)
        outline(all, R, IH)
        backApp = back
        if (apps.isNotEmpty()) featureApp(apps[0])
    }

    private var backApp: Apps.App? = null

    override fun onAppFocus(a: Apps.App, v: Widget) {
        featureApp(a)
        lift(v, 1.13f, IH)
    }

    override fun onActionFocus(action: Int, v: Widget) {
        feature(null, action, label(action), "AURORA", ABOUT[action])
        lift(v, 1.06f, CH)
    }

    private fun featureApp(a: Apps.App) {
        val back = backApp != null && backApp!!.pkg == a.pkg
        feature(artOf(a), -1, a.label, if (back) "CONTINUE" else "APP", if (back) "Pick up where you left off" else "Press OK to open  ·  hold OK for options")
    }

    private fun feature(art: AppArt?, action: Int, title: String, eyebrow: String, sub: String) {
        if (art === heroArt && action == heroAction && title == heroTitle) return
        heroArt = art
        heroAction = action
        heroTitle = title
        heroEyebrow = eyebrow
        heroSub = sub
        hero.invalidate()
        hero.alpha = 0.35f
        hero.animate().alpha(1f).setDuration(240).start()
    }

    override fun setStatus(timer: String, sleep: String) {
        super.setStatus(timer, sleep)
        if (heroAction == TIMER || heroAction == SLEEP) { heroTitle = label(heroAction); hero.invalidate() }
    }

    override fun tick(now: Date, hhmm: String) {
        super.tick(now, hhmm)
        date.text = fmt("EEEE, MMMM d", now)
    }

    override fun start() { fx?.start() }

    override fun stop() { fx?.stop() }

    /** Coming home: the screen settles back from just in front of you. */
    override fun onReturn() {
        stage.alpha = 0f
        stage.scaleX = 1.04f
        stage.scaleY = 1.04f
        stage.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(DecelerateInterpolator(2f)).start()
    }

    // ------------------------------------------------------------------ focus

    /** The rounded shape the system shadow is cast from. */
    private fun outline(v: View, radius: Float, height: Float) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, o: Outline) {
                o.setRoundRect(0, 0, view.width, Math.round(px(height)), px(radius))
                o.alpha = 0.9f
            }
        }
    }

    /** Lift toward the viewer, swing in from where focus came from, and catch the light once. */
    private fun lift(v: View, scale: Float, height: Float) {
        val cx = v.x + v.width / 2f
        val dir = if (lastX.isNaN() || Math.abs(cx - lastX) < 1) 0f else Math.signum(cx - lastX)
        lastX = cx
        v.pivotX = v.width / 2f
        v.pivotY = px(height / 2)
        v.cameraDistance = 9000 * home.resources.displayMetrics.density
        v.rotationY = -dir * 9f
        v.animate().scaleX(scale).scaleY(scale).rotationY(0f).translationZ(px(14f))
                .setDuration(360).setInterpolator(DecelerateInterpolator(2.2f)).start()
        v.setOnFocusChangeListener { x, has ->
            if (!has) x.animate().scaleX(1f).scaleY(1f).rotationY(0f).translationZ(0f).setDuration(220).start()
        }
        home.wall?.setParallax((0.5f - cx / px(W.toFloat())) * 2f)

        shine?.cancel()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 850
        a.startDelay = 140
        a.interpolator = DecelerateInterpolator(1.4f)
        a.addUpdateListener { x -> shineT = x.animatedValue as Float; v.invalidate() }
        shine = a
        shineT = 0f
        a.start()
    }

    // ------------------------------------------------------------------ drawing

    /** Darken the shelf where text and icons sit, like the scrims on a TV home. */
    private fun shade(c: Canvas) {
        val p = paint()
        p.shader = LinearGradient(0f, 150f, 0f, H.toFloat(), intArrayOf(0x00000000, 0x8C000000.toInt(), 0xD9000000.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 150f, W.toFloat(), H.toFloat(), p)
        p.shader = LinearGradient(0f, 0f, 560f, 0f, 0x73000000, 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 560f, 320f, p)
        p.shader = LinearGradient(0f, 0f, 0f, 120f, 0x59000000, 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), 120f, p)
    }

    private fun hero(c: Canvas) {
        val eb = textPaint(12f, 0xB3FFFFFF.toInt(), medium)
        eb.letterSpacing = 0.14f
        c.drawText(heroEyebrow, 48f, 180f, eb)
        val tp = textPaint(44f, WHITE, medium)
        tp.setShadowLayer(14f, 0f, 2f, 0x66000000)
        c.drawText(fit(heroTitle, tp, 480f), 46f, 226f, tp)
        val sp = textPaint(15f, 0xC0FFFFFF.toInt(), regular)
        sp.setShadowLayer(8f, 0f, 1f, 0x55000000)
        c.drawText(fit(heroSub, sp, 480f), 48f, 256f, sp)

        // the featured card, floating over the shelf
        val p = paint()
        p.color = 0xFF000000.toInt()
        p.setShadowLayer(26f, 0f, 12f, 0x8C000000.toInt())
        c.drawRoundRect(CARD, 18f, 18f, p)
        p.clearShadowLayer()
        val clip = Path()
        clip.addRoundRect(CARD, 18f, 18f, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        val art = heroArt
        if (art != null) art.fill(c, CARD)
        else {
            p.shader = LinearGradient(CARD.left, CARD.top, CARD.right, CARD.bottom, 0xFF3A3A40.toInt(), 0xFF141418.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(CARD, p)
            p.shader = null
            icon(c, if (heroAction < 0) ALL else heroAction, CARD.centerX(), CARD.centerY(), 74f, 0xF2FFFFFF.toInt(), 3.2f)
        }
        p.shader = LinearGradient(0f, CARD.top, 0f, CARD.top + CARD.height() * 0.5f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(CARD, p)
        c.restore()
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = 0x2EFFFFFF
        c.drawRoundRect(RectF(CARD.left + 0.5f, CARD.top + 0.5f, CARD.right - 0.5f, CARD.bottom - 0.5f), 18f, 18f, p)
    }

    private fun appIcon(c: Canvas, w: Float, on: Boolean, art: AppArt?, name: String) {
        val r = RectF(0f, 0f, w, IH)
        val clip = Path()
        clip.addRoundRect(r, R, R, Path.Direction.CW)
        val p = paint()
        c.save()
        c.clipPath(clip)
        if (art != null) art.fill(c, r)
        else {
            p.shader = LinearGradient(0f, 0f, 0f, IH, 0xFF5A5A62.toInt(), 0xFF2A2A30.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(r, p)
            p.shader = null
            icon(c, ALL, w / 2, IH / 2, 30f, WHITE, 2f)
        }
        // a pane of glass over the artwork: a soft top light, and one sweep when it lifts
        p.shader = LinearGradient(0f, 0f, 0f, IH * 0.6f, if (on) 0x38FFFFFF else 0x1AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        if (on && shineT < 1f) {
            val bx = -w * 0.4f + w * 1.8f * shineT
            p.shader = LinearGradient(bx - 34f, 0f, bx + 34f, IH * 0.6f, intArrayOf(0x00FFFFFF, 0x59FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawRect(r, p)
        }
        c.restore()
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.8f
        p.color = if (on) 0x4DFFFFFF else 0x1FFFFFFF
        c.drawRoundRect(RectF(0.4f, 0.4f, w - 0.4f, IH - 0.4f), R, R, p)
        if (on) {
            val tp = textPaint(12.5f, WHITE, medium)
            tp.setShadowLayer(6f, 0f, 1f, 0x80000000.toInt())
            centerText(c, fit(name, tp, w + 20), w / 2, IH + 22f, tp)
        }
    }

    private fun glass(c: Canvas, w: Float, h: Float, on: Boolean, action: Int) {
        val p = paint()
        val r = RectF(0f, 0f, w, h)
        if (on) {
            p.color = WHITE
            c.drawRoundRect(r, h / 2, h / 2, p)
        } else {
            p.shader = LinearGradient(0f, 0f, 0f, h, 0x47FFFFFF, 0x1FFFFFFF, Shader.TileMode.CLAMP)
            c.drawRoundRect(r, h / 2, h / 2, p)
            p.shader = LinearGradient(0f, 0f, 0f, h, 0x66FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 0.8f
            c.drawRoundRect(RectF(0.4f, 0.4f, w - 0.4f, h - 0.4f), h / 2, h / 2, p)
            p.shader = null
            p.style = Paint.Style.FILL
        }
        val ink = if (on) INK else WHITE
        icon(c, action, 25f, h / 2, 17f, ink, 1.7f)
        val tp = textPaint(13f, ink, medium)
        c.drawText(fit(label(action), tp, w - 52f), 44f, h / 2 + 4.6f, tp)
    }

    override fun label(action: Int): String {
        val s = super.label(action)
        return if (action == SLEEP && s == "Sleep timer") "Sleep" else s
    }

    override fun firstApp(): View? = if (appViews.isEmpty()) actions[GAMES] else appViews[0]
}
