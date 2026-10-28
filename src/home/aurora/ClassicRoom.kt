package home.aurora

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Classic: a Windows 95 desktop.  Everything is drawn one pixel at a time on the
 * 960 × 540 stage and shown at 2× without smoothing, like a 95 machine on a big
 * monitor: teal desktop, four-line bevels, pixel-art icons, a Programs window
 * holding the dock, a Date/Time Properties window and the taskbar.
 */
class ClassicRoom(h: HomeActivity, t: HomeTheme) : Room(h, t) {

    private companion object {
        const val TEAL = 0xFF008080.toInt()
        const val SILVER = 0xFFC0C0C0.toInt()
        const val LIGHT = 0xFFDFDFDF.toInt()
        const val GRAY = 0xFF808080.toInt()
        const val DARK = 0xFF0A0A0A.toInt()
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val NAVY = 0xFF000080.toInt()
        const val YELLOW = 0xFFFFFF00.toInt()
        const val OCHRE = 0xFFC8B400.toInt()
        const val RED = 0xFFE01010.toInt()
        const val MAROON = 0xFF800000.toInt()
        const val GREEN = 0xFF008000.toInt()
        const val LIME = 0xFF20D020.toInt()
        const val BROWN = 0xFF7A4A1E.toInt()
        const val FOCUS = 0xFFFFFF7F.toInt()   // the XOR of white over navy

        // Programs window, Date/Time window, taskbar
        const val PX = 176
        const val PY = 14
        const val PW = 516
        const val PH = 400
        const val DX = 700
        const val DY = 14
        const val DW = 252
        const val DH = 400
        const val BAR = 512
        const val CELL_W = 76
        const val CELL_H = 64
        const val COLS = 6
        const val ROWS = 5

        const val I_COMPUTER = 0
        const val I_BIN = 1
        const val I_PANEL = 2
        const val I_JOY = 3
        const val I_TV = 4
        const val I_PLUG = 5
        const val I_CLOCK = 6
        const val I_MOON = 7
        const val I_PROGRAMS = 8

        val HARD = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
        val SMOOTH = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val SANS: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    /** A 1:1 pixel canvas with hard edges, shown at the room's scale without smoothing. */
    private class Pix(w: Int, h: Int) {
        val w = max(1, w)
        val h = max(1, h)
        val bmp: Bitmap = Bitmap.createBitmap(this.w, this.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()

        init {
            p.isAntiAlias = false
            p.isFilterBitmap = false
            p.isDither = false
            p.isSubpixelText = false
            p.textSize = 11f
            p.typeface = SANS
        }

        fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
            if (w <= 0 || h <= 0) return
            p.style = Paint.Style.FILL
            p.color = color
            c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)
        }

        fun dot(x: Int, y: Int, color: Int) = rect(x, y, 1, 1, color)

        fun outline(x: Int, y: Int, w: Int, h: Int, color: Int) {
            rect(x, y, w, 1, color); rect(x, y + h - 1, w, 1, color)
            rect(x, y, 1, h, color); rect(x + w - 1, y, 1, h, color)
        }

        /** A line drawn with a square `t`-pixel brush. */
        fun line(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, t: Int = 1) {
            var x = x0
            var y = y0
            val dx = abs(x1 - x0)
            val dy = -abs(y1 - y0)
            val sx = if (x0 < x1) 1 else -1
            val sy = if (y0 < y1) 1 else -1
            var err = dx + dy
            while (true) {
                rect(x - (t - 1) / 2, y - (t - 1) / 2, t, t, color)
                if (x == x1 && y == y1) break
                val e2 = 2 * err
                if (e2 >= dy) { err += dy; x += sx }
                if (e2 <= dx) { err += dx; y += sy }
            }
        }

        fun disc(cx: Float, cy: Float, r: Float, color: Int) {
            p.style = Paint.Style.FILL
            p.color = color
            c.drawCircle(cx, cy, r, p)
        }

        /** Windows' four-line 3D edge: outer and inner top-left, inner and outer bottom-right. */
        fun edge(x: Int, y: Int, w: Int, h: Int, ol: Int, il: Int, ir: Int, or: Int) {
            rect(x, y, w, 1, ol); rect(x, y, 1, h, ol)
            rect(x + 1, y + 1, w - 2, 1, il); rect(x + 1, y + 1, 1, h - 2, il)
            rect(x + 1, y + h - 2, w - 2, 1, ir); rect(x + w - 2, y + 1, 1, h - 2, ir)
            rect(x, y + h - 1, w, 1, or); rect(x + w - 1, y, 1, h, or)
        }

        fun button(x: Int, y: Int, w: Int, h: Int) { rect(x, y, w, h, SILVER); edge(x, y, w, h, WHITE, LIGHT, GRAY, DARK) }
        fun pressed(x: Int, y: Int, w: Int, h: Int) { rect(x, y, w, h, SILVER); edge(x, y, w, h, DARK, GRAY, LIGHT, WHITE) }
        fun window(x: Int, y: Int, w: Int, h: Int) { rect(x, y, w, h, SILVER); edge(x, y, w, h, LIGHT, WHITE, GRAY, DARK) }
        fun field(x: Int, y: Int, w: Int, h: Int, fill: Int) { rect(x, y, w, h, fill); edge(x, y, w, h, GRAY, DARK, LIGHT, WHITE) }

        fun shallow(x: Int, y: Int, w: Int, h: Int) {
            rect(x, y, w, 1, GRAY); rect(x, y, 1, h, GRAY)
            rect(x, y + h - 1, w, 1, WHITE); rect(x + w - 1, y, 1, h, WHITE)
        }

        /** A group box's engraved line. */
        fun etched(x: Int, y: Int, w: Int, h: Int) {
            outline(x + 1, y + 1, w - 1, h - 1, WHITE)
            outline(x, y, w - 1, h - 1, GRAY)
        }

        fun dotted(x: Int, y: Int, w: Int, h: Int, color: Int) {
            var i = x
            while (i < x + w) { dot(i, y, color); dot(i, y + h - 1, color); i += 2 }
            var j = y
            while (j < y + h) { dot(x, j, color); dot(x + w - 1, j, color); j += 2 }
        }

        fun text(s: String, x: Int, baseline: Int, color: Int, bold: Boolean = false): Int {
            p.typeface = if (bold) BOLD else SANS
            p.style = Paint.Style.FILL
            p.color = color
            c.drawText(s, x.toFloat(), baseline.toFloat(), p)
            return width(s, bold)
        }

        fun width(s: String, bold: Boolean = false): Int {
            p.typeface = if (bold) BOLD else SANS
            return p.measureText(s).roundToInt()
        }

        /** A menu word with its access key underlined. */
        fun menu(s: String, x: Int, baseline: Int): Int {
            val w = text(s, x, baseline, BLACK)
            rect(x, baseline + 2, width(s.substring(0, 1)), 1, BLACK)
            return w
        }

        fun draw(b: Bitmap, x: Int, y: Int) = c.drawBitmap(b, x.toFloat(), y.toFloat(), HARD)

        /** Hand the finished pixels to the room's canvas, pixel-doubled. */
        fun show(target: Canvas, x: Float, y: Float) {
            target.drawBitmap(bmp, x, y, HARD)
            bmp.recycle()
        }
    }

    private var programsActive = true
    private var status = ""
    private var count = 0
    private lateinit var programs: View
    private lateinit var dateTime: View
    private lateinit var taskbar: View

    override fun build() {
        art { c -> val p = Pix(W, H); desktop(p); p.show(c, 0f, 0f) }

        // desktop icons, two columns down the left edge
        desk(I_COMPUTER, "My Computer", 4, 8) { AuroraSettings(home).tvInfo() }
        control(GAMES, 4f, 76f, 76f, CELL_H.toFloat(), cell(I_JOY) { label(GAMES) })
        control(LIVE, 4f, 144f, 76f, CELL_H.toFloat(), cell(I_TV) { label(LIVE) })
        control(INPUTS, 4f, 212f, 76f, CELL_H.toFloat(), cell(I_PLUG) { label(INPUTS) })
        desk(I_BIN, "Recycle Bin", 86, 8) { home.showHidden() }
        control(TIMER, 86f, 76f, 76f, CELL_H.toFloat(), cell(I_CLOCK) { label(TIMER) })
        control(SLEEP, 86f, 144f, 76f, CELL_H.toFloat(), cell(I_MOON) { label(SLEEP) })
        control(SETTINGS, 86f, 212f, 76f, CELL_H.toFloat(), cell(I_PANEL) { "Control Panel" })

        programs = drawn({ c -> val p = Pix(PW, PH); programsWindow(p); p.show(c, 0f, 0f) }, PX.toFloat(), PY.toFloat(), PW.toFloat(), PH.toFloat())
        dateTime = drawn({ c -> val p = Pix(DW, DH); dateTimeWindow(p); p.show(c, 0f, 0f) }, DX.toFloat(), DY.toFloat(), DW.toFloat(), DH.toFloat())
        taskbar = drawn({ c -> val p = Pix(W, H - BAR); taskbar(p); p.show(c, 0f, 0f) }, 0f, BAR.toFloat(), W.toFloat(), (H - BAR).toFloat())

        val start = control(ALL, 2f, (BAR + 4).toFloat(), 56f, 22f) { c, w, h, f -> val p = Pix(w.roundToInt(), h.roundToInt()); startButton(p, f); p.show(c, 0f, 0f) }
        start.contentDescription = "Start"
    }

    override fun setApps(apps: List<Apps.App>, back: Apps.App?) {
        clearApps()
        val n = min(apps.size, COLS * ROWS - 1)
        val ox = PX + 12
        val oy = PY + 44
        for (i in 0 until n) {
            val a = apps[i]
            val art = artOf(a)
            val name = if (back != null && i == 0) "Back to " + art.label else art.label
            app(a, (ox + i % COLS * CELL_W).toFloat(), (oy + i / COLS * CELL_H).toFloat(), CELL_W.toFloat(), CELL_H.toFloat(),
                    cellOf({ p -> art.drawIcon(p.c, 16f, 16f, 32f) }, { name }, false))
        }
        // the last icon opens Start ▸ Programs: every app
        val more = Widget(home, this, cellOf({ p -> icon(p, I_PROGRAMS) }, { "All Programs" }, false))
        more.contentDescription = "All apps"
        more.setOnClickListener { v -> home.act(ALL, v) }
        more.hook = Hook { has -> if (has) { home.actionFocused("All apps"); focusIn(true, "All Programs") } }
        appViews.add(more)
        place(more, (ox + n % COLS * CELL_W).toFloat(), (oy + n / COLS * CELL_H).toFloat(), CELL_W.toFloat(), CELL_H.toFloat())
        count = n + 1
        programs.invalidate()
    }

    override fun onAppFocus(a: Apps.App, v: Widget) = focusIn(true, a.label)

    override fun onActionFocus(action: Int, v: Widget) = focusIn(false, "")

    private fun focusIn(inPrograms: Boolean, what: String) {
        if (inPrograms) status = what
        if (inPrograms == programsActive && !inPrograms) return
        programsActive = inPrograms
        programs.invalidate()
        taskbar.invalidate()
    }

    override fun tick(now: Date, hhmm: String) {
        super.tick(now, hhmm)
        if (::dateTime.isInitialized) { dateTime.invalidate(); taskbar.invalidate() }
    }

    override fun onReturn() {}   // 95 just repaints

    // ------------------------------------------------------------------ desktop

    private fun desk(kind: Int, name: String, x: Int, y: Int, open: () -> Unit) {
        val v = Widget(home, this, cell(kind) { name })
        v.contentDescription = name
        v.setOnClickListener { Sounds.select(home); open() }
        v.hook = Hook { has -> if (has) { home.actionFocused(name); focusIn(false, "") } }
        place(v, x.toFloat(), y.toFloat(), 76f, CELL_H.toFloat())
    }

    private fun cell(kind: Int, name: () -> String): Face = cellOf({ p -> icon(p, kind) }, name, true)

    /** An icon with its caption: navy caption and a dithered icon when selected. */
    private fun cellOf(drawIcon: (Pix) -> Unit, name: () -> String, onDesktop: Boolean): Face = Face { c, w, h, on ->
        val p = Pix(w.roundToInt(), h.roundToInt())
        val ic = Pix(32, 32)
        drawIcon(ic)
        if (on) dither(ic.bmp)
        val ix = (p.w - 32) / 2
        p.draw(ic.bmp, ix, 3)
        ic.bmp.recycle()
        val lines = wrap(name(), p.w - 6, p)
        var lw = 0
        for (l in lines) lw = max(lw, p.width(l))
        lw += 4
        val lh = lines.size * 12 + 2
        val lx = (p.w - lw) / 2
        val ly = 38
        if (on) { p.rect(lx, ly, lw, lh, NAVY); p.dotted(lx, ly, lw, lh, FOCUS) }
        for (i in lines.indices) p.text(lines[i], (p.w - p.width(lines[i])) / 2, ly + 10 + i * 12, if (on || onDesktop) WHITE else BLACK)
        p.show(c, 0f, 0f)
    }

    /** Explorer's "selected" look: every other pixel of the icon turns navy. */
    private fun dither(b: Bitmap) {
        for (y in 0 until b.height) for (x in 0 until b.width) {
            if ((x + y) % 2 == 0 && (b.getPixel(x, y) ushr 24) > 0x80) b.setPixel(x, y, NAVY)
        }
    }

    private fun wrap(s: String, max: Int, p: Pix): List<String> {
        if (p.width(s) <= max) return listOf(s)
        val words = s.split(" ")
        var first = ""
        var i = 0
        while (i < words.size) {
            val next = if (first.isEmpty()) words[i] else first + " " + words[i]
            if (p.width(next) > max && first.isNotEmpty()) break
            first = next
            i++
        }
        val rest = words.drop(i).joinToString(" ")
        val out = ArrayList<String>()
        out.add(clip(first, max, p))
        if (rest.isNotEmpty()) out.add(clip(rest, max, p))
        return out
    }

    private fun clip(s: String, max: Int, p: Pix): String {
        if (p.width(s) <= max) return s
        var t = s
        while (t.length > 1 && p.width("$t...") > max) t = t.substring(0, t.length - 1)
        return "$t..."
    }

    private fun desktop(p: Pix) {
        p.rect(0, 0, W, H, TEAL)
        val c = p.c
        when (holiday) {
            "halloween" -> { pumpkin(c, 46f, 500f, 15f); bat(c, 120f, 448f, 12f, BLACK); bat(c, 150f, 470f, 8f, BLACK) }
            "christmas" -> wreath(c, 60f, 450f, 26f)
            "newyear" -> { burst(c, 50f, 440f, 22f, YELLOW); burst(c, 120f, 470f, 16f, WHITE) }
            "july4" -> { burst(c, 50f, 440f, 22f, 0xFFE04030.toInt()); burst(c, 120f, 470f, 16f, WHITE) }
        }
    }

    // ------------------------------------------------------------------ windows

    private fun frame(p: Pix, w: Int, h: Int, title: String, active: Boolean, kind: Int, help: Boolean) {
        p.window(0, 0, w, h)
        p.rect(3, 3, w - 6, 18, if (active) NAVY else GRAY)
        small(p, kind, 5, 4)
        p.text(title, 23, 16, if (active) WHITE else SILVER, true)
        val by = 5
        val close = w - 5 - 16
        titleButton(p, close, by) { x, y -> for (i in 0 until 7) { p.rect(x + 4 + i, y + 3 + i, 2, 1, BLACK); p.rect(x + 10 - i, y + 3 + i, 2, 1, BLACK) } }
        if (help) {
            titleButton(p, close - 18, by) { x, y -> p.text("?", x + 5, y + 11, BLACK, true) }
        } else {
            titleButton(p, close - 18, by) { x, y -> p.outline(x + 3, y + 2, 9, 9, BLACK); p.rect(x + 3, y + 3, 9, 1, BLACK) }
            titleButton(p, close - 34, by) { x, y -> p.rect(x + 4, y + 9, 6, 2, BLACK) }
        }
    }

    private fun titleButton(p: Pix, x: Int, y: Int, glyph: (Int, Int) -> Unit) {
        p.button(x, y, 16, 14)
        glyph(x, y)
    }

    private fun programsWindow(p: Pix) {
        frame(p, PW, PH, "Aurora Home", programsActive, I_PROGRAMS, false)
        var mx = 10
        for (m in arrayOf("File", "Edit", "View", "Help")) mx += p.menu(m, mx, 35) + 14
        p.field(3, 42, PW - 6, PH - 42 - 24, WHITE)
        val sy = PH - 21
        p.shallow(3, sy, 150, 18)
        p.text("$count object(s)", 8, sy + 13, BLACK)
        p.shallow(156, sy, PW - 159, 18)
        p.text(clip(status, PW - 172, p), 161, sy + 13, BLACK)
    }

    private fun dateTimeWindow(p: Pix) {
        val w = DW
        val h = DH
        frame(p, w, h, "Date/Time Properties", false, I_CLOCK, true)
        val cal = Calendar.getInstance()
        cal.time = now

        // tabs and the page they sit on
        p.button(6, 44, w - 12, h - 44 - 36)
        p.rect(8, 44, w - 16, 2, SILVER)
        p.button(6, 26, 86, 20)
        p.rect(8, 44, 82, 2, SILVER)
        p.text("Date & Time", 13, 40, BLACK)
        p.rect(92, 29, 74, 16, SILVER)
        p.rect(92, 29, 72, 1, WHITE); p.rect(165, 30, 1, 14, DARK); p.rect(164, 30, 1, 14, GRAY)
        p.text("Time Zone", 101, 41, BLACK)
        p.rect(6, 45, 1, 1, WHITE)

        // Date: month, year, the month's calendar
        p.etched(14, 58, w - 28, 156)
        p.rect(20, 52, p.width("Date") + 6, 12, SILVER)
        p.text("Date", 23, 62, BLACK)
        val month = Room.fmt("MMMM", now)
        combo(p, 22, 70, 116, month)
        spinner(p, 144, 70, w - 144 - 22, Room.fmt("yyyy", now))
        val gx = 22
        val gy = 96
        val gw = w - 44
        p.field(gx, gy, gw, 112, WHITE)
        val cw = (gw - 4) / 7
        val days = arrayOf("S", "M", "T", "W", "T", "F", "S")
        p.rect(gx + 2, gy + 2, gw - 4, 14, GRAY)
        for (i in 0 until 7) p.text(days[i], gx + 2 + i * cw + (cw - p.width(days[i])) / 2, gy + 13, WHITE)
        val first = cal.clone() as Calendar
        first.set(Calendar.DAY_OF_MONTH, 1)
        val lead = first.get(Calendar.DAY_OF_WEEK) - 1
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val n = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (d in 1..n) {
            val i = d - 1 + lead
            val x = gx + 2 + (i % 7) * cw
            val y = gy + 18 + (i / 7) * 15
            val s = d.toString()
            if (d == today) p.rect(x + 2, y, cw - 4, 14, NAVY)
            p.text(s, x + (cw - p.width(s)) / 2, y + 11, if (d == today) WHITE else BLACK)
        }

        // Time: the analog clock and its field
        p.etched(14, 226, w - 28, h - 226 - 46)
        p.rect(20, 220, p.width("Time") + 6, 12, SILVER)
        p.text("Time", 23, 230, BLACK)
        analog(p, w / 2, 280, 40, cal)
        val t = Room.fmt("h:mm a", now)
        spinner(p, (w - 112) / 2, 328, 112, t)

        // OK, Cancel, Apply
        val bw = 70
        val bx = w - 8 - bw * 3 - 8
        for ((i, s) in arrayOf("OK", "Cancel", "Apply").withIndex()) {
            val x = bx + i * (bw + 4)
            p.button(x, h - 30, bw, 22)
            val tx = x + (bw - p.width(s)) / 2
            if (i == 2) p.text(s, tx + 1, h - 14, WHITE)   // greyed out, engraved
            p.text(s, tx, h - 15, if (i == 2) GRAY else BLACK)
        }
        p.outline(bx - 1, h - 31, bw + 2, 24, BLACK)
    }

    private fun combo(p: Pix, x: Int, y: Int, w: Int, s: String) {
        p.field(x, y, w, 20, WHITE)
        p.text(s, x + 5, y + 14, BLACK)
        p.button(x + w - 18, y + 2, 16, 16)
        arrow(p, x + w - 14, y + 8, true)
    }

    private fun spinner(p: Pix, x: Int, y: Int, w: Int, s: String) {
        p.field(x, y, w, 20, WHITE)
        p.text(s, x + 5, y + 14, BLACK)
        p.button(x + w - 18, y + 2, 16, 8)
        p.button(x + w - 18, y + 10, 16, 8)
        arrow(p, x + w - 14, y + 4, false)
        arrow(p, x + w - 14, y + 12, true)
    }

    private fun arrow(p: Pix, x: Int, y: Int, down: Boolean) {
        for (i in 0 until 4) {
            val row = if (down) i else 3 - i
            p.rect(x + i, y + row, 7 - i * 2, 1, BLACK)
        }
    }

    private fun analog(p: Pix, cx: Int, cy: Int, r: Int, cal: Calendar) {
        for (i in 0 until 60) {
            val a = i / 60.0 * Math.PI * 2
            val x = cx + (sin(a) * r).roundToInt()
            val y = cy - (cos(a) * r).roundToInt()
            if (i % 5 == 0) {
                p.rect(x - 1, y - 1, 3, 3, TEAL)
                p.dot(x - 1, y - 1, WHITE)
                p.rect(x + 1, y - 1, 1, 3, BLACK); p.rect(x - 1, y + 1, 3, 1, BLACK)
            } else p.dot(x, y, GRAY)
        }
        val m = cal.get(Calendar.MINUTE)
        val hr = cal.get(Calendar.HOUR) + m / 60.0
        val ha = hr / 12 * Math.PI * 2
        val ma = m / 60.0 * Math.PI * 2
        p.line(cx, cy, cx + (sin(ha) * r * 0.55).roundToInt(), cy - (cos(ha) * r * 0.55).roundToInt(), TEAL, 3)
        p.line(cx, cy, cx + (sin(ma) * r * 0.82).roundToInt(), cy - (cos(ma) * r * 0.82).roundToInt(), TEAL, 2)
        p.rect(cx - 1, cy - 1, 3, 3, BLACK)
    }

    // ------------------------------------------------------------------ taskbar

    private fun taskbar(p: Pix) {
        val w = W
        p.rect(0, 0, w, H - BAR, SILVER)
        p.rect(0, 0, w, 1, LIGHT)
        p.rect(0, 1, w, 1, WHITE)
        task(p, 62, "Aurora Home", I_PROGRAMS, programsActive)
        task(p, 62 + 164, "Date/Time Properties", I_CLOCK, false)
        val tx = w - 2 - 100
        p.shallow(tx, 4, 100, 22)
        speaker(p, tx + 5, 7)
        val t = Room.fmt("h:mm a", now)
        p.text(t, tx + 100 - 8 - p.width(t), 19, BLACK)
    }

    private fun task(p: Pix, x: Int, s: String, kind: Int, down: Boolean) {
        val w = 160
        if (down) {
            p.pressed(x, 4, w, 22)
            for (yy in 6 until 24) for (xx in x + 2 until x + w - 2) if ((xx + yy) % 2 == 0) p.dot(xx, yy, WHITE)
        } else p.button(x, 4, w, 22)
        val o = if (down) 1 else 0
        small(p, kind, x + 4 + o, 7 + o)
        p.text(clip(s, w - 30, p), x + 23 + o, 19 + o, BLACK, down)
    }

    private fun startButton(p: Pix, on: Boolean) {
        if (on) {
            p.outline(0, 0, p.w, p.h, BLACK)
            p.rect(1, 1, p.w - 2, p.h - 2, SILVER)
            p.edge(1, 1, p.w - 2, p.h - 2, WHITE, LIGHT, GRAY, DARK)
            p.dotted(4, 4, p.w - 8, p.h - 8, BLACK)
        } else p.button(0, 0, p.w, p.h)
        flag(p, 5, 4)
        p.text("Start", 24, 16, BLACK, true)
    }

    /** A waving four-pane flag with a trail behind it. */
    private fun flag(p: Pix, x: Int, y: Int) {
        val wave = intArrayOf(1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2)
        for (i in 0 until 14) {
            val xx = x + 3 + i
            if (i == 6) continue
            val yy = y + wave[i]
            p.rect(xx, yy, 1, 5, if (i < 6) RED else 0xFF20A030.toInt())
            p.rect(xx, yy + 6, 1, 5, if (i < 6) 0xFF2858B0.toInt() else 0xFFF0C020.toInt())
        }
        for (j in 0 until 4) { p.dot(x, y + 2 + j * 3, BLACK); p.dot(x + 1, y + 1 + j * 3, BLACK) }
    }

    private fun speaker(p: Pix, x: Int, y: Int) {
        p.rect(x + 1, y + 5, 4, 6, BLACK)
        p.rect(x + 2, y + 6, 2, 4, YELLOW)
        for (i in 0 until 5) p.rect(x + 5 + i, y + 5 - i, 1, 6 + i * 2, BLACK)
        for (i in 0 until 3) p.rect(x + 6 + i, y + 6 - i, 1, 4 + i * 2, YELLOW)
    }

    /** A 16-pixel version of a 32-pixel icon, for title bars and the taskbar. */
    private fun small(p: Pix, kind: Int, x: Int, y: Int) {
        val ic = Pix(32, 32)
        icon(ic, kind)
        p.c.drawBitmap(ic.bmp, Rect(0, 0, 32, 32), RectF(x.toFloat(), y.toFloat(), x + 16f, y + 16f), SMOOTH)
        ic.bmp.recycle()
    }

    // ------------------------------------------------------------------ 32 × 32 icons

    private fun icon(p: Pix, kind: Int) {
        when (kind) {
            I_COMPUTER -> computer(p)
            I_BIN -> bin(p)
            I_PANEL -> { folder(p); gear(p, 22, 21) }
            I_JOY -> joystick(p)
            I_TV -> tv(p)
            I_PLUG -> plug(p)
            I_CLOCK -> alarm(p)
            I_MOON -> moon(p)
            else -> { folder(p); window(p, 13, 13) }
        }
    }

    private fun computer(p: Pix) {
        // monitor
        p.outline(4, 1, 24, 20, BLACK)
        p.rect(5, 2, 22, 18, SILVER)
        p.rect(5, 2, 22, 1, WHITE); p.rect(5, 2, 1, 18, WHITE)
        p.rect(5, 19, 22, 1, GRAY); p.rect(26, 2, 1, 18, GRAY)
        p.rect(7, 4, 18, 13, BLACK)
        p.rect(8, 5, 16, 11, TEAL)
        p.dot(9, 6, WHITE); p.dot(10, 6, 0xFF40C0C0.toInt()); p.dot(9, 7, 0xFF40C0C0.toInt())
        p.dot(23, 18, LIME)
        // neck and system box
        p.rect(12, 21, 8, 2, GRAY); p.rect(11, 21, 1, 2, BLACK); p.rect(20, 21, 1, 2, BLACK)
        p.outline(1, 23, 30, 8, BLACK)
        p.rect(2, 24, 28, 6, SILVER)
        p.rect(2, 24, 28, 1, WHITE); p.rect(2, 24, 1, 6, WHITE)
        p.rect(2, 29, 28, 1, GRAY)
        p.rect(17, 26, 10, 1, BLACK); p.rect(17, 27, 10, 1, WHITE)
        p.rect(5, 26, 2, 2, GREEN)
    }

    private fun bin(p: Pix) {
        // rim
        p.rect(9, 4, 14, 1, BLACK)
        p.rect(8, 5, 16, 2, GRAY); p.dot(8, 5, BLACK); p.dot(23, 5, BLACK); p.rect(9, 5, 13, 1, LIGHT)
        // a wire-mesh can narrowing toward the floor
        for (y in 7 until 30) {
            val inset = (y - 7) * 3 / 22
            val l = 7 + inset
            val r = 24 - inset
            p.dot(l, y, BLACK); p.dot(r, y, BLACK)
            for (x in l + 1 until r) {
                val mesh = (x + y) % 4 == 0 || (x - y + 64) % 4 == 0
                val shade = x > r - 5
                p.dot(x, y, if (mesh) GRAY else if (shade) SILVER else WHITE)
            }
        }
        p.rect(10, 29, 12, 1, BLACK)
        p.rect(11, 30, 12, 1, GRAY)
    }

    private fun folder(p: Pix) {
        p.outline(2, 6, 11, 4, BLACK)
        p.rect(3, 7, 9, 3, OCHRE)
        p.outline(2, 9, 28, 19, BLACK)
        p.rect(3, 10, 26, 17, OCHRE)
        // the front flap
        p.outline(2, 12, 28, 16, BLACK)
        p.rect(3, 13, 26, 14, YELLOW)
        p.rect(3, 13, 26, 1, 0xFFFFFFC0.toInt())
        p.rect(3, 13, 1, 14, 0xFFFFFFC0.toInt())
        p.rect(3, 26, 26, 1, OCHRE)
        p.rect(30, 10, 1, 19, GRAY); p.rect(3, 28, 28, 1, GRAY)
    }

    private fun window(p: Pix, x: Int, y: Int) {
        p.outline(x, y, 16, 13, BLACK)
        p.rect(x + 1, y + 1, 14, 11, WHITE)
        p.rect(x + 1, y + 1, 14, 3, NAVY)
        p.rect(x + 12, y + 2, 2, 1, WHITE)
        p.rect(x + 3, y + 6, 6, 1, GRAY); p.rect(x + 3, y + 8, 9, 1, GRAY)
    }

    private fun gear(p: Pix, cx: Int, cy: Int) {
        for (i in 0 until 8) {
            val a = i * Math.PI / 4
            val x = cx + (cos(a) * 6).roundToInt()
            val y = cy + (sin(a) * 6).roundToInt()
            p.rect(x - 1, y - 1, 3, 3, BLACK)
        }
        p.disc(cx + 0.5f, cy + 0.5f, 6f, BLACK)
        p.disc(cx + 0.5f, cy + 0.5f, 5f, SILVER)
        p.dot(cx - 2, cy - 3, WHITE); p.dot(cx - 3, cy - 2, WHITE)
        p.disc(cx + 0.5f, cy + 0.5f, 2f, GRAY)
    }

    private fun joystick(p: Pix) {
        // base
        p.outline(5, 20, 23, 10, BLACK)
        p.rect(6, 21, 21, 8, 0xFF404040.toInt())
        p.rect(6, 21, 21, 1, GRAY)
        p.rect(7, 29, 21, 1, GRAY)
        // fire button
        p.rect(8, 18, 5, 3, BLACK); p.rect(9, 18, 3, 2, RED)
        // stick and ball
        p.rect(15, 9, 3, 12, BLACK)
        p.rect(16, 9, 1, 12, GRAY)
        p.disc(16.5f, 7f, 5.5f, BLACK)
        p.disc(16.5f, 7f, 4.5f, RED)
        p.rect(14, 4, 2, 2, 0xFFFF9090.toInt())
        p.rect(14, 23, 6, 2, BLACK)
    }

    private fun tv(p: Pix) {
        p.line(16, 8, 10, 1, BLACK); p.line(16, 8, 22, 1, BLACK)
        p.outline(2, 8, 28, 21, BLACK)
        p.rect(3, 9, 26, 19, BROWN)
        p.rect(3, 9, 26, 1, 0xFFB07840.toInt()); p.rect(3, 9, 1, 19, 0xFFB07840.toInt())
        p.rect(3, 27, 26, 1, 0xFF4A2A10.toInt())
        p.rect(5, 11, 18, 15, BLACK)
        p.rect(6, 12, 16, 13, TEAL)
        p.dot(7, 13, WHITE); p.dot(8, 13, 0xFF40C0C0.toInt()); p.dot(7, 14, 0xFF40C0C0.toInt())
        p.rect(24, 12, 3, 3, SILVER); p.dot(24, 12, WHITE)
        p.rect(24, 17, 3, 3, SILVER); p.dot(24, 17, WHITE)
        p.rect(24, 22, 3, 1, BLACK); p.rect(24, 24, 3, 1, BLACK)
        p.rect(4, 29, 3, 2, BLACK); p.rect(25, 29, 3, 2, BLACK)
    }

    private fun plug(p: Pix) {
        // prongs
        p.outline(11, 2, 4, 9, BLACK); p.rect(12, 3, 2, 7, 0xFFE0C040.toInt())
        p.outline(18, 2, 4, 9, BLACK); p.rect(19, 3, 2, 7, 0xFFE0C040.toInt())
        // body
        p.outline(8, 10, 17, 12, BLACK)
        p.rect(9, 11, 15, 10, SILVER)
        p.rect(9, 11, 15, 1, WHITE); p.rect(9, 11, 1, 10, WHITE)
        p.rect(9, 20, 15, 1, GRAY); p.rect(23, 11, 1, 10, GRAY)
        p.rect(12, 22, 9, 3, BLACK); p.rect(13, 22, 7, 2, GRAY)
        // cable
        p.line(16, 25, 16, 27, BLACK, 2)
        p.line(16, 28, 10, 30, BLACK, 2)
        p.line(10, 30, 3, 29, BLACK, 2)
    }

    private fun alarm(p: Pix) {
        p.disc(8f, 6f, 5f, BLACK); p.disc(8f, 6f, 4f, SILVER); p.dot(6, 4, WHITE)
        p.disc(24f, 6f, 5f, BLACK); p.disc(24f, 6f, 4f, SILVER); p.dot(22, 4, WHITE)
        p.line(9, 27, 6, 30, BLACK, 2); p.line(23, 27, 26, 30, BLACK, 2)
        p.disc(16f, 17f, 12.5f, BLACK)
        p.disc(16f, 17f, 11.5f, MAROON)
        p.disc(16f, 17f, 9.5f, BLACK)
        p.disc(16f, 17f, 8.5f, WHITE)
        p.rect(15, 9, 2, 2, BLACK); p.rect(15, 24, 2, 2, BLACK); p.rect(8, 16, 2, 2, BLACK); p.rect(23, 16, 2, 2, BLACK)
        p.line(16, 17, 16, 11, BLACK); p.line(16, 17, 20, 17, BLACK)
        p.dot(12, 12, 0xFFE0E0E0.toInt())
    }

    private fun moon(p: Pix) {
        val c = p.c
        p.disc(14f, 17f, 12f, BLACK)
        p.disc(14f, 17f, 11f, YELLOW)
        c.save()
        val clip = Path()
        clip.addCircle(14f, 17f, 12f, Path.Direction.CW)
        c.clipPath(clip)
        p.disc(21f, 12f, 11f, BLACK)
        c.restore()
        val clear = Paint()
        clear.isAntiAlias = false
        clear.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        c.drawCircle(21f, 12f, 10f, clear)
        p.dot(9, 12, 0xFFFFFFC0.toInt()); p.dot(8, 14, 0xFFFFFFC0.toInt())
        // z z
        zed(p, 22, 17, 4); zed(p, 27, 9, 3)
    }

    private fun zed(p: Pix, x: Int, y: Int, s: Int) {
        p.rect(x, y, s, 1, NAVY)
        for (i in 1 until s - 1) p.dot(x + s - 1 - i, y + i, NAVY)
        p.rect(x, y + s - 1, s, 1, NAVY)
    }
}
