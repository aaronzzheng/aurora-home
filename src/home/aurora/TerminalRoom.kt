package home.aurora

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Computer Science: the whole screen is the front of a black hacker terminal.
 * A charcoal cabinet, a funnel bezel round a curved green phosphor tube (glow,
 * scanlines, a faint hex dump in the dark, a room reflection on the glass) and
 * a row of physical soft keys under the tube.  On the glass: a root shell
 * listing the programs (apps), a big clock and a status listing; each soft key's job is
 * labelled on the bottom line of the screen right above it, the way terminals
 * labelled their function keys.  The cabinet is a pre-lit plate (cs_bezel)
 * laid over the screen; only the phosphor and the keys redraw.
 */
class TerminalRoom(h: HomeActivity, t: HomeTheme) : Room(h, t) {

    private companion object {
        // geometry, in sync with the plate (scratch tools/cs_plate.py)
        val TUBE = RectF(80f, 36f, 880f, 436f)
        const val KEY_X0 = 106f
        const val KEY_W = 98f
        const val KEY_GAP = 12f
        const val KEY_Y = 472f
        const val KEY_H = 42f
        val BADGE = RectF(16f, 470f, 94f, 516f)
        const val KNOB_X = 912f
        const val KNOB_Y = 493f
        const val KNOB_R = 19f
        const val LED_X = 888f
        const val LED_Y = 474f

        const val ROW_X = 112f
        const val ROW_Y = 104f
        const val ROW_W = 410f
        const val ROW = 30f
        const val PROGRAMS = 7
        const val COL2 = 566f
        const val LABEL_Y = 386f

        // P1 green phosphor
        const val BG = 0xFF020A04.toInt()
        const val GREEN = 0xFF46F07A.toInt()
        const val BRIGHT = 0xFFB4FFC8.toInt()
        const val DIM = 0xFF22A048.toInt()
        const val FAINT = 0xFF0E4A20.toInt()
        const val INK = 0xFF02140A.toInt()
        const val GLOW = 0x9922E05A.toInt()
    }

    private val mono: Typeface = Typeface.MONOSPACE
    private val bold: Typeface = Typeface.DEFAULT_BOLD   // a marker: glowing() draws it as fake-bold monospace (the bold face isn't monospaced everywhere)
    private lateinit var screen: View
    private lateinit var cursor: View
    private lateinit var warm: View
    private lateinit var glass: View
    private val keys = ArrayList<Widget>()
    private var command = ""
    private var lit = -1          // the soft key in focus, or -1
    private var blink: ObjectAnimator? = null
    private var power = 1f

    private val keyOrder = intArrayOf(GAMES, INPUTS, LIVE, TIMER, SLEEP, SETTINGS, ALL)
    private val keyNames = arrayOf("GAMES", "INPUTS", "LIVE TV", "TIMER", "SLEEP", "SETUP", "ALL APPS")

    override fun build() {
        screen = drawn({ c -> phosphor(c) }, TUBE.left, TUBE.top, TUBE.width(), TUBE.height())
        cursor = drawn({ c ->
            val p = paint()
            p.color = GREEN
            c.drawRect(0f, 3f, 9f, 18f, p)
        }, 0f, 0f, 10f, 20f)
        warm = drawn({ c -> warmUp(c) }, TUBE.left, TUBE.top, TUBE.width(), TUBE.height())
        warm.visibility = View.GONE
        glass = art { c -> cabinet(c) }
        clock = ClockFace { s -> hhmm = s; screen.invalidate() }

        for (i in keyOrder.indices) {
            val a = keyOrder[i]
            val n = i + 1
            val k = control(a, keyX(i) - 5, KEY_Y - 5, KEY_W + 10, KEY_H + 12) { c, w, h, f -> keycap(c, w, h, f, n) }
            if (a == ALL) k.contentDescription = "All apps"
            keys.add(k)
        }
        val refresh = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { screen.invalidate() }
        }
        temp.addTextChangedListener(refresh)
        sky.addTextChangedListener(refresh)
        week.addTextChangedListener(refresh)
        remote.addTextChangedListener(refresh)
        type("")
    }

    private fun keyX(i: Int) = KEY_X0 + i * (KEY_W + KEY_GAP)

    override fun setApps(apps: List<Apps.App>, back: Apps.App?) {
        clearApps()
        val n = min(apps.size, PROGRAMS)
        for (i in 0 until n) {
            val a = apps[i]
            val name = exe(a.label)
            val meta = if (back != null && i == 0) "LAST" else String.format(Locale.US, "%3dK", 120 + (a.pkg.hashCode() and 0x3FF))
            app(a, ROW_X, ROW_Y + i * ROW, ROW_W, ROW) { c, w, h, f -> program(c, w, h, f, i + 1, name, meta) }
        }
        // the cabinet sits over the glass; the keys sit on the cabinet
        warm.bringToFront()
        glass.bringToFront()
        for (k in keys) k.bringToFront()
    }

    override fun firstApp(): View? = if (appViews.isEmpty()) actions[GAMES] else appViews[0]

    override fun onAppFocus(a: Apps.App, v: Widget) {
        lit = -1
        type("./" + exe(a.label))
    }

    override fun onActionFocus(action: Int, v: Widget) {
        lit = keyOrder.indexOf(action)
        val cmds = arrayOf("./games", "inputs --list", "./livetv", "timer --set", "sleep --after", "sudo setup", "ls -a /apps")
        type(cmds[action])
    }

    private fun type(cmd: String) {
        command = cmd
        screen.invalidate()
        val m = glowing(14f, GREEN, bold)
        cursor.translationX = px(ROW_X + 4 + m.measureText(PROMPT + cmd) + 2)
        cursor.translationY = px(PROMPT_Y - 16)
    }

    private val PROMPT_Y = 364f
    private val PROMPT = "root@aurora:~# "

    override fun label(action: Int): String {
        val s = super.label(action)
        return if (action == SLEEP && s == "Sleep timer") "Sleep" else s
    }

    override fun tick(now: Date, hhmm: String) {
        super.tick(now, hhmm)
        screen.invalidate()
    }

    override fun setStatus(timer: String, sleep: String) {
        super.setStatus(timer, sleep)
        screen.invalidate()
    }

    override fun start() {
        // the block cursor blinks: the only thing that moves
        val b = ObjectAnimator.ofFloat(cursor, View.ALPHA, 1f, 0f)
        blink = b
        b.duration = 530
        b.repeatCount = ValueAnimator.INFINITE
        b.repeatMode = ValueAnimator.RESTART
        b.setInterpolator { t -> if (t < 0.5f) 0f else 1f }
        b.start()
    }

    override fun stop() { blink?.cancel() }

    /** Back from a program: the tube warms up, a bright line opening into the picture. */
    override fun onReturn() {
        warm.visibility = View.VISIBLE
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 560
        a.interpolator = DecelerateInterpolator(1.6f)
        a.addUpdateListener { x -> power = x.animatedValue as Float; warm.invalidate() }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(an: android.animation.Animator) { warm.visibility = View.GONE }
        })
        power = 0f
        a.start()
    }

    private fun warmUp(c: Canvas) {
        val w = TUBE.width()
        val h = TUBE.height()
        val p = paint()
        val open = h / 2 * power
        p.color = 0xFF000000.toInt()
        c.drawRect(0f, 0f, w, h / 2 - open, p)
        c.drawRect(0f, h / 2 + open, w, h, p)
        p.color = alpha(BRIGHT, 1 - power)
        c.drawRect(0f, h / 2 - open - 1.5f, w, h / 2 + open + 1.5f, p)
    }

    // ------------------------------------------------------------------ the cabinet, painted once

    private fun cabinet(c: Canvas) {
        // decoded off the main thread and cached; the cabinet paints itself once it lands
        val plate = plate(R.drawable.cs_bezel, false) { repaint(glass) } ?: return
        c.drawBitmap(plate, null, RectF(0f, 0f, W.toFloat(), H.toFloat()), paint())

        // the badge in its recess
        val gold = textPaint(13f, 0xFFC9CDD0.toInt(), tf("sans-serif-condensed", Typeface.BOLD_ITALIC))
        gold.letterSpacing = 0.04f
        gold.setShadowLayer(0.6f, 0f, -0.5f, 0x66000000)
        centerText(c, "Aurora", BADGE.centerX(), BADGE.top + 22, gold)
        val small = textPaint(6.5f, 0xFF7F8A84.toInt(), tf("sans-serif-condensed", Typeface.BOLD))
        small.letterSpacing = 0.25f
        centerText(c, "VT·960", BADGE.centerX(), BADGE.top + 35, small)

        // power lamp
        glow(c, LED_X, LED_Y, 9f, 0x8833E866.toInt())
        val p = paint()
        p.alpha = 255; p.shader = RadialGradient(LED_X - 0.6f, LED_Y - 0.8f, 2.8f, intArrayOf(0xFFD8FFE0.toInt(), 0xFF3CE070.toInt(), 0xFF107A30.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(LED_X, LED_Y, 2.5f, p)
        p.shader = null

        // brightness knob: brown moulded skirt, ridged grip, a pointer line
        val soft = paint()
        soft.color = 0x80000000.toInt()
        soft.maskFilter = android.graphics.BlurMaskFilter(3f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        c.drawCircle(KNOB_X + 1, KNOB_Y + 3, KNOB_R, soft)
        p.alpha = 255; p.shader = RadialGradient(KNOB_X - 6, KNOB_Y - 8, KNOB_R * 1.5f, intArrayOf(0xFF4A4D50.toInt(), 0xFF1C1E20.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(KNOB_X, KNOB_Y, KNOB_R, p)
        p.shader = null
        p.strokeWidth = 0.9f
        for (i in 0 until 40) {
            val a = i * Math.PI / 20
            p.color = if (i % 2 == 0) 0x33FFFFFF else 0x55000000
            c.drawLine(KNOB_X + Math.cos(a).toFloat() * (KNOB_R - 3), KNOB_Y + Math.sin(a).toFloat() * (KNOB_R - 3),
                KNOB_X + Math.cos(a).toFloat() * KNOB_R, KNOB_Y + Math.sin(a).toFloat() * KNOB_R, p)
        }
        val cap = KNOB_R * 0.72f
        p.alpha = 255; p.shader = SweepGradient(KNOB_X, KNOB_Y, intArrayOf(0xFF5A5E62.toInt(), 0xFF2E3134.toInt(), 0xFF6C7074.toInt(), 0xFF2A2D30.toInt(), 0xFF5A5E62.toInt()), null)
        c.drawCircle(KNOB_X, KNOB_Y, cap, p)
        p.shader = null
        p.alpha = 255; p.shader = LinearGradient(0f, KNOB_Y - cap, 0f, KNOB_Y + cap, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(KNOB_X, KNOB_Y, cap, p)
        p.shader = null
        p.color = 0xFF46F07A.toInt()
        p.strokeWidth = 1.6f
        p.strokeCap = Paint.Cap.ROUND
        c.drawLine(KNOB_X + 3.5f, KNOB_Y - 3.5f, KNOB_X + cap * 0.68f, KNOB_Y - cap * 0.68f, p)

        holidayProps(c)
    }

    private fun holidayProps(c: Canvas) {
        if (holiday == null) return
        when (holiday) {
            "halloween" -> {
                pumpkin(c, 56f, 468f, 15f)
                bat(c, 912f, 26f, 11f, 0xFF1A120C.toInt())
                bat(c, 880f, 18f, 7f, 0xFF1A120C.toInt())
            }
            "christmas" -> garland(c, 26f, 934f, 8f, 8f, true)
            "newyear" -> bunting(c, 26f, 934f, 6f, 8f, NEWYEAR)
            else -> bunting(c, 26f, 934f, 6f, 8f, JULY4)
        }
    }

    // ------------------------------------------------------------------ the phosphor

    private fun glowing(size: Float, color: Int, tf: Typeface, heavy: Boolean = true): Paint {
        val p = textPaint(size, color, Typeface.MONOSPACE)
        p.isFakeBoldText = tf === bold && heavy
        p.setShadowLayer(size * 0.32f, 0f, 0f, if (color == INK) 0x00000000 else GLOW)
        return p
    }

    private fun phosphor(c: Canvas) {
        val w = TUBE.width()
        val h = TUBE.height()
        val p = paint()
        p.color = BG
        c.drawRect(0f, 0f, w, h, p)
        // the beam's warmth in the middle of the face
        p.alpha = 255; p.shader = RadialGradient(w * 0.45f, h * 0.45f, w * 0.6f, 0x1A22E05A, 0x0022E05A, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        c.save()
        c.translate(-TUBE.left, -TUBE.top)   // draw in stage coordinates
        hexDump(c)

        // status line
        val dim = glowing(11f, DIM, mono)
        c.drawText("AURORA/OS 2.6  ·  SECURE SHELL  ·  ROOT", 112f, 66f, dim)
        dim.textAlign = Paint.Align.RIGHT
        c.drawText("PTS/0  " + fmt("yyyy-MM-dd", now), 848f, 66f, dim)
        dim.textAlign = Paint.Align.LEFT
        rule(c, 112f, 76f, 848f)

        // the run menu header
        val hd = glowing(11f, GREEN, bold)
        hd.letterSpacing = 0.1f
        c.drawText("$ ls ~/programs", ROW_X + 4, ROW_Y - 8, hd)
        if (appViews.isEmpty()) c.drawText("  (empty)", ROW_X + 4, ROW_Y + 20, dim)
        else c.drawText(String.format(Locale.US, "%d program%s  ·  F7: ls -a", appViews.size, if (appViews.size == 1) "" else "s"), ROW_X + 10, ROW_Y + appViews.size * ROW + 20, glowing(10f, DIM, mono))

        // column two: a big clock and a status listing
        p.color = FAINT
        c.drawRect(COL2 - 26, 90f, COL2 - 25f, 340f, p)
        val lab = glowing(9f, DIM, bold)
        lab.letterSpacing = 0.2f
        c.drawText("TIME", COL2, 100f, lab)
        val big = glowing(68f, BRIGHT, bold)
        big.setShadowLayer(14f, 0f, 0f, 0xB322E05A.toInt())
        c.drawText(time(hhmm), COL2 - 4, 164f, big)
        val day = glowing(12f, GREEN, bold)
        day.letterSpacing = 0.08f
        c.drawText(fmt("EEEE  d MMMM", now).uppercase(Locale.getDefault()), COL2, 188f, day)

        val t = temp.text.toString()
        val s = sky.text.toString()
        val nl = s.indexOf('\n')
        val rows = arrayOf(
            arrayOf("WX", if (t.isEmpty()) "OFFLINE" else t + " " + (if (nl < 0) s else s.substring(0, nl))),
            arrayOf("WEEK", if (week.text.isEmpty()) "NO SESSIONS" else week.text.toString().replace("This week  ·  ", "").replace("  ·  ", ", ")),
            arrayOf("TIMER", if (timerText == "Timer") "IDLE" else timerText.replace("Timer · ", "").replace("Stopwatch · ", "SW ")),
            arrayOf("SLEEP", if (sleepText == "Sleep timer") "OFF" else sleepText.replace("Sleep · ", "")),
            arrayOf("LINK", if (remote.visibility == View.VISIBLE && remote.text.isNotEmpty()) remote.text.toString().replace("Phone remote  ·  ", "") else "LOCAL"))
        val k = glowing(11f, DIM, mono)
        val v = glowing(11f, GREEN, mono)
        for (i in rows.indices) {
            val y = 222f + i * 20f
            c.drawText(rows[i][0], COL2, y, k)
            v.color = if ((i == 2 && rows[i][1] != "IDLE") || (i == 3 && rows[i][1] != "OFF")) BRIGHT else GREEN
            c.drawText(fit(rows[i][1].uppercase(Locale.getDefault()), v, 848f - COL2 - 62), COL2 + 60, y, v)
        }
        val greeting = theme.greeting
        if (greeting != null) {
            val g = glowing(11f, BRIGHT, bold)
            g.letterSpacing = 0.06f
            c.drawText("*** " + greeting.uppercase(Locale.getDefault()) + " ***", COL2, 334f, g)
        } else {
            c.drawText("OK: EXEC  ·  HOLD OK: EDIT", COL2, 334f, glowing(9.5f, DIM, mono))
        }

        // the command line
        rule(c, 112f, 344f, 848f)
        val pr = glowing(14f, GREEN, bold)
        c.drawText(PROMPT + command, ROW_X + 4, PROMPT_Y, pr)

        // soft-key labels, each right above its key
        for (i in keyOrder.indices) {
            val x = keyX(i)
            val r = RectF(x + 1, LABEL_Y, x + KEY_W - 1, LABEL_Y + 24)
            val on = i == lit
            val name = if (keyOrder[i] == TIMER || keyOrder[i] == SLEEP) label(keyOrder[i]).uppercase(Locale.getDefault()).replace(" · ", " ") else keyNames[i]
            if (on) {
                p.color = 0x4022E05A
                c.drawRect(r.left - 2, r.top - 2, r.right + 2, r.bottom + 2, p)
                p.color = GREEN
                c.drawRect(r, p)
            } else {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1f
                p.color = DIM
                c.drawRect(r, p)
                p.style = Paint.Style.FILL
            }
            val nm = glowing(10.5f, if (on) INK else GREEN, bold)
            centerText(c, fit(name, nm, r.width() - 8), r.centerX(), r.bottom - 6, nm)
        }
        c.restore()
    }

    private fun rule(c: Canvas, x1: Float, y: Float, x2: Float) {
        val p = paint()
        p.color = FAINT
        var x = x1
        while (x < x2) { c.drawRect(x, y, x + 5f, y + 1.2f, p); x += 7f }
    }

    /** A program in the run menu; in inverse video when chosen. */
    private fun program(c: Canvas, w: Float, h: Float, f: Boolean, n: Int, name: String, meta: String) {
        val p = paint()
        if (f) {
            p.color = 0x4022E05A
            c.drawRect(0f, 1f, w, h - 1f, p)
            p.color = GREEN
            c.drawRect(2f, 3f, w - 2f, h - 3f, p)
        }
        val num = glowing(14f, if (f) INK else DIM, bold)
        c.drawText(n.toString(), 10f, h / 2 + 5, num)
        val nm = glowing(14f, if (f) INK else GREEN, bold)
        val label = fit(name, nm, w - 160)
        c.drawText(label, 34f, h / 2 + 5, nm)
        // dotted leader to the size column
        val lead = glowing(14f, if (f) INK else FAINT, mono)
        val from = 34f + nm.measureText(label) + 8
        val to = w - 110
        var x = from
        val dot = lead.measureText(".")
        val sb = StringBuilder()
        while (x + dot < to) { sb.append('.'); x += dot }
        c.drawText(sb.toString(), from, h / 2 + 5, lead)
        val m = glowing(12f, if (f) INK else DIM, mono)
        c.drawText(meta, w - 100, h / 2 + 5, m)
        if (f) {
            val run = glowing(11f, INK, bold)
            run.textAlign = Paint.Align.RIGHT
            c.drawText("◄ EXEC", w - 8, h / 2 + 4.5f, run)
        }
    }

    // ------------------------------------------------------------------ the soft keys

    /** A black keycap in its well; pressed down and lit green by the screen when chosen. */
    private fun keycap(c: Canvas, w: Float, h: Float, f: Boolean, n: Int) {
        val p = paint()
        val d = if (f) 2.6f else 0f
        val top = RectF(6f, 4f + d, w - 6f, h - 13f + d)
        // the skirt (front face of the key), visible below the top
        val skirt = RectF(5f, 6f + d, w - 5f, h - 6f)
        p.alpha = 255; p.shader = LinearGradient(0f, skirt.top, 0f, skirt.bottom, 0xFF26282B.toInt(), 0xFF141517.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(skirt, 5f, 5f, p)
        p.shader = null
        // dished top, lit from above
        p.alpha = 255; p.shader = LinearGradient(0f, top.top, 0f, top.bottom,
            if (f) intArrayOf(0xFF2A3A30.toInt(), 0xFF1E2A22.toInt(), 0xFF18201B.toInt()) else intArrayOf(0xFF3A3D41.toInt(), 0xFF2C2F32.toInt(), 0xFF232528.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(top, 4f, 4f, p)
        p.alpha = 255; p.shader = RadialGradient(top.centerX(), top.centerY() + 6, top.width() * 0.6f, 0x00000000, 0x33000000, Shader.TileMode.CLAMP)
        c.drawRoundRect(top, 4f, 4f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.8f
        p.color = 0x40FFFFFF
        c.drawLine(top.left + 4, top.top + 0.6f, top.right - 4, top.top + 0.6f, p)
        p.style = Paint.Style.FILL
        if (f) {
            // the lit screen above throws warm light onto the key
            p.alpha = 255; p.shader = LinearGradient(0f, top.top, 0f, top.top + 16, 0x6622E05A, 0x0022E05A, Shader.TileMode.CLAMP)
            c.drawRoundRect(top, 4f, 4f, p)
            p.shader = null
        }
        val leg = textPaint(9f, if (f) 0xFF7CFFA0.toInt() else 0xFF2FB85A.toInt(), tf("sans-serif-condensed", Typeface.BOLD))
        leg.letterSpacing = 0.06f
        c.drawText("F$n", top.left + 7, top.top + 13, leg)
        // a small printed arrow pointing up at the screen label
        val a = Path()
        a.moveTo(top.centerX(), top.bottom - 11)
        a.lineTo(top.centerX() - 4, top.bottom - 6)
        a.lineTo(top.centerX() + 4, top.bottom - 6)
        a.close()
        p.color = if (f) 0xFF7CFFA0.toInt() else 0x8022A048.toInt()
        c.drawPath(a, p)
    }

    // ------------------------------------------------------------------ names

    private fun exe(label: String): String {
        val s = label.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return if (s.isEmpty()) "app" else s.take(22)
    }

    /** A faint hex dump in the dark parts of the tube: it reads as "something is running". */
    private fun hexDump(c: Canvas) {
        val p = textPaint(9f, 0x1422E05A, mono)
        val r = java.util.Random(1977)
        var y = 52f
        while (y < 432f) {
            val sb = StringBuilder()
            sb.append(String.format(Locale.US, "%08x  ", (y * 977).toInt()))
            for (i in 0 until 26) sb.append(String.format(Locale.US, "%02x ", r.nextInt(256)))
            c.drawText(sb.toString(), 96f, y, p)
            y += 11f
        }
    }
}
