package home.aurora

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Space: the view from a crew station.  The whole screen is the cabin wall
 * around the cupola window: machined aluminium ring, rubber seal, Earth below
 * (a real ISS photograph by day, city lights by night).  The bulkhead panels
 * carry the instruments: a mission clock with green segment digits, an
 * environment readout, backlit flight-system push buttons for the everyday
 * controls, and recessed module displays for the apps.  The panel relief is a
 * pre-lit plate (space_cabin); everything that changes is drawn on top.
 */
class SpaceRoom(h: HomeActivity, t: HomeTheme) : Room(h, t) {

    private companion object {
        // geometry, in sync with the plate (scratch tools/space_plate.py)
        const val CX = 480f
        const val CY = 212f
        const val R_GLASS = 170f
        val CLOCKBOX = RectF(30f, 50f, 240f, 196f)
        val ENVBOX = RectF(30f, 230f, 240f, 330f)
        val BTN_X = floatArrayOf(720f, 834f)
        val BTN_Y = floatArrayOf(56f, 172f, 288f)
        const val BTN_W = 96f
        const val BTN_H = 100f
        const val BAY_X0 = 26f
        const val BAY_W = 142f
        const val BAY_GAP = 9f
        const val BAY_Y = 450f
        const val BAY_H = 64f
        const val BAYS = 6

        const val EL = 0xFFA6E36E.toInt()        // electroluminescent green
        const val EL_DIM = 0xFF5E8A40.toInt()
        const val EL_GHOST = 0x14A6E36E
        const val LAMP = 0xFFFFC56A.toInt()      // warm annunciator light
        const val LEGEND = 0xFFD9DCDD.toInt()
        const val PAINT = 0xFFE9EAE6.toInt()     // white panel lettering
    }

    private val cond = tf("sans-serif-condensed", Typeface.BOLD)
    private val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private lateinit var earth: Earth
    private lateinit var clockView: View
    private lateinit var env: View
    private lateinit var wall: View
    private val blanks = ArrayList<View>()
    private var target = "STANDBY"
    private var night = false

    override fun build() {
        night = isNight()
        earth = place(Earth(), CX - R_GLASS - 2, CY - R_GLASS - 2, R_GLASS * 2 + 4, R_GLASS * 2 + 4)
        wall = art { c -> cabin(c) }
        clockView = drawn({ c -> missionClock(c) }, CLOCKBOX.left, CLOCKBOX.top, CLOCKBOX.width(), CLOCKBOX.height())
        env = drawn({ c -> environment(c) }, ENVBOX.left, ENVBOX.top, ENVBOX.width(), ENVBOX.height())
        clock = ClockFace { s -> hhmm = s; clockView.invalidate() }

        val order = intArrayOf(GAMES, INPUTS, LIVE, TIMER, SLEEP, SETTINGS)
        for (i in order.indices) {
            val a = order[i]
            val x = BTN_X[i % 2]
            val y = BTN_Y[i / 2]
            control(a, x - 4, y - 4, BTN_W + 8, BTN_H + 8) { c, w, h, f -> button(c, w, h, f, a) }
        }
        val refresh = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { env.invalidate() }
        }
        temp.addTextChangedListener(refresh)
        sky.addTextChangedListener(refresh)
        week.addTextChangedListener(refresh)
    }

    override fun setApps(apps: List<Apps.App>, back: Apps.App?) {
        clearApps()
        for (b in blanks) stage.removeView(b)
        blanks.clear()
        val n = min(apps.size, BAYS - 1)
        for (i in 0 until n) {
            val a = apps[i]
            val art = artOf(a)
            val code = if (back != null && i == 0) "RTN" else String.format(Locale.US, "M-%02d", i + 1)
            app(a, bayX(i), BAY_Y, BAY_W, BAY_H) { c, w, h, f -> module(c, w, h, f, art, code) }
        }
        // the module index (all apps) always sits in the last bay
        val all = Widget(home, this) { c, w, h, f -> index(c, w, h, f) }
        all.contentDescription = "All apps"
        all.setOnClickListener { v -> home.act(ALL, v) }
        all.hook = Hook { has -> if (has) { home.actionFocused("All apps"); aim("ALL MODULES") } }
        appViews.add(all)
        place(all, bayX(BAYS - 1), BAY_Y, BAY_W, BAY_H)
        for (i in n until BAYS - 1) blanks.add(drawn({ c -> emptyBay(c, BAY_W, BAY_H) }, bayX(i), BAY_Y, BAY_W, BAY_H))
    }

    private fun bayX(i: Int) = BAY_X0 + i * (BAY_W + BAY_GAP)

    override fun firstApp(): View? = if (appViews.isEmpty()) actions[GAMES] else appViews[0]

    override fun onAppFocus(a: Apps.App, v: Widget) { aim(a.label.uppercase(Locale.getDefault())) }

    override fun onActionFocus(action: Int, v: Widget) { aim("SYS " + label(action).uppercase(Locale.getDefault())) }

    private fun aim(t: String) {
        target = t
        env.invalidate()
    }

    override fun label(action: Int): String {
        val s = super.label(action)
        return if (action == SLEEP && s == "Sleep timer") "Sleep" else s
    }

    override fun tick(now: Date, hhmm: String) {
        super.tick(now, hhmm)
        clockView.invalidate()
        env.invalidate()
        val n = isNight()
        if (n != night) { night = n; earth.load() }
    }

    override fun setStatus(timer: String, sleep: String) {
        super.setStatus(timer, sleep)
        env.invalidate()
    }

    /** Back from an app: the cabin lights come up and the Earth settles into the window. */
    override fun onReturn() {
        stage.alpha = 0f
        stage.animate().alpha(1f).setDuration(380).start()
        earth.scaleX = 1.06f
        earth.scaleY = 1.06f
        earth.animate().scaleX(1f).scaleY(1f).setDuration(900).setInterpolator(DecelerateInterpolator(2f)).start()
    }

    /** City lights from 19:00 to 07:00, the same hours as every other look. */
    private fun isNight(): Boolean = HomeTheme.isNight(HomeTheme.hour(home))

    // ------------------------------------------------------------------ the cabin wall, painted once

    private fun cabin(c: Canvas) {
        // decoded off the main thread and cached; the wall paints itself once it lands
        val plate = plate(R.drawable.space_cabin, false) { repaint(wall) } ?: return
        c.drawBitmap(plate, null, RectF(0f, 0f, W.toFloat(), H.toFloat()), paint())

        // white panel lettering, silk-screened onto the paint
        engrave(c, "MISSION TIME", CLOCKBOX.left + 2, CLOCKBOX.top - 9, 7.5f)
        engrave(c, "CABIN · ENVIRONMENT", ENVBOX.left + 2, ENVBOX.top - 9, 7.5f)
        engrave(c, "FLIGHT SYSTEMS", BTN_X[0], BTN_Y[0] - 13, 7.5f)
        // the station placard on the lower left
        val plate2 = RectF(30f, 352f, 240f, 404f)
        val p = paint()
        p.color = 0x24000000
        c.drawRoundRect(plate2, 3f, 3f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.8f
        p.color = 0x59E9EAE6
        c.drawRoundRect(plate2, 3f, 3f, p)
        p.style = Paint.Style.FILL
        engrave(c, "AURORA ORBITAL MODULE", plate2.left + 12, plate2.top + 22, 10f)
        engrave(c, "CUPOLA · CREW STATION 2 · HOLD OK TO EDIT", plate2.left + 12, plate2.top + 38, 6.2f)
        holidayProps(c)
    }

    private fun engrave(c: Canvas, s: String, x: Float, y: Float, size: Float) {
        val t = textPaint(size, 0x66000000, cond)
        t.letterSpacing = 0.16f
        c.drawText(s, x + 0.4f, y + 0.6f, t)
        t.color = PAINT
        c.drawText(s, x, y, t)
    }

    private fun holidayProps(c: Canvas) {
        if (holiday == null) return
        when (holiday) {
            "halloween" -> {
                pumpkin(c, 214f, 420f, 15f)
                pumpkin(c, 186f, 420f, 10f)
            }
            "christmas" -> garland(c, 14f, 946f, 12f, 14f, true)
            "newyear" -> bunting(c, 14f, 946f, 10f, 16f, NEWYEAR)
            else -> bunting(c, 14f, 946f, 10f, 16f, JULY4)
        }
    }

    // ------------------------------------------------------------------ instruments

    /** Green segment digits behind smoked glass, like the flight computer's display. */
    private fun missionClock(c: Canvas) {
        val w = CLOCKBOX.width()
        val h = CLOCKBOX.height()
        glassFloor(c, w, h)
        val lab = textPaint(7f, EL_DIM, mono)
        lab.letterSpacing = 0.18f
        c.drawText("LOCAL", 12f, 17f, lab)
        lab.textAlign = Paint.Align.RIGHT
        val doy = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        c.drawText(String.format(Locale.US, "FLIGHT DAY %03d", doy), w - 12, 17f, lab)
        lab.textAlign = Paint.Align.LEFT

        // HH:MM in four seven-segment cells; a blank leading hour stays dark
        val s = hhmm.padStart(4, ' ')
        val dw = 34f
        val dh = 64f
        val gap = 10f
        val colon = 18f
        val total = dw * 4 + gap * 3 + colon
        val x0 = (w - total - dh * 0.1f) / 2
        val y = 32f
        for (i in 0 until 4) {
            val x = x0 + i * (dw + gap) + if (i >= 2) colon else 0f
            digit(c, s[i], x, y, dw, dh, 7.5f)
        }
        val cx = x0 + 2 * dw + gap * 1.5f + colon / 2 + dh * 0.1f * 0.5f
        glowDot(c, cx + 1.5f, y + dh * 0.3f, 4.2f)
        glowDot(c, cx - 2f, y + dh * 0.7f, 4.2f)
        val d = textPaint(10.5f, EL, mono)
        d.letterSpacing = 0.16f
        d.setShadowLayer(4f, 0f, 0f, 0x99A6E36E.toInt())
        centerText(c, fmt("EEE dd MMM yyyy", now).uppercase(Locale.getDefault()), w / 2, h - 14, d)
        glassTop(c, w, h)
    }

    private fun environment(c: Canvas) {
        val w = ENVBOX.width()
        val h = ENVBOX.height()
        glassFloor(c, w, h)
        val k = textPaint(7.5f, EL_DIM, mono)
        k.letterSpacing = 0.14f
        val v = textPaint(10f, EL, mono)
        val t = temp.text.toString()
        val sk = sky.text.toString()
        val nl = sk.indexOf('\n')
        val wx = if (t.isEmpty()) "NO SIGNAL" else t + "  " + (if (nl < 0) sk else sk.substring(0, nl))
        val greeting = theme.greeting
        val log = if (greeting != null) greeting
            else if (timerText != "Timer") timerText.replace(" · ", " ")
            else if (week.text.isEmpty()) "NO ACTIVITY" else week.text.toString().replace("This week  ·  ", "").replace("  ·  ", " · ")
        val rows = arrayOf(arrayOf("TGT", target), arrayOf("WX", wx), arrayOf(if (greeting != null) "MSG" else if (timerText != "Timer") "TMR" else "LOG", log))
        for (i in rows.indices) {
            val y = 28f + i * 27f
            c.drawText(rows[i][0], 12f, y, k)
            v.color = if (i == 0) LAMP else EL
            v.setShadowLayer(3f, 0f, 0f, if (i == 0) 0x80FFC56A.toInt() else 0x80A6E36E.toInt())
            c.drawText(fit(rows[i][1].uppercase(Locale.getDefault()), v, w - 58), 46f, y, v)
        }
        glassTop(c, w, h)
    }

    /** The black floor of a recessed display. */
    private fun glassFloor(c: Canvas, w: Float, h: Float) {
        val p = paint()
        p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF060807.toInt(), 0xFF0C100C.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(1.2f, 1.2f, w - 1.2f, h - 1.2f), 5f, 5f, p)
        p.shader = null
    }

    /** Smoked cover glass: a shadow from the top lip and a faint reflection. */
    private fun glassTop(c: Canvas, w: Float, h: Float) {
        val p = paint()
        p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, 10f, 0x99000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(1.2f, 1.2f, w - 1.2f, 11f, p)
        p.alpha = 255; p.shader = LinearGradient(0f, 0f, w * 0.7f, h, intArrayOf(0x14FFFFFF, 0x05FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.45f, 0.46f), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(1.2f, 1.2f, w - 1.2f, h - 1.2f), 5f, 5f, p)
        p.shader = null
    }

    private fun glowDot(c: Canvas, x: Float, y: Float, r: Float) {
        val p = paint()
        p.alpha = 255; p.shader = RadialGradient(x, y, r * 3, 0x55A6E36E, 0x00A6E36E, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 3, p)
        p.shader = null
        p.color = EL
        c.drawRect(x - r / 2 - 0.8f, y - r / 2 - 0.8f, x + r / 2 + 0.8f, y + r / 2 + 0.8f, p)
    }

    /** One slanted seven-segment cell; unlit segments show faintly, as on the real thing. */
    private fun digit(c: Canvas, ch: Char, x: Float, y: Float, w: Float, h: Float, t: Float) {
        val on = when (ch) {
            '0' -> 0b1111110; '1' -> 0b0110000; '2' -> 0b1101101; '3' -> 0b1111001; '4' -> 0b0110011
            '5' -> 0b1011011; '6' -> 0b1011111; '7' -> 0b1110000; '8' -> 0b1111111; '9' -> 0b1111011
            else -> 0
        }
        val m = h / 2
        val segs = arrayOf(
            floatArrayOf(t / 2, 0f, w - t / 2, 0f),          // a
            floatArrayOf(w, t / 2, w, m - t / 2),            // b
            floatArrayOf(w, m + t / 2, w, h - t / 2),        // c
            floatArrayOf(t / 2, h, w - t / 2, h),            // d
            floatArrayOf(0f, m + t / 2, 0f, h - t / 2),      // e
            floatArrayOf(0f, t / 2, 0f, m - t / 2),          // f
            floatArrayOf(t / 2, m, w - t / 2, m))            // g
        val slant = 0.1f
        val p = paint()
        for (pass in 0 until 3) {
            for (i in 0 until 7) {
                val lit = on and (1 shl (6 - i)) != 0
                if (pass < 2 && !lit) continue
                if (pass == 2 && lit) continue
                val s = segs[i]
                val path = hexSeg(x + s[0] + (h - s[1]) * slant, y + s[1], x + s[2] + (h - s[3]) * slant, y + s[3], t)
                when (pass) {
                    0 -> { p.color = 0x30A6E36E; p.style = Paint.Style.STROKE; p.strokeWidth = t * 0.9f; p.strokeJoin = Paint.Join.ROUND }
                    1 -> { p.color = EL; p.style = Paint.Style.FILL }
                    else -> { p.color = EL_GHOST; p.style = Paint.Style.FILL }
                }
                c.drawPath(path, p)
            }
        }
    }

    private fun hexSeg(x1: Float, y1: Float, x2: Float, y2: Float, t: Float): Path {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val ux = dx / len
        val uy = dy / len
        val e = t / 2 * 0.86f
        val nx = -uy * e
        val ny = ux * e
        val g = 0.9f   // gap between neighbouring segments
        val ax = x1 + ux * g
        val ay = y1 + uy * g
        val bx = x2 - ux * g
        val by = y2 - uy * g
        val p = Path()
        p.moveTo(ax, ay)
        p.lineTo(ax + ux * e + nx, ay + uy * e + ny)
        p.lineTo(bx - ux * e + nx, by - uy * e + ny)
        p.lineTo(bx, by)
        p.lineTo(bx - ux * e - nx, by - uy * e - ny)
        p.lineTo(ax + ux * e - nx, ay + uy * e - ny)
        p.close()
        return p
    }

    // ------------------------------------------------------------------ flight-system push buttons

    /** A square backlit push button in its recess: smoked cap, white legend; lit warm and pressed in when chosen. */
    private fun button(c: Canvas, w: Float, h: Float, f: Boolean, action: Int) {
        val p = paint()
        val d = if (f) 1.6f else 0f
        val face = RectF(6f, 6f + d, w - 6f, h - 8f + d)
        // the cap's shadow on the recess floor, and its side wall
        p.color = 0x99000000.toInt()
        if (!f) c.drawRoundRect(RectF(face.left + 1, face.top + 4, face.right + 1, face.bottom + 4), 7f, 7f, p)
        p.color = if (f) 0xFF3A2E20.toInt() else 0xFF141517.toInt()
        c.drawRoundRect(RectF(face.left, face.top + 2, face.right, face.bottom + 2), 7f, 7f, p)
        p.alpha = 255
        p.shader = if (f) RadialGradient(face.centerX(), face.top + face.height() * 0.42f, face.width() * 0.8f,
                intArrayOf(0xFF6E5432.toInt(), 0xFF4A3622.toInt(), 0xFF2A1F14.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            else LinearGradient(0f, face.top, 0f, face.bottom, intArrayOf(0xFF45484C.toInt(), 0xFF2E3033.toInt(), 0xFF25272A.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(face, 7f, 7f, p)
        p.shader = null
        // satin bevel: light top edge, dark bottom edge
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.alpha = 255; p.shader = LinearGradient(0f, face.top, 0f, face.bottom, if (f) 0xB3FFE2B0.toInt() else 0x8CFFFFFF.toInt(), 0x40000000, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(face.left + 0.6f, face.top + 0.6f, face.right - 0.6f, face.bottom - 0.6f), 6.5f, 6.5f, p)
        p.shader = null
        p.style = Paint.Style.FILL

        // the legend window: the part of the cap that lights up
        val win = RectF(face.left + 8, face.top + 8, face.right - 8, face.top + 54)
        p.color = if (f) 0xFF1E140A.toInt() else 0xFF0E0F11.toInt()
        c.drawRoundRect(win, 3f, 3f, p)
        if (f) {
            p.alpha = 255; p.shader = RadialGradient(win.centerX(), win.centerY(), win.width() * 0.7f, intArrayOf(0xFFFFE0A8.toInt(), 0xFFF0B256.toInt(), 0xFFB97A30.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            c.drawRoundRect(win, 3f, 3f, p)
            p.shader = null
        }
        icon(c, action, win.centerX(), win.centerY(), 24f, if (f) 0xFF3A2408.toInt() else 0xFF9A9EA2.toInt(), 2.2f)
        p.alpha = 255; p.shader = LinearGradient(0f, win.top, 0f, win.top + 14, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRoundRect(win, 3f, 3f, p)
        p.shader = null

        // engraved legend below the window
        val name = label(action).uppercase(Locale.getDefault())
        val t = textPaint(10.5f, if (f) 0xFFFFE6BE.toInt() else LEGEND, cond)
        t.letterSpacing = 0.08f
        if (f) t.setShadowLayer(4f, 0f, 0f, 0xCCFFB050.toInt())
        centerText(c, fit(name, t, face.width() - 10), face.centerX(), face.bottom - 15, t)
        // status lamp: green while a timer or sleep countdown runs
        val running = (action == TIMER && timerText != "Timer") || (action == SLEEP && sleepText != "Sleep timer")
        val lamp = RectF(face.centerX() - 9, face.bottom - 8, face.centerX() + 9, face.bottom - 5)
        p.color = if (running) EL else if (f) LAMP else 0xFF3A3C3F.toInt()
        c.drawRoundRect(lamp, 1.5f, 1.5f, p)
        // warm light spilling onto the recess walls around a lit cap
        if (f) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f
            p.color = 0x40FFB050
            c.drawRoundRect(RectF(2.5f, 2.5f, w - 2.5f, h - 2.5f), 8f, 8f, p)
            p.style = Paint.Style.FILL
        }
    }

    // ------------------------------------------------------------------ module displays

    /** An app's display in its bay: dimmed until chosen, then full bright with a lit amber frame. */
    private fun module(c: Canvas, w: Float, h: Float, f: Boolean, art: AppArt, code: String) {
        val screen = RectF(3f, 3f, w - 3f, h - 3f)
        val p = paint()
        c.save()
        val clip = Path()
        clip.addRoundRect(screen, 3f, 3f, Path.Direction.CW)
        c.clipPath(clip)
        art.fill(c, screen)
        if (!f) { p.color = 0x8005080A.toInt(); c.drawRect(screen, p) }
        // the caption strip
        p.alpha = 255; p.shader = LinearGradient(0f, h - 22, 0f, h - 3, 0x00000000, 0xD9000000.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(screen.left, h - 22, screen.right, screen.bottom, p)
        p.shader = null
        val cd = textPaint(6.5f, if (f) LAMP else EL_DIM, mono)
        cd.letterSpacing = 0.14f
        c.drawText(code, screen.left + 6, screen.bottom - 6, cd)
        val nm = textPaint(7.5f, if (f) 0xFFFFF1DA.toInt() else 0xFFB8BCC0.toInt(), mono)
        nm.letterSpacing = 0.06f
        val nx = screen.left + 10 + cd.measureText(code)
        c.drawText(fit(art.label.uppercase(Locale.getDefault()), nm, screen.right - nx - 6), nx, screen.bottom - 6, nm)
        screenGlass(c, screen)
        c.restore()
        if (f) litFrame(c, w, h)
    }

    private fun index(c: Canvas, w: Float, h: Float, f: Boolean) {
        val screen = RectF(3f, 3f, w - 3f, h - 3f)
        val p = paint()
        p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF0A100A.toInt(), 0xFF060806.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(screen, 3f, 3f, p)
        p.shader = null
        val col = if (f) LAMP else EL
        icon(c, ALL, 26f, h / 2, 20f, col, 2f)
        val t = textPaint(10f, col, mono)
        t.letterSpacing = 0.1f
        t.setShadowLayer(3f, 0f, 0f, if (f) 0x80FFC56A.toInt() else 0x80A6E36E.toInt())
        c.drawText("ALL APPS", 44f, h / 2 - 1, t)
        val s = textPaint(6.5f, EL_DIM, mono)
        s.letterSpacing = 0.14f
        c.drawText("MODULE INDEX", 44f, h / 2 + 11, s)
        screenGlass(c, screen)
        if (f) litFrame(c, w, h)
    }

    private fun emptyBay(c: Canvas, w: Float, h: Float) {
        val screen = RectF(3f, 3f, w - 3f, h - 3f)
        val p = paint()
        p.color = 0xFF070908.toInt()
        c.drawRoundRect(screen, 3f, 3f, p)
        val s = textPaint(6.5f, 0xFF39452F.toInt(), mono)
        s.letterSpacing = 0.2f
        centerText(c, "BAY EMPTY", w / 2, h / 2 + 2, s)
        screenGlass(c, screen)
    }

    private fun screenGlass(c: Canvas, r: RectF) {
        val p = paint()
        p.alpha = 255; p.shader = LinearGradient(r.left, r.top, r.left + r.width() * 0.5f, r.bottom, intArrayOf(0x1FFFFFFF, 0x08FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 0.51f), Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        p.alpha = 255; p.shader = LinearGradient(0f, r.top, 0f, r.top + 8, 0x80000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(r.left, r.top, r.right, r.top + 8, p)
        p.shader = null
    }

    /** Amber edge lighting round the chosen display. */
    private fun litFrame(c: Canvas, w: Float, h: Float) {
        val p = paint()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = 0x59FFB050
        c.drawRoundRect(RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f), 5f, 5f, p)
        p.strokeWidth = 1.6f
        p.color = LAMP
        c.drawRoundRect(RectF(2.2f, 2.2f, w - 2.2f, h - 2.2f), 4f, 4f, p)
    }

    // ------------------------------------------------------------------ the window

    /** Earth through the cupola: a real photograph, swapped for city lights at night. */
    private inner class Earth : View(home) {
        private var bmp: Bitmap? = null
        private var want = 0   // the photo asked for last; a slower one landing late is ignored
        private val p = paint()

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            load()
        }

        /** Off the main thread and cached; the old photo (or black) stays until the new one lands. */
        fun load() {
            val id = if (night) R.drawable.sp_earth_at_night else R.drawable.space_earth
            want = id
            val s = px(R_GLASS * 2 + 4).roundToInt()
            val b = plate(id, false, s, s) { b -> if (want == id) show(b) }
            if (b != null) show(b)
        }

        private fun show(b: Bitmap) {
            p.shader = null
            bmp = b   // shared with the cache: never recycled here
            shaderFor(width, height)
            invalidate()
        }

        private fun shaderFor(w: Int, h: Int) {
            val b = bmp ?: return
            if (w <= 0) return
            val s = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val m = Matrix()
            // cover the circle, centred (the night photo is wide: keep its middle)
            val sc = Math.max(w / b.width.toFloat(), h / b.height.toFloat())
            m.setScale(sc, sc)
            m.postTranslate((w - b.width * sc) / 2, (h - b.height * sc) / 2)
            s.setLocalMatrix(m)
            p.shader = s
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { shaderFor(w, h) }

        override fun onDraw(c: Canvas) {
            c.drawColor(0xFF000000.toInt())
            if (p.shader != null) c.drawCircle(width / 2f, height / 2f, width / 2f, p)
        }
    }
}
