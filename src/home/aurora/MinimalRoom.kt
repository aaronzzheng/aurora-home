package home.aurora

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.Calendar
import java.util.Date
import java.util.Locale
import home.aurora.R as Res
import kotlin.math.min

/**
 * Minimal: a typographic page.  A masthead and hairline rules, the time set
 * very large on the left, a numbered index of apps on the right and a single
 * row of text controls at the foot.  The page is printed on matte paper by day
 * and turns to ink-black stock in the evening.  Focus is one fine rule that
 * glides beneath whatever is chosen; the chosen line steps forward a little.
 */
class MinimalRoom(h: HomeActivity, t: HomeTheme) : Room(h, t) {

    private val sans = Room.tf("sans-serif", Typeface.NORMAL)
    private val medium = Room.tf("sans-serif-medium", Typeface.NORMAL)
    private val thin = Room.tf("sans-serif-thin", Typeface.NORMAL)
    private val serifItalic = Room.tf("serif", Typeface.ITALIC)
    private lateinit var paper: View
    private lateinit var page: View
    private lateinit var rule: View
    private var selected = ""
    private var day = false
    private var ruleShown = false
    private val grain: Bitmap by lazy {
        val o = BitmapFactory.Options()
        o.inScaled = false
        BitmapFactory.decodeResource(home.resources, Res.drawable.minimal_paper, o)
    }

    /** Where the gliding rule sits under each item, and how far each item has stepped forward. */
    private val under = HashMap<View, RectF>()
    private class Step { var v = 0f; var anim: ValueAnimator? = null }

    private val ink get() = if (day) DAY_INK else NIGHT_INK
    private val stock get() = if (day) DAY_PAPER else NIGHT_PAPER
    private val grey get() = if (day) DAY_GREY else NIGHT_GREY
    private val hair get() = if (day) DAY_RULE else NIGHT_RULE

    override fun build() {
        day = isDay(Date())
        stage.setBackgroundColor(stock)
        paper = drawn({ c -> paper(c) }, 0f, 0f, W.toFloat(), H.toFloat())
        page = drawn({ c -> page(c) }, 0f, 0f, W.toFloat(), H.toFloat())
        val order = intArrayOf(GAMES, INPUTS, LIVE, TIMER, SLEEP, SETTINGS)
        val cw = (R - L) / order.size
        for (i in order.indices) {
            val a = order[i]
            val n = (i + 1).toString()
            val step = Step()
            val x = L + i * cw
            val v = control(a, x, CTRL_Y, cw, 58f) { c, w, h, f -> control(c, w, h, f, step.v, n, label(a)) }
            track(v, step, RectF(x + 10, CTRL_Y + 62f, x + cw - 18, CTRL_Y + 63.5f))
        }
        // the one moving thing on the page: a fine rule that follows focus
        rule = View(home)
        rule.setBackgroundColor(ink)
        rule.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        rule.pivotX = 0f
        rule.pivotY = 0f
        rule.alpha = 0f
        place(rule, 0f, 0f, 100f, 1.5f)
    }

    override fun setApps(apps: List<Apps.App>, back: Apps.App?) {
        clearApps()
        val n = min(apps.size, ROWS - 1)
        for (i in 0 until n) {
            val a = apps[i]
            val no = String.format(Locale.US, "%02d", i + 1)
            val note = if (back != null && i == 0) "last opened" else ""
            val step = Step()
            val y = INDEX_Y + i * ROW
            val v = app(a, INDEX_X, y, R - INDEX_X, ROW) { c, w, h, f -> row(c, w, h, f, step.v, no, a.label, note) }
            track(v, step, rowRule(y))
        }
        val step = Step()
        val all = Widget(home, this) { c, w, h, f -> row(c, w, h, f, step.v, "—", "All apps", "index") }
        all.contentDescription = "All apps"
        all.setOnClickListener { v -> home.act(ALL, v) }
        all.hook = Room.Hook { has -> if (has) { home.actionFocused("All apps"); select("All apps", all) } }
        appViews.add(all)
        val y = INDEX_Y + n * ROW
        place(all, INDEX_X, y, R - INDEX_X, ROW)
        track(all, step, rowRule(y))
        rule.bringToFront()
    }

    private fun rowRule(y: Float) = RectF(INDEX_X + 44, y + ROW - 1.5f, R, y + ROW)

    /** Remember where the rule goes for `v`, and step its text forward while it has focus. */
    private fun track(v: Widget, step: Step, r: RectF) {
        under[v] = r
        v.setOnFocusChangeListener { _, has ->
            step.anim?.cancel()
            val a = ValueAnimator.ofFloat(step.v, if (has) 1f else 0f)
            a.duration = if (has) 260 else 160
            a.interpolator = DecelerateInterpolator(1.6f)
            a.addUpdateListener { x -> step.v = x.animatedValue as Float; v.invalidate() }
            step.anim = a
            a.start()
        }
    }

    override fun onAppFocus(a: Apps.App, v: Widget) { select(a.label, v) }

    override fun onActionFocus(action: Int, v: Widget) { select(label(action), v) }

    private fun select(s: String, v: View) {
        selected = s
        page.invalidate()
        val r = under[v] ?: return
        // glide: the rule slides and stretches to its new place (a property animation, no redraws)
        val tx = r.left * k
        val ty = r.top * k
        val sx = r.width() / 100f
        rule.animate().cancel()
        if (!ruleShown) {
            ruleShown = true
            rule.translationX = tx; rule.translationY = ty; rule.scaleX = sx
            rule.animate().alpha(1f).setDuration(220).start()
        } else {
            rule.animate().translationX(tx).translationY(ty).scaleX(sx).alpha(1f)
                .setDuration(300).setInterpolator(DecelerateInterpolator(2f)).start()
        }
    }

    override fun tick(now: Date, hhmm: String) {
        super.tick(now, hhmm)
        val d = isDay(now)
        if (d != day) {
            // evening falls (or morning comes): reprint the page on the other stock
            day = d
            stage.setBackgroundColor(stock)
            rule.setBackgroundColor(ink)
            paper.invalidate()
            for (a in actions) a?.invalidate()
            for (v in appViews) v.invalidate()
        }
        page.invalidate()
    }

    /** Coming back: the ink settles onto the page. */
    override fun onReturn() {
        page.alpha = 0f
        page.animate().alpha(1f).setDuration(320).start()
    }

    /** Paper from seven in the morning, black stock from seven in the evening. */
    private fun isDay(d: Date): Boolean {
        val cal = Calendar.getInstance()
        cal.time = d
        return HomeTheme.isDay(HomeTheme.hour(home, cal))   // preview builds can pretend it's another hour
    }

    // ------------------------------------------------------------------ the stock

    private fun paper(c: Canvas) {
        val p = Room.paint()
        c.drawColor(stock)
        // the paper's tooth and fibres
        val s = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val m = android.graphics.Matrix()
        m.setScale(0.5f, 0.5f)
        s.setLocalMatrix(m)
        p.shader = s
        if (day) {
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        } else {
            // inverted, so the fibres are faint light flecks on black stock
            val cm = ColorMatrix(floatArrayOf(-0.7f, 0f, 0f, 0f, 178f, 0f, -0.7f, 0f, 0f, 178f, 0f, 0f, -0.7f, 0f, 178f, 0f, 0f, 0f, 1f, 0f))
            p.colorFilter = ColorMatrixColorFilter(cm)
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        c.drawPaint(p)
        p.xfermode = null
        p.colorFilter = null
        // soft light falling from the upper left, the far corner a touch deeper
        p.alpha = 255
        p.shader = RadialGradient(W * 0.2f, -H * 0.2f, W * 1.1f,
            intArrayOf(if (day) 0x1AFFFFFF else 0x0DFFFFFF, 0x00000000, if (day) 0x14000000 else 0x33000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null
    }

    // ------------------------------------------------------------------ the type

    private fun page(c: Canvas) {
        val line = Room.paint()
        line.color = hair
        // masthead
        val mast = Room.textPaint(10f, ink, medium)
        mast.letterSpacing = 0.4f
        c.drawText("HOME", L, 44f, mast)
        val meta = Room.textPaint(10f, grey, sans)
        meta.letterSpacing = 0.24f
        meta.textAlign = Paint.Align.RIGHT
        val hol = holiday
        val edition = if (hol == null) Room.fmt("EEEE d MMMM yyyy", now).uppercase(Locale.getDefault())
        else theme.greeting!!.uppercase(Locale.getDefault()) + " EDITION  ·  " + Room.fmt("d MMMM", now).uppercase(Locale.getDefault())
        if (hol != null) meta.color = Room.holidayAccent(hol)
        c.drawText(edition, R, 44f, meta)
        val mid = Room.textPaint(10f, grey, serifItalic)
        mid.letterSpacing = 0.06f
        if (hol == null) Room.centerText(c, if (day) "Day edition" else "Evening edition", (L + R) / 2f, 44f, mid)
        c.drawRect(L, 58f, R, 59f, line)
        c.drawRect(L, 61.5f, R, 62f, line)
        // the time, set large and thin
        val time = Room.textPaint(184f, ink, thin)
        time.letterSpacing = -0.03f
        time.fontFeatureSettings = "tnum"
        c.drawText(Room.time(hhmm), L - 10, 254f, time)
        val day = Room.textPaint(24f, ink, serifItalic)
        c.drawText(Room.fmt("EEEE, d MMMM", now), L, 296f, day)
        c.drawRect(L, 316f, 500f, 317f, line)
        val body = Room.textPaint(12f, grey, sans)
        val wk = week.text.toString()
        c.drawText(Room.fit(if (wk.isEmpty()) "No viewing recorded this week." else wk.replace("  ·  ", " — "), body, 452f), L, 340f, body)
        if (selected.isNotEmpty()) {
            val sel = Room.textPaint(12f, ink, sans)
            val lead = Room.textPaint(12f, grey, serifItalic)
            c.drawText("Selected", L, 362f, lead)
            c.drawText(Room.fit("$selected.  OK to open.", sel, 380f), L + lead.measureText("Selected") + 10f, 362f, sel)
        }
        // index heading and column rule
        val head = Room.textPaint(9f, grey, medium)
        head.letterSpacing = 0.4f
        c.drawText("INDEX", INDEX_X, 92f, head)
        c.drawRect(INDEX_X - 24, 80f, INDEX_X - 23, 404f, line)
        // foot
        c.drawRect(L, 428f, R, 429f, line)
        val foot = Room.textPaint(8.5f, grey, sans)
        foot.letterSpacing = 0.14f
        val rem = if (remote.visibility == View.VISIBLE) remote.text.toString() else ""
        c.drawText("HOLD OK ON AN APP TO EDIT" + (if (rem.isEmpty()) "" else "   ·   " + rem.uppercase(Locale.getDefault())), L, 522f, foot)
        val folio = Room.textPaint(8.5f, grey, sans)
        folio.letterSpacing = 0.14f
        folio.textAlign = Paint.Align.RIGHT
        val cal = Calendar.getInstance()
        cal.time = now
        c.drawText("NO. " + cal.get(Calendar.DAY_OF_YEAR), R, 522f, folio)
    }

    private fun row(c: Canvas, w: Float, h: Float, f: Boolean, s: Float, no: String, name: String, note: String) {
        val p = Room.paint()
        p.color = hair
        c.drawRect(0f, h - 1, w, h, p)
        val num = Room.textPaint(11f, if (s > 0.5f) ink else grey, medium)
        num.letterSpacing = 0.1f
        c.drawText(no, 0f, h / 2 + 5, num)
        val t = Room.textPaint(17f, ink, if (f) medium else sans)
        c.drawText(Room.fit(name, t, w - 160), 44f + 8f * s, h / 2 + 6, t)
        val n = Room.textPaint(9f, grey, sans)
        n.letterSpacing = 0.16f
        n.textAlign = Paint.Align.RIGHT
        if (s > 0.01f) {
            n.color = ink
            n.alpha = (255 * s).toInt()
            c.drawText("OPEN  →", w - 4, h / 2 + 4, n)
        }
        if (s < 0.99f && note.isNotEmpty()) {
            n.color = grey
            n.alpha = (255 * (1 - s)).toInt()
            c.drawText(note.uppercase(Locale.getDefault()), w - 4, h / 2 + 4, n)
        }
    }

    private fun control(c: Canvas, w: Float, h: Float, f: Boolean, s: Float, no: String, label: String) {
        val num = Room.textPaint(9f, if (s > 0.5f) ink else grey, medium)
        c.drawText(no, 10f, 18f - 3f * s, num)
        var main = label
        var sub = ""
        val dot = label.indexOf(" · ")
        if (dot > 0) { main = label.substring(0, dot); sub = label.substring(dot + 3) }
        val t = Room.textPaint(16f, ink, if (f) medium else sans)
        c.drawText(Room.fit(main, t, w - 22), 10f, 40f, t)
        if (sub.isNotEmpty()) {
            val st = Room.textPaint(10f, grey, sans)
            c.drawText(sub, 10f, 54f, st)
        }
    }

    companion object {
        // day: warm matte paper and soft black ink
        private const val DAY_PAPER = 0xFFF1EDE4.toInt()
        private const val DAY_INK = 0xFF191817.toInt()
        private const val DAY_GREY = 0xFF7E796F.toInt()
        private const val DAY_RULE = 0xFFC9C3B7.toInt()
        // evening: ink-black stock, warm white type
        private const val NIGHT_PAPER = 0xFF0E0E0D.toInt()
        private const val NIGHT_INK = 0xFFEDEAE3.toInt()
        private const val NIGHT_GREY = 0xFF8A867E.toInt()
        private const val NIGHT_RULE = 0xFF34322F.toInt()

        private const val L = 48f
        private const val R = 912f
        private const val ROW = 33f
        private const val INDEX_X = 560f
        private const val INDEX_Y = 104f
        private const val ROWS = 8
        private const val CTRL_Y = 440f

    }
}
