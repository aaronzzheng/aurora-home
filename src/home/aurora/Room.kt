package home.aurora

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.math.roundToInt

/**
 * A whole home screen for one theme: every look gets its own room with its
 * own layout, controls, focus behavior and clock.  [HomeActivity]'s plain
 * layout remains only as a fallback for a look without one.
 *
 * Rooms are laid out on a fixed 960 × 540 design stage that is scaled to the
 * screen, so a composition always fits one TV viewport and never scrolls
 * vertically.  Static art is painted once into a bitmap; controls are small
 * views that only redraw when their focus or text changes.
 */
abstract class Room(h: HomeActivity, t: HomeTheme) {

    @JvmField val home: HomeActivity = h
    @JvmField val theme: HomeTheme = t
    @JvmField val k: Float          // px per design unit
    @JvmField val stage: FrameLayout
    @JvmField val holiday: String? = t.holiday   // "halloween", "christmas", "newyear", "july4" or null

    // Readouts the activity keeps current. Rooms attach the ones they show.
    @JvmField var clock: ClockFace = ClockFace { }
    @JvmField var date: TextView
    @JvmField var week: TextView
    @JvmField var temp: TextView
    @JvmField var sky: TextView
    @JvmField var remote: TextView
    @JvmField var focusTitle: TextView
    @JvmField var weatherBox: View
    @JvmField var timerText: String = "Timer"
    @JvmField var sleepText: String = "Sleep"
    @JvmField var hhmm: String = " 000"
    @JvmField var now: Date = Date()

    @JvmField val actions: Array<Widget?> = arrayOfNulls(ACTIONS.size)
    @JvmField val appViews: MutableList<Widget> = ArrayList()

    init {
        val m = h.resources.displayMetrics
        k = Math.min(Math.max(m.widthPixels, m.heightPixels) / W.toFloat(), Math.min(m.widthPixels, m.heightPixels) / H.toFloat())
        stage = FrameLayout(h)
        stage.clipChildren = false
        stage.clipToPadding = false
        date = TextView(h)
        week = TextView(h)
        temp = TextView(h)
        sky = TextView(h)
        remote = TextView(h)
        focusTitle = TextView(h)
        weatherBox = View(h)
    }

    /** Lay out the room: background art, clock, controls. Apps arrive later through [setApps]. */
    abstract fun build()

    /** Put the dock's apps into the room's app slots; `back` is the app just left (may be null). */
    abstract fun setApps(apps: List<@JvmSuppressWildcards Apps.App>, back: Apps.App?)

    /** Called when an app slot gets focus (after the activity's own handling). */
    open fun onAppFocus(a: Apps.App, v: Widget) {}

    /** Called when a control gets focus. */
    open fun onActionFocus(action: Int, v: Widget) {}

    /** Every minute (and when the clock format changes). */
    open fun tick(now: Date, hhmm: String) {
        this.now = now
        this.hhmm = hhmm
        clock.setTime(hhmm)
        date.text = fmt("EEEE · MMMM d", now).uppercase(Locale.getDefault())
    }

    /** Timer and sleep text changed. */
    open fun setStatus(timer: String, sleep: String) {
        if (timer == timerText && sleep == sleepText) return
        timerText = timer
        sleepText = sleep
        if (actions[TIMER] != null) actions[TIMER]!!.invalidate()
        if (actions[SLEEP] != null) actions[SLEEP]!!.invalidate()
    }

    /** The room's short, theme-native transition when coming back from an app. */
    open fun onReturn() {
        stage.alpha = 0f
        stage.animate().alpha(1f).setDuration(260).start()
    }

    open fun start() {}
    open fun stop() {}

    /** The first app slot, used to land on "Back to". */
    open fun firstApp(): View? = if (appViews.isEmpty()) actions[GAMES] else appViews[0]

    open fun label(action: Int): String {
        if (action == TIMER) return timerText
        if (action == SLEEP) return sleepText
        return ACTIONS[action]
    }

    // ------------------------------------------------------------------ layout helpers

    open fun px(design: Float): Float = design * k

    /** Place `v` at design coordinates; a negative size means wrap content. */
    open fun <V : View> place(v: V, x: Float, y: Float, w: Float, h: Float): V {
        val lp = FrameLayout.LayoutParams(if (w < 0) -2 else (w * k).roundToInt(), if (h < 0) -2 else (h * k).roundToInt(), Gravity.TOP or Gravity.LEFT)
        lp.leftMargin = (x * k).roundToInt()
        lp.topMargin = (y * k).roundToInt()
        if (v.parent == null) stage.addView(v, lp)
        else v.layoutParams = lp
        return v
    }

    open fun text(t: TextView, size: Float, color: Int, tf: Typeface?): TextView {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * k)
        t.setTextColor(color)
        t.typeface = tf
        t.includeFontPadding = false
        return t
    }

    open fun text(s: String?, size: Float, color: Int, tf: Typeface?): TextView {
        val t = text(TextView(home), size, color, tf)
        t.text = s
        return t
    }

    /** A flip clock whose cards are `height` design units tall. */
    open fun flip(height: Float): FlipClock {
        return FlipClock(home, height * k / home.resources.displayMetrics.density)
    }

    /** A full-stage picture painted once into a bitmap. */
    open fun art(a: Art): View {
        val v: View = Cached(home, this, a)
        place(v, 0f, 0f, W.toFloat(), H.toFloat())
        return v
    }

    /** Paint `v` again: art made by [art] redraws its bitmap, anything else just invalidates. */
    open fun repaint(v: View) { if (v is Cached) v.repaint() else v.invalidate() }

    /** Paint the whole stage again, art and glass alike: a plate they draw has just arrived. */
    open fun repaintAll() { repaintTree(stage) }

    private fun repaintTree(v: View) {
        repaint(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) repaintTree(v.getChildAt(i))
    }

    /**
     * A bundled photo plate sized for this screen (`w` × `h` px; the whole stage by default),
     * decoded off the main thread and kept across rebuilds. Null until it's ready, then
     * `ready` runs on the main thread. Shared: never recycle it.
     */
    open fun plate(id: Int, opaque: Boolean, w: Int = (W * k).roundToInt(), h: Int = (H * k).roundToInt(), ready: (Bitmap) -> Unit): Bitmap? =
        Bitmaps.plate(home.resources, id, w, h, opaque, ready)

    /** A small picture that is redrawn only when invalidated. */
    open fun drawn(a: Art, x: Float, y: Float, w: Float, h: Float): View {
        val v: View = object : View(home) {
            override fun onDraw(c: Canvas) {
                c.save()
                c.scale(k, k)
                a.draw(c)
                c.restore()
            }
        }
        v.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        place(v, x, y, w, h)
        return v
    }

    open fun control(action: Int, x: Float, y: Float, w: Float, h: Float, face: Face): Widget {
        val v = Widget(home, this, face)
        v.contentDescription = ACTIONS[action]
        v.setOnClickListener { home.act(action, v) }
        v.hook = Hook { has -> if (has) { home.actionFocused(ACTIONS[action]); onActionFocus(action, v) } }
        actions[action] = v
        place(v, x, y, w, h)
        return v
    }

    open fun app(a: Apps.App, x: Float, y: Float, w: Float, h: Float, face: Face): Widget {
        val v = Widget(home, this, face)
        v.tag = a.pkg
        v.contentDescription = a.label
        v.setOnClickListener { home.launchApp(a, v) }
        v.setOnLongClickListener { home.appOptions(a.pkg); true }
        v.hook = Hook { has -> if (has) { home.appFocused(a); onAppFocus(a, v) } }
        appViews.add(v)
        place(v, x, y, w, h)
        return v
    }

    open fun clearApps() {
        for (v in appViews) if (v.parent != null) (v.parent as FrameLayout).removeView(v)
        appViews.clear()
    }

    // ------------------------------------------------------------------ drawing helpers

    fun interface Art { fun draw(c: Canvas) }

    /** Draws a control or app slot in design units; `w`/`h` are its size. */
    fun interface Face { fun draw(c: Canvas, w: Float, h: Float, focused: Boolean) }

    fun interface Hook { fun focus(has: Boolean) }

    /** A focusable, drawn control. Redraws only when focus or its text changes. */
    class Widget(c: Context, r: Room, f: Face) : View(c) {
        @JvmField val face: Face = f
        @JvmField val room: Room = r
        @JvmField var hook: Hook? = null
        @JvmField var on: Boolean = false

        init {
            isFocusable = true
            // a TV home always shows what is selected, even if the window starts in touch mode
            isFocusableInTouchMode = true
            isClickable = true
            defaultFocusHighlightEnabled = false
        }

        override fun onFocusChanged(gain: Boolean, dir: Int, prev: Rect?) {
            super.onFocusChanged(gain, dir, prev)
            on = gain
            invalidate()
            if (hook != null) hook!!.focus(gain)
        }

        override fun onDraw(c: Canvas) {
            c.save()
            c.scale(room.k, room.k)
            room.face(this).draw(c, width / room.k, height / room.k, on)
            c.restore()
        }
    }

    open fun face(w: Widget): Face = w.face

    private class Cached(c: Context, private val room: Room, private val art: Art) : View(c) {
        private var bmp: Bitmap? = null

        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            if (w <= 0 || h <= 0) return
            if (bmp != null) bmp!!.recycle()
            bmp = null
            paint()
        }

        private fun paint() {
            val w = width
            val h = height
            if (w <= 0 || h <= 0) return
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.scale(room.k, room.k)
            art.draw(c)
            bmp = b
        }

        /** Paint it afresh on the next frame (the old bitmap is left to GC: the render thread may still hold it). */
        fun repaint() {
            bmp = null
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            // painted lazily too, so coming back after a detach (which frees the bitmap) isn't blank
            if (bmp == null || bmp!!.isRecycled) paint()
            if (bmp != null) c.drawBitmap(bmp!!, 0f, 0f, null)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            if (bmp != null) { bmp!!.recycle(); bmp = null }
        }
    }

    /** An app's TV banner or icon, ready to draw at any size. */
    class AppArt(a: Apps.App, apps: Apps) {
        @JvmField val banner: Drawable? = a.banner(apps.pm())
        @JvmField val icon: Drawable? = a.icon(apps.pm())
        @JvmField val color: Int = Tile.averageColor(banner ?: icon!!)
        @JvmField val label: String = a.label

        /** Banner cropped to fill `r`; apps without a banner get their icon on their own color. */
        fun fill(c: Canvas, r: RectF) {
            c.save()
            c.clipRect(r)
            if (banner != null) {
                crop(c, banner, r)
            } else {
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.alpha = 255; p.shader = LinearGradient(r.left, r.top, r.right, r.bottom, Tile.lighten(color, 0.15f), Tile.darken(color, 0.4f), Shader.TileMode.CLAMP)
                c.drawRect(r, p)
                val s = Math.min(r.width(), r.height()) * 0.5f
                drawIcon(c, r.centerX(), r.centerY(), s)
            }
            c.restore()
        }

        fun drawIcon(c: Canvas, cx: Float, cy: Float, size: Float) {
            val d = icon ?: banner ?: return
            val iw = Math.max(1, d.intrinsicWidth).toFloat()
            val ih = Math.max(1, d.intrinsicHeight).toFloat()
            val s = size / Math.max(iw, ih)
            c.save()
            c.translate(cx, cy)
            c.scale(s, s)
            d.setBounds((-iw / 2).roundToInt(), (-ih / 2).roundToInt(), (iw / 2).roundToInt(), (ih / 2).roundToInt())
            d.draw(c)
            c.restore()
        }

        private companion object {
            fun crop(c: Canvas, d: Drawable, r: RectF) {
                var iw = Math.max(1, d.intrinsicWidth).toFloat()
                var ih = Math.max(1, d.intrinsicHeight).toFloat()
                if (d.intrinsicWidth <= 0) { iw = 320f; ih = 180f }
                val s = Math.max(r.width() / iw, r.height() / ih)
                c.save()
                c.translate(r.centerX(), r.centerY())
                c.scale(s, s)
                d.setBounds((-iw / 2).roundToInt(), (-ih / 2).roundToInt(), (iw / 2).roundToInt(), (ih / 2).roundToInt())
                d.draw(c)
                c.restore()
            }
        }
    }

    open fun artOf(a: Apps.App): AppArt {
        val key = a.pkg + "|" + a.info.applicationInfo.sourceDir
        var art = artCache[key]
        if (art == null) {
            if (artCache.size > 60) artCache.clear()
            art = AppArt(a, home.apps!!)
            artCache[key] = art
        }
        return art
    }

    companion object {
        const val GAMES = 0
        const val INPUTS = 1
        const val LIVE = 2
        const val TIMER = 3
        const val SLEEP = 4
        const val SETTINGS = 5
        const val ALL = 6
        @JvmField val ACTIONS: Array<String> = arrayOf("Games", "Inputs", "Live TV", "Timer", "Sleep", "Settings", "All apps")
        const val W = 960
        const val H = 540

        // Banners, icons and their colors survive the home screen being rebuilt (coming
        // back from an app, a theme change). Keyed by where the app is installed, which
        // changes when it's updated, so a new banner shows up.
        private val artCache: MutableMap<String, AppArt> = HashMap()

        @JvmStatic
        fun create(h: HomeActivity, t: HomeTheme): Room? {
            return when (t.id) {
                "retro" -> RetroRoom(h, t)
                "classic" -> ClassicRoom(h, t)
                "aurora" -> AuroraRoom(h, t)
                "art" -> ArtRoom(h, t)
                "space" -> SpaceRoom(h, t)
                "cozy" -> CozyRoom(h, t)
                "minimal" -> MinimalRoom(h, t)
                "light" -> LightRoom(h, t)
                "cs" -> TerminalRoom(h, t)
                else -> null   // HomeActivity falls back to its plain layout
            }
        }

        @JvmStatic fun paint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        @JvmStatic
        fun textPaint(size: Float, color: Int, tf: Typeface?): Paint {
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
            p.textSize = size
            p.color = color
            p.typeface = tf
            return p
        }

        @JvmStatic
        fun fit(s: String?, p: Paint, max: Float): String {
            var s = s ?: return ""
            if (p.measureText(s) <= max) return s
            while (s.length > 2 && p.measureText("$s…") > max) s = s.substring(0, s.length - 1)
            return s.trim { it <= ' ' } + "…"
        }

        @JvmStatic
        fun centerText(c: Canvas, s: String, cx: Float, baseline: Float, p: Paint) {
            val a = p.textAlign
            p.textAlign = Paint.Align.CENTER
            c.drawText(s, cx, baseline, p)
            p.textAlign = a
        }

        @JvmStatic fun alpha(color: Int, a: Float): Int = HomeTheme.alpha(color, a)

        @JvmStatic fun tf(family: String?, style: Int): Typeface = Typeface.create(family, style)

        /** Time as "4:07" from the activity's "0407"/" 407" string. */
        @JvmStatic
        fun time(hhmm: String): String {
            val s = hhmm.trim { it <= ' ' }
            if (s.length < 3) return s
            return s.substring(0, s.length - 2) + ":" + s.substring(s.length - 2)
        }

        private val formats = HashMap<String, SimpleDateFormat>()
        private var formatsLocale: Locale? = null

        /** `d` in `pattern`; the formatters are kept, and rebuilt when the language changes. */
        @JvmStatic
        fun fmt(pattern: String, d: Date): String {
            synchronized(formats) {
                val l = Locale.getDefault()
                if (l != formatsLocale) { formats.clear(); formatsLocale = l }
                return formats.getOrPut(pattern) { SimpleDateFormat(pattern, l) }.format(d)
            }
        }

        /** A simple line icon for an everyday control, `s` units across. */
        @JvmStatic
        fun icon(c: Canvas, action: Int, cx: Float, cy: Float, s: Float, color: Int, stroke: Float) {
            val p = paint()
            p.color = color
            p.style = Paint.Style.STROKE
            p.strokeWidth = stroke
            p.strokeCap = Paint.Cap.ROUND
            p.strokeJoin = Paint.Join.ROUND
            val h = s / 2
            when (action) {
                GAMES -> {
                    val g = Path()
                    g.moveTo(cx - h * 0.62f, cy - h * 0.38f)
                    g.lineTo(cx + h * 0.62f, cy - h * 0.38f)
                    g.quadTo(cx + h, cy - h * 0.38f, cx + h * 0.95f, cy + h * 0.2f)
                    g.quadTo(cx + h * 0.9f, cy + h * 0.62f, cx + h * 0.55f, cy + h * 0.45f)
                    g.lineTo(cx + h * 0.3f, cy + h * 0.2f)
                    g.lineTo(cx - h * 0.3f, cy + h * 0.2f)
                    g.lineTo(cx - h * 0.55f, cy + h * 0.45f)
                    g.quadTo(cx - h * 0.9f, cy + h * 0.62f, cx - h * 0.95f, cy + h * 0.2f)
                    g.quadTo(cx - h, cy - h * 0.38f, cx - h * 0.62f, cy - h * 0.38f)
                    c.drawPath(g, p)
                    c.drawLine(cx - h * 0.62f, cy - h * 0.1f, cx - h * 0.3f, cy - h * 0.1f, p)
                    c.drawLine(cx - h * 0.46f, cy - h * 0.26f, cx - h * 0.46f, cy + h * 0.06f, p)
                    p.style = Paint.Style.FILL
                    c.drawCircle(cx + h * 0.42f, cy - h * 0.16f, stroke * 0.8f, p)
                    c.drawCircle(cx + h * 0.6f, cy + h * 0.02f, stroke * 0.8f, p)
                }
                INPUTS -> {
                    c.drawRoundRect(cx - h * 0.9f, cy - h * 0.55f, cx + h * 0.5f, cy + h * 0.55f, h * 0.15f, h * 0.15f, p)
                    c.drawLine(cx - h * 0.2f, cy, cx + h, cy, p)
                    c.drawLine(cx + h * 0.65f, cy - h * 0.32f, cx + h, cy, p)
                    c.drawLine(cx + h * 0.65f, cy + h * 0.32f, cx + h, cy, p)
                }
                LIVE -> {
                    c.drawRoundRect(cx - h, cy - h * 0.45f, cx + h, cy + h * 0.7f, h * 0.15f, h * 0.15f, p)
                    c.drawLine(cx, cy - h * 0.45f, cx - h * 0.45f, cy - h, p)
                    c.drawLine(cx, cy - h * 0.45f, cx + h * 0.45f, cy - h, p)
                }
                TIMER -> {
                    c.drawCircle(cx, cy + h * 0.1f, h * 0.82f, p)
                    c.drawLine(cx, cy + h * 0.1f, cx, cy - h * 0.38f, p)
                    c.drawLine(cx, cy + h * 0.1f, cx + h * 0.32f, cy + h * 0.3f, p)
                    c.drawLine(cx - h * 0.25f, cy - h, cx + h * 0.25f, cy - h, p)
                }
                SLEEP -> {
                    val m = Path()
                    m.addCircle(cx, cy, h * 0.85f, Path.Direction.CW)
                    val cut = Path()
                    cut.addCircle(cx + h * 0.45f, cy - h * 0.35f, h * 0.7f, Path.Direction.CW)
                    m.op(cut, Path.Op.DIFFERENCE)
                    c.drawPath(m, p)
                }
                SETTINGS -> {
                    c.drawCircle(cx, cy, h * 0.32f, p)
                    for (i in 0 until 8) {
                        val a = i * Math.PI / 4
                        c.drawLine(cx + Math.cos(a).toFloat() * h * 0.58f, cy + Math.sin(a).toFloat() * h * 0.58f,
                                cx + Math.cos(a).toFloat() * h * 0.92f, cy + Math.sin(a).toFloat() * h * 0.92f, p)
                    }
                    c.drawCircle(cx, cy, h * 0.62f, p)
                }
                else -> {   // all apps: a 3x3 grid of dots
                    p.style = Paint.Style.FILL
                    for (i in 0 until 9) c.drawCircle(cx + (i % 3 - 1) * h * 0.7f, cy + (i / 3 - 1) * h * 0.7f, stroke * 1.1f, p)
                }
            }
        }

        // ------------------------------------------------------------------ holiday props
        // Each room places these on its own furniture; nothing here is a full-screen effect.

        /** A carved pumpkin sitting on a surface at `baseY`. */
        @JvmStatic
        fun pumpkin(c: Canvas, cx: Float, baseY: Float, r: Float) {
            val p = paint()
            val cy = baseY - r * 0.82f
            p.color = 0x55000000
            c.drawOval(cx - r * 1.1f, baseY - r * 0.12f, cx + r * 1.1f, baseY + r * 0.1f, p)
            val ribs = intArrayOf(0xFFB8480F.toInt(), 0xFFD9621A.toInt(), 0xFFE87A26.toInt(), 0xFFD9621A.toInt(), 0xFFB8480F.toInt())
            for (i in ribs.indices) {
                val off = (i - 2) * r * 0.36f
                val rw = r * (if (i == 2) 0.55f else 0.5f)
                p.color = ribs[i]
                c.drawOval(cx + off - rw, cy - r * 0.82f, cx + off + rw, cy + r * 0.82f, p)
            }
            p.color = 0xFF4F5A22.toInt()
            c.drawRoundRect(cx - r * 0.08f, cy - r * 1.08f, cx + r * 0.1f, cy - r * 0.72f, r * 0.05f, r * 0.05f, p)
            // a warm candle face
            p.color = 0xFFFFD27A.toInt()
            val f = Path()
            f.moveTo(cx - r * 0.42f, cy - r * 0.16f); f.lineTo(cx - r * 0.2f, cy - r * 0.38f); f.lineTo(cx - r * 0.1f, cy - r * 0.1f); f.close()
            f.moveTo(cx + r * 0.42f, cy - r * 0.16f); f.lineTo(cx + r * 0.2f, cy - r * 0.38f); f.lineTo(cx + r * 0.1f, cy - r * 0.1f); f.close()
            c.drawPath(f, p)
            val m = Path()
            m.moveTo(cx - r * 0.5f, cy + r * 0.18f)
            m.quadTo(cx, cy + r * 0.62f, cx + r * 0.5f, cy + r * 0.18f)
            m.lineTo(cx + r * 0.3f, cy + r * 0.3f); m.lineTo(cx + r * 0.18f, cy + r * 0.2f); m.lineTo(cx + r * 0.05f, cy + r * 0.34f)
            m.lineTo(cx - r * 0.1f, cy + r * 0.22f); m.lineTo(cx - r * 0.24f, cy + r * 0.34f); m.close()
            c.drawPath(m, p)
        }

        /** An evergreen garland with warm bulbs, hanging from (x1,y) to (x2,y). */
        @JvmStatic
        fun garland(c: Canvas, x1: Float, x2: Float, y: Float, sag: Float, bulbs: Boolean) {
            val p = paint()
            val r = Random(11)
            val n = ((x2 - x1) / 4).toInt()
            for (i in 0..n) {
                val t = i / n.toFloat()
                val x = x1 + (x2 - x1) * t
                val yy = y + sag * 4 * t * (1 - t)
                p.color = if (r.nextBoolean()) 0xFF1F4A2C.toInt() else 0xFF2C6038.toInt()
                c.drawCircle(x + r.nextFloat() * 3 - 1.5f, yy + r.nextFloat() * 3 - 1.5f, 4.2f, p)
            }
            if (!bulbs) return
            val col = intArrayOf(0xFFE5483B.toInt(), 0xFFF2C14E.toInt(), 0xFFEFE6D2.toInt())
            for (i in 1 until 12) {
                val t = i / 12f
                val x = x1 + (x2 - x1) * t
                val yy = y + sag * 4 * t * (1 - t) + 4
                p.color = alpha(col[i % 3], 0.35f)
                c.drawCircle(x, yy, 5.5f, p)
                p.color = col[i % 3]
                c.drawCircle(x, yy, 2.2f, p)
            }
        }

        @JvmStatic
        fun wreath(c: Canvas, cx: Float, cy: Float, r: Float) {
            val p = paint()
            val rnd = Random(3)
            for (i in 0 until 70) {
                val a = i / 70.0 * Math.PI * 2
                p.color = if (rnd.nextBoolean()) 0xFF1F4A2C.toInt() else 0xFF2F6A3C.toInt()
                c.drawCircle(cx + Math.cos(a).toFloat() * r, cy + Math.sin(a).toFloat() * r, r * 0.26f, p)
            }
            p.color = 0xFFC23A30.toInt()
            for (i in 0 until 8) {
                val a = i / 8.0 * Math.PI * 2 + 0.3
                c.drawCircle(cx + Math.cos(a).toFloat() * r, cy + Math.sin(a).toFloat() * r, r * 0.08f, p)
            }
            val bow = Path()
            bow.moveTo(cx, cy + r)
            bow.lineTo(cx - r * 0.4f, cy + r * 0.75f); bow.lineTo(cx - r * 0.4f, cy + r * 1.25f); bow.close()
            bow.moveTo(cx, cy + r)
            bow.lineTo(cx + r * 0.4f, cy + r * 0.75f); bow.lineTo(cx + r * 0.4f, cy + r * 1.25f); bow.close()
            c.drawPath(bow, p)
        }

        /** Pennant bunting between two points. */
        @JvmStatic
        fun bunting(c: Canvas, x1: Float, x2: Float, y: Float, sag: Float, colors: IntArray) {
            val p = paint()
            p.color = 0x99FFFFFF.toInt()
            p.strokeWidth = 1.2f
            val n = Math.max(4, ((x2 - x1) / 30).toInt())
            var px0 = x1
            var py0 = y
            for (i in 1..n) {
                val t = i / n.toFloat()
                val x = x1 + (x2 - x1) * t
                val yy = y + sag * 4 * t * (1 - t)
                c.drawLine(px0, py0, x, yy, p)
                px0 = x; py0 = yy
            }
            for (i in 0 until n) {
                val t0 = (i + 0.15f) / n
                val t1 = (i + 0.85f) / n
                val tm = (i + 0.5f) / n
                val ax = x1 + (x2 - x1) * t0
                val ay = y + sag * 4 * t0 * (1 - t0)
                val bx = x1 + (x2 - x1) * t1
                val by = y + sag * 4 * t1 * (1 - t1)
                val mx = x1 + (x2 - x1) * tm
                val my = y + sag * 4 * tm * (1 - tm) + 18
                val f = Path()
                f.moveTo(ax, ay); f.lineTo(bx, by); f.lineTo(mx, my); f.close()
                p.color = colors[i % colors.size]
                c.drawPath(f, p)
            }
        }

        /** A still firework burst (printed, not animated). */
        @JvmStatic
        fun burst(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
            val p = paint()
            p.strokeCap = Paint.Cap.ROUND
            for (i in 0 until 16) {
                val a = i / 16.0 * Math.PI * 2
                val cos = Math.cos(a).toFloat()
                val sin = Math.sin(a).toFloat()
                p.strokeWidth = 1.6f
                p.color = alpha(color, 0.75f)
                c.drawLine(cx + cos * r * 0.35f, cy + sin * r * 0.35f, cx + cos * r, cy + sin * r, p)
                p.color = color
                c.drawCircle(cx + cos * r * 1.08f, cy + sin * r * 1.08f, 1.6f, p)
            }
        }

        @JvmStatic
        fun bat(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
            val p = paint()
            p.color = color
            val b = Path()
            b.moveTo(cx, cy - s * 0.15f)
            b.quadTo(cx - s * 0.5f, cy - s * 0.6f, cx - s, cy - s * 0.2f)
            b.quadTo(cx - s * 0.75f, cy - s * 0.1f, cx - s * 0.7f, cy + s * 0.15f)
            b.quadTo(cx - s * 0.45f, cy - s * 0.05f, cx - s * 0.3f, cy + s * 0.2f)
            b.quadTo(cx - s * 0.15f, cy, cx, cy + s * 0.25f)
            b.quadTo(cx + s * 0.15f, cy, cx + s * 0.3f, cy + s * 0.2f)
            b.quadTo(cx + s * 0.45f, cy - s * 0.05f, cx + s * 0.7f, cy + s * 0.15f)
            b.quadTo(cx + s * 0.75f, cy - s * 0.1f, cx + s, cy - s * 0.2f)
            b.quadTo(cx + s * 0.5f, cy - s * 0.6f, cx, cy - s * 0.15f)
            c.drawPath(b, p)
        }

        @JvmStatic
        fun glow(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
            val p = paint()
            p.alpha = 255; p.shader = RadialGradient(cx, cy, r, color, color and 0xFFFFFF, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
        }

        @JvmStatic
        fun holidayAccent(h: String?): Int {
            if (h == null) return 0
            return when (h) {
                "halloween" -> 0xFFE8742A.toInt()
                "christmas" -> 0xFFC23A30.toInt()
                "newyear" -> 0xFFE9C46A.toInt()
                else -> 0xFFC23A30.toInt()
            }
        }

        @JvmField val JULY4: IntArray = intArrayOf(0xFFB8352E.toInt(), 0xFFF3EEE4.toInt(), 0xFF2D4A7A.toInt())
        @JvmField val NEWYEAR: IntArray = intArrayOf(0xFFE9C46A.toInt(), 0xFFF3EEE4.toInt(), 0xFFC9A24A.toInt())
    }
}
