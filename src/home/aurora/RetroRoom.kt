package home.aurora

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.Date
import java.util.Locale
import java.util.Random

/**
 * Retro: the whole screen is the front of a 1960s Japanese television.  A
 * cream enamel bezel with slatted speakers frames a rounded picture tube; the
 * ribbed panel below has piano-key channel presets (your apps), push buttons
 * for the everyday controls and a chrome tuning dial for every channel.
 * Choosing a channel presses its key, turns the dial and tunes the tube.
 */
class RetroRoom(h: HomeActivity, t: HomeTheme) : Room(h, t) {

    private companion object {
        const val CREAM = 0xFFEFE3C6.toInt()
        const val CREAM_HI = 0xFFF8F0DC.toInt()
        const val CREAM_LO = 0xFFD9C9A4.toInt()
        const val INK = 0xFF3A2414.toInt()
        const val GOLD = 0xFFB8893A.toInt()
        const val AMBER = 0xFFFFB347.toInt()
        const val OSD = 0xFFB8F0A8.toInt()

        val BEZEL = RectF(18f, 16f, 942f, 404f)
        val TUBE = RectF(150f, 40f, 810f, 380f)
        const val TUBE_R = 46f
        val PANEL = RectF(18f, 412f, 942f, 526f)
        const val KEYS_X = 44f
        const val KEYS_Y = 422f
        const val KEY_W = 92f
        const val KEY_H = 38f
        const val KEY_GAP = 7f
        const val PRESETS = 6
        const val BTN_Y = 470f
        const val BTN_W = 99f
        const val DIAL_X = 776f
        const val DIAL_Y = 469f
        const val DIAL_R = 44f
    }

    private val chunky = tf("sans-serif-black", Typeface.NORMAL)
    private val cond = tf("sans-serif-condensed", Typeface.BOLD)
    private val osd = tf("monospace", Typeface.BOLD)
    private lateinit var tube: Tube
    private lateinit var dial: Widget
    private var dialAngle = 0f
    private var dialAnim: ValueAnimator? = null
    private val blanks = ArrayList<View>()

    override fun build() {
        art { c -> front(c) }
        tube = place(Tube(), TUBE.left, TUBE.top, TUBE.width(), TUBE.height())
        clock = ClockFace { s -> hhmm = s; tube.invalidate() }

        // push buttons for the everyday controls, along the bottom of the panel
        val order = intArrayOf(GAMES, INPUTS, LIVE, TIMER, SLEEP, SETTINGS)
        for (i in order.indices) {
            val a = order[i]
            control(a, KEYS_X + i * BTN_W, BTN_Y, BTN_W - 4, 44f) { c, w, h, f -> button(c, w, h, f, label(a)) }
        }

        // the tuning dial: every channel
        dial = control(ALL, DIAL_X - DIAL_R - 8, DIAL_Y - DIAL_R - 8, DIAL_R * 2 + 16, DIAL_R * 2 + 16) { c, _, _, f -> knob(c, f) }
    }

    override fun setApps(apps: List<Apps.App>, back: Apps.App?) {
        clearApps()
        val n = Math.min(apps.size, PRESETS)
        for (i in 0 until n) {
            val a = apps[i]
            val art = artOf(a)
            val ch = (i + 1).toString()
            val last = back != null && i == 0
            app(a, KEYS_X + i * (KEY_W + KEY_GAP), KEYS_Y, KEY_W, KEY_H + 4) { c, w, _, f -> key(c, w, f, ch, if (last) "LAST: " + art.label else art.label) }
        }
        // presets nobody has set yet are plain keys
        for (b in blanks) stage.removeView(b)
        blanks.clear()
        for (i in n until PRESETS) blanks.add(drawn({ c -> key(c, KEY_W, false, "", "") }, KEYS_X + i * (KEY_W + KEY_GAP), KEYS_Y, KEY_W, KEY_H + 4))
        if (apps.isNotEmpty()) tune(0, artOf(apps[0]), -1, apps[0].label, false)
    }

    override fun firstApp(): View? = if (appViews.isEmpty()) actions[GAMES] else appViews[0]

    override fun onAppFocus(a: Apps.App, v: Widget) {
        tune(appViews.indexOf(v), artOf(a), -1, a.label, true)
    }

    override fun onActionFocus(action: Int, v: Widget) {
        tune(-1, null, action, if (action == ALL) "ALL CHANNELS" else label(action).uppercase(Locale.getDefault()), true)
    }

    private fun tune(preset: Int, art: AppArt?, action: Int, name: String, turn: Boolean) {
        tube.show(if (preset < 0) 0 else preset + 1, art, action, name)
        // the dial clicks round to the channel: twelve detents
        val to = if (preset >= 0) preset * 30f else if (action == ALL) 330f else dialAngle
        if (!turn) { dialAngle = to; dial.invalidate(); return }
        dialAnim?.cancel()
        val a = ValueAnimator.ofFloat(dialAngle, to)
        a.duration = 280
        a.interpolator = DecelerateInterpolator(2f)
        a.addUpdateListener { x -> dialAngle = x.animatedValue as Float; dial.invalidate() }
        dialAnim = a
        a.start()
    }

    override fun tick(now: Date, hhmm: String) {
        super.tick(now, hhmm)
        tube.invalidate()
    }

    override fun setStatus(timer: String, sleep: String) {
        super.setStatus(timer, sleep)
        tube.invalidate()
    }

    /** Coming home: the set warms up, a bright line opening into the picture. */
    override fun onReturn() { tube.powerOn() }

    override fun label(action: Int): String {
        val s = super.label(action)
        return if (action == SLEEP && s == "Sleep timer") "Sleep" else s
    }

    // ------------------------------------------------------------------ the cabinet, painted once

    private fun front(c: Canvas) {
        val p = paint()
        val soft = paint()

        // the walnut cabinet's front edge
        p.alpha = 255; p.shader = texture(R.drawable.tx_walnut, 0.6f, 90f)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(0x33FFE6C8, 0x00000000, 0x66000000), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null

        // cream enamel: the bezel and the control panel, each a slightly raised plate
        for (r in arrayOf(BEZEL, PANEL)) {
            soft.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
            soft.color = 0xB3000000.toInt()
            c.drawRoundRect(RectF(r.left + 1, r.top + 3, r.right + 1, r.bottom + 4), 10f, 10f, soft)
            enamel(c, r, 10f)
        }

        // the bezel steps down toward the tube
        val lip = RectF(TUBE.left - 22, TUBE.top - 16, TUBE.right + 22, TUBE.bottom + 16)
        p.alpha = 255; p.shader = LinearGradient(0f, lip.top, 0f, lip.bottom, intArrayOf(0xFFE2D3B0.toInt(), 0xFFEADDBE.toInt(), 0xFFF4EAD2.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(lip, TUBE_R + 16, TUBE_R + 16, p)
        p.shader = null
        stroke(c, lip, TUBE_R + 16, 1.4f, 0x59000000, 0x80FFFFFF.toInt())
        // the tube's dark surround
        val mask = RectF(TUBE.left - 6, TUBE.top - 5, TUBE.right + 6, TUBE.bottom + 5)
        p.color = 0xFF0E0D0B.toInt()
        c.drawRoundRect(mask, TUBE_R + 5, TUBE_R + 5, p)
        stroke(c, mask, TUBE_R + 5, 1.2f, 0x99000000.toInt(), 0x59FFFFFF)

        // slatted speakers either side of the tube
        speaker(c, RectF(44f, 64f, 116f, 356f))
        speaker(c, RectF(844f, 64f, 916f, 356f))

        // the badge under the tube
        val face = tf("serif", Typeface.BOLD_ITALIC)
        centerText(c, "Aurora", W / 2f + 0.6f, 396.6f, textPaint(15f, 0x40000000, face))
        val badge = textPaint(15f, GOLD, face)
        badge.setShadowLayer(0.8f, 0f, -0.6f, 0x99FFFFFF.toInt())
        centerText(c, "Aurora", W / 2f, 396f, badge)

        // the control panel: fine ribs
        val inner = RectF(PANEL.left + 6, PANEL.top + 5, PANEL.right - 6, PANEL.bottom - 5)
        val ribs = Path()
        ribs.addRoundRect(inner, 6f, 6f, Path.Direction.CW)
        c.save()
        c.clipPath(ribs)
        var y = PANEL.top + 6
        while (y < PANEL.bottom) {
            p.color = 0x1F5A3A1A
            c.drawRect(PANEL.left, y, PANEL.right, y + 0.8f, p)
            p.color = 0x40FFFFFF
            c.drawRect(PANEL.left, y + 0.8f, PANEL.right, y + 1.4f, p)
            y += 3.2f
        }
        c.restore()
        stroke(c, inner, 6f, 1f, 0x40000000, 0x99FFFFFF.toInt())
        // the recess the preset keys sit in
        val well = RectF(KEYS_X - 6, KEYS_Y - 5, KEYS_X + PRESETS * (KEY_W + KEY_GAP) - KEY_GAP + 6, KEYS_Y + KEY_H + 6)
        p.color = 0xFF2A1C12.toInt()
        c.drawRoundRect(well, 5f, 5f, p)
        stroke(c, well, 5f, 1f, 0xB3000000.toInt(), 0x80FFFFFF.toInt())
        // dial scale: a black ring with channel numbers
        soft.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        soft.color = 0x99000000.toInt()
        c.drawCircle(DIAL_X + 1, DIAL_Y + 3, DIAL_R + 12, soft)
        p.alpha = 255; p.shader = RadialGradient(DIAL_X, DIAL_Y - 10, DIAL_R + 16, intArrayOf(0xFF2E2620.toInt(), 0xFF141210.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(DIAL_X, DIAL_Y, DIAL_R + 12, p)
        p.shader = null
        stroke(c, RectF(DIAL_X - DIAL_R - 12, DIAL_Y - DIAL_R - 12, DIAL_X + DIAL_R + 12, DIAL_Y + DIAL_R + 12), DIAL_R + 12, 1.2f, 0x80FFFFFF.toInt(), 0x66000000)
        val num = textPaint(6.5f, CREAM, cond)
        p.color = CREAM
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90)
            val cs = Math.cos(a).toFloat()
            val sn = Math.sin(a).toFloat()
            c.drawCircle(DIAL_X + cs * (DIAL_R + 3.5f), DIAL_Y + sn * (DIAL_R + 3.5f), 1f, p)
            centerText(c, if (i == 11) "≡" else (i + 1).toString(), DIAL_X + cs * (DIAL_R + 8.5f), DIAL_Y + sn * (DIAL_R + 8.5f) + 2.3f, num)
        }
        // a small printed plate beside the dial
        val plate = RectF(844f, 446f, 922f, 492f)
        p.color = 0xFF2A1C12.toInt()
        c.drawRoundRect(plate, 3f, 3f, p)
        stroke(c, plate, 3f, 1f, 0xB3000000.toInt(), 0x80FFFFFF.toInt())
        val pl = textPaint(8f, 0xFFE9D8A8.toInt(), cond)
        pl.letterSpacing = 0.12f
        centerText(c, "CHANNEL", plate.centerX(), plate.top + 19, pl)
        centerText(c, "TUNING", plate.centerX(), plate.top + 31, pl)

        holidayProps(c)
    }

    /** Cream enamel on a raised plate: light from above, a hint of texture, crisp edges. */
    private fun enamel(c: Canvas, r: RectF, radius: Float) {
        val p = paint()
        p.alpha = 255; p.shader = LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(CREAM_HI, CREAM, CREAM_LO), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, radius, radius, p)
        p.shader = null
        val clip = Path()
        clip.addRoundRect(r, radius, radius, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        val rnd = Random(r.top.toLong())
        val n = (r.width() * r.height() / 40).toInt()
        for (i in 0 until n) {
            p.color = if (rnd.nextBoolean()) 0x0A5A3A1A else 0x0FFFFFFF
            val x = r.left + rnd.nextFloat() * r.width()
            val y = r.top + rnd.nextFloat() * r.height()
            c.drawRect(x, y, x + 0.8f, y + 0.8f, p)
        }
        // a broad gloss from the room's light
        p.alpha = 255; p.shader = LinearGradient(r.left, r.top, r.left + r.width() * 0.6f, r.bottom, intArrayOf(0x1FFFFFFF, 0x00FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        c.restore()
        p.shader = null
        stroke(c, r, radius, 1.4f, 0xCCFFFFFF.toInt(), 0x66000000)
    }

    /** A rounded outline lit from above: `top` along the upper edge fading to `bottom` below. */
    private fun stroke(c: Canvas, r: RectF, radius: Float, width: Float, top: Int, bottom: Int) {
        val p = paint()
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        p.alpha = 255; p.shader = LinearGradient(0f, r.top, 0f, r.bottom, top, bottom, Shader.TileMode.CLAMP)
        val i = width / 2
        c.drawRoundRect(RectF(r.left + i, r.top + i, r.right - i, r.bottom - i), radius, radius, p)
    }

    private fun speaker(c: Canvas, r: RectF) {
        val p = paint()
        val radius = r.width() / 2
        p.color = 0xFF2A1C12.toInt()
        c.drawRoundRect(r, radius, radius, p)
        val clip = Path()
        clip.addRoundRect(r, radius, radius, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        // vertical cream slats over dark cloth
        var x = r.left + 4
        while (x < r.right - 3) {
            p.alpha = 255; p.shader = LinearGradient(x, 0f, x + 4.2f, 0f, intArrayOf(CREAM_HI, CREAM, CREAM_LO), null, Shader.TileMode.CLAMP)
            c.drawRect(x, r.top, x + 4.2f, r.bottom, p)
            x += 7.2f
        }
        p.alpha = 255; p.shader = LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(0x66000000, 0x00000000, 0x00000000, 0x59000000), floatArrayOf(0f, 0.12f, 0.85f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        c.restore()
        p.shader = null
        stroke(c, r, radius, 1.4f, 0x8C000000.toInt(), 0xB3FFFFFF.toInt())
    }

    private fun holidayProps(c: Canvas) {
        if (holiday == null) return
        when (holiday) {
            "halloween" -> {
                pumpkin(c, 80f, 404f, 16f)
                bat(c, 868f, 30f, 12f, 0xFF1A0E06.toInt())
                bat(c, 900f, 46f, 8f, 0xFF1A0E06.toInt())
            }
            "christmas" -> garland(c, 22f, 938f, 18f, 14f, true)
            "newyear" -> bunting(c, 22f, 938f, 16f, 18f, NEWYEAR)
            else -> bunting(c, 22f, 938f, 16f, 18f, JULY4)
        }
    }

    private fun texture(id: Int, scale: Float, rotate: Float): BitmapShader {
        val o = BitmapFactory.Options()
        o.inScaled = false
        val s = BitmapShader(BitmapFactory.decodeResource(home.resources, id, o), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val m = Matrix()
        m.setRotate(rotate)
        m.postScale(scale, scale)
        s.setLocalMatrix(m)
        return s
    }

    // ------------------------------------------------------------------ the controls

    /** A cream piano key with its channel number; pressed in and lit when chosen. */
    private fun key(c: Canvas, w: Float, f: Boolean, num: String, name: String) {
        val p = paint()
        val h = KEY_H
        val down = if (f) 2.5f else 0f
        // the key's front lip below its face (shrinks as it goes down)
        p.color = 0xFFB9A57E.toInt()
        c.drawRoundRect(1f, 4f + down, w - 1, h + 2, 4f, 4f, p)
        val top = RectF(1f, down, w - 1, h - 3 + down * 0.4f)
        p.alpha = 255; p.shader = LinearGradient(0f, top.top, 0f, top.bottom, if (f) intArrayOf(0xFFE6D6B2.toInt(), 0xFFD8C69E.toInt()) else intArrayOf(CREAM_HI, CREAM), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(top, 4f, 4f, p)
        p.shader = null
        stroke(c, top, 4f, 1f, 0xE6FFFFFF.toInt(), 0x33000000)
        // the lamp at the top of each key
        val lamp = RectF(w / 2 - 9, top.top + 4, w / 2 + 9, top.top + 7)
        p.color = if (f) AMBER else 0xFFB9A57E.toInt()
        c.drawRoundRect(lamp, 1.5f, 1.5f, p)
        if (f) glow(c, w / 2, lamp.centerY(), 14f, 0x99FFB347.toInt())
        if (num.isEmpty()) return
        val n = textPaint(13f, INK, chunky)
        centerText(c, num, w / 2, top.top + 21, n)
        val t = textPaint(6.8f, if (f) 0xFF8A4A16.toInt() else 0xFF6B4A33.toInt(), cond)
        t.letterSpacing = 0.06f
        centerText(c, fit(name.uppercase(Locale.getDefault()), t, w - 10), w / 2, top.top + 31, t)
    }

    /** A round black push button with its printed label. */
    private fun button(c: Canvas, w: Float, h: Float, f: Boolean, label: String) {
        val p = paint()
        val cx = 15f
        val cy = h / 2
        // chrome collar
        p.alpha = 255; p.shader = SweepGradient(cx, cy, intArrayOf(0xFFF2EFE8.toInt(), 0xFF8E897E.toInt(), 0xFFD6D1C6.toInt(), 0xFF77736A.toInt(), 0xFFF2EFE8.toInt()), null)
        c.drawCircle(cx, cy, 11f, p)
        p.shader = null
        if (f) glow(c, cx, cy, 20f, 0x80FFB347.toInt())
        // the cap, pressed in when chosen
        val d = if (f) 0.8f else 0f
        p.alpha = 255; p.shader = RadialGradient(cx - 2.5f, cy - 3f + d, 10f, intArrayOf(if (f) 0xFF5A3A20.toInt() else 0xFF4A4440.toInt(), 0xFF141210.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy + d, 8f, p)
        p.shader = null
        p.color = if (f) 0xCCFFB347.toInt() else 0x66FFFFFF
        c.drawCircle(cx - 2.6f, cy - 2.8f + d, 1.8f, p)
        val t = textPaint(10f, if (f) 0xFF8A4A16.toInt() else INK, cond)
        t.letterSpacing = 0.06f
        c.drawText(fit(label.uppercase(Locale.getDefault()), t, w - 34), 32f, cy + 3.6f, t)
    }

    /** The tuning dial's knob: spun aluminium with a grip bar, turned to the channel. */
    private fun knob(c: Canvas, f: Boolean) {
        val p = paint()
        val cx = DIAL_R + 8
        val cy = DIAL_R + 8
        if (f) glow(c, cx, cy, DIAL_R + 8, 0x66FFB347)
        val r = DIAL_R - 4
        // knurled skirt
        p.alpha = 255; p.shader = RadialGradient(cx - 8, cy - 10, r * 1.4f, intArrayOf(0xFF6A645A.toInt(), 0xFF2A2620.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        c.save()
        c.rotate(dialAngle, cx, cy)
        p.strokeWidth = 1f
        for (i in 0 until 60) {
            val a = i * Math.PI / 30
            p.color = if (i % 2 == 0) 0x4DFFFFFF else 0x73000000
            c.drawLine(cx + Math.cos(a).toFloat() * (r - 5), cy + Math.sin(a).toFloat() * (r - 5), cx + Math.cos(a).toFloat() * r, cy + Math.sin(a).toFloat() * r, p)
        }
        c.restore()
        // spun cap: the light sweeps around it and stays put while it turns
        val cap = r * 0.8f
        p.alpha = 255; p.shader = SweepGradient(cx, cy, intArrayOf(0xFFEDEAE2.toInt(), 0xFF8E897E.toInt(), 0xFFD9D4C8.toInt(), 0xFF7A766C.toInt(), 0xFFF4F1EA.toInt(), 0xFF8E897E.toInt(), 0xFFEDEAE2.toInt()), null)
        c.drawCircle(cx, cy, cap, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.color = 0x80000000.toInt()
        c.drawCircle(cx, cy, cap, p)
        p.style = Paint.Style.FILL
        // a raised grip bar with the pointer at its end
        c.save()
        c.rotate(dialAngle, cx, cy)
        p.alpha = 255; p.shader = LinearGradient(cx - 6, 0f, cx + 6, 0f, intArrayOf(0xFF3A3630.toInt(), 0xFF1A1816.toInt(), 0xFF3A3630.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(cx - 5.5f, cy - cap + 4, cx + 5.5f, cy + cap - 4, 5f, 5f, p)
        p.shader = null
        p.color = 0x40FFFFFF
        c.drawRoundRect(cx - 4f, cy - cap + 5, cx - 1.6f, cy + cap - 6, 2f, 2f, p)
        p.color = if (f) AMBER else 0xFFE9D8A8.toInt()
        c.drawCircle(cx, cy - cap + 9, 2.2f, p)
        c.restore()
    }

    // ------------------------------------------------------------------ the picture tube

    private inner class Tube : View(home) {
        private var art: AppArt? = null
        private var action = -1
        private var name = ""
        private var channel = 0
        private var tunedAt = 0L
        private var power = 1f
        private val p = paint()
        private val noise: Bitmap
        private var glass: Bitmap? = null

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            noise = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
            val rnd = Random(1)
            val px = IntArray(200 * 100)
            for (i in px.indices) { val v = 30 + rnd.nextInt(210); px[i] = 0xFF000000.toInt() or (v shl 16) or (v shl 8) or v }
            noise.setPixels(px, 0, 200, 0, 0, 200, 100)
        }

        fun show(ch: Int, a: AppArt?, act: Int, n: String) {
            val same = ch == channel && a === art && act == action
            channel = ch
            art = a
            action = act
            name = n
            if (!same) tunedAt = SystemClock.uptimeMillis()
            invalidate()
        }

        fun powerOn() {
            val a = ValueAnimator.ofFloat(0f, 1f)
            a.duration = 620
            a.interpolator = DecelerateInterpolator(1.6f)
            a.addUpdateListener { x -> power = x.animatedValue as Float; invalidate() }
            power = 0f
            a.start()
        }

        /** Scanlines, the tube's curvature and its reflections: painted once. */
        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            if (w <= 0) return
            glass?.recycle()
            val g = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            glass = g
            val c = Canvas(g)
            c.scale(k, k)
            val sw = w / k
            val sh = h / k
            val q = paint()
            q.color = 0x26000000
            var y = 0f
            while (y < sh) { c.drawRect(0f, y, sw, y + 0.9f, q); y += 2f }
            // the face is curved: dark toward the edges
            q.style = Paint.Style.STROKE
            q.strokeWidth = 34f
            q.color = 0xD9000000.toInt()
            q.maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
            c.drawRoundRect(RectF(-10f, -10f, sw + 10, sh + 10), TUBE_R + 10, TUBE_R + 10, q)
            q.maskFilter = null
            q.style = Paint.Style.FILL
            // reflections: a soft window up and to the left, a sheen across the top
            q.alpha = 255; q.shader = LinearGradient(0f, 0f, sw * 0.45f, sh * 0.6f, 0x29FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            val hl = Path()
            hl.addOval(RectF(-sw * 0.15f, -sh * 0.45f, sw * 0.6f, sh * 0.42f), Path.Direction.CW)
            c.drawPath(hl, q)
            q.shader = null
            q.color = 0x1AFFF4E0
            q.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
            c.drawRoundRect(RectF(sw * 0.07f, sh * 0.08f, sw * 0.2f, sh * 0.34f), 8f, 8f, q)
            q.maskFilter = null
            q.alpha = 255; q.shader = LinearGradient(0f, 6f, 0f, 20f, 0x24FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(sw * 0.12f, 5f, sw * 0.88f, 20f), 10f, 10f, q)
        }

        override fun onDraw(c: Canvas) {
            val tube = Path()
            tube.addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), px(TUBE_R), px(TUBE_R), Path.Direction.CW)
            c.save()
            c.clipPath(tube)
            c.drawColor(0xFF0C0E0C.toInt())
            c.save()
            c.scale(k, k)
            val again = picture(c, TUBE.width(), TUBE.height())
            c.restore()
            glass?.let { c.drawBitmap(it, 0f, 0f, null) }
            c.restore()
            if (again) postInvalidateDelayed(40)
        }

        /** Returns true while it's still animating (static between channels). */
        private fun picture(c: Canvas, w: Float, h: Float): Boolean {
            val age = SystemClock.uptimeMillis() - tunedAt
            val tuning = tunedAt > 0 && age < 170
            val r = RectF(0f, 0f, w, h)
            if (tuning) {
                p.isFilterBitmap = false
                c.drawBitmap(noise, null, RectF(-((age * 37) % 40).toFloat(), -((age * 13) % 30).toFloat(), w + 40, h + 30), p)
                p.isFilterBitmap = true
            } else {
                val art = art
                if (art != null) {
                    art.fill(c, r)
                    p.color = 0x1AFF9A40
                    c.drawRect(r, p)
                } else {
                    bars(c, w, h, action == ALL)
                    if (action != ALL) {
                        p.color = 0xB3000000.toInt()
                        c.drawRect(r, p)
                        icon(c, action, w / 2, h / 2 - 22, 70f, 0xFFF6EBD0.toInt(), 3.6f)
                    }
                }
                // on-screen display: time, channel, what's on
                val o = textPaint(24f, OSD, osd)
                o.setShadowLayer(2f, 1.5f, 1.5f, 0xE6000000.toInt())
                c.drawText(time(hhmm), 40f, 46f, o)
                o.textAlign = Paint.Align.RIGHT
                if (channel > 0) c.drawText("CH " + String.format(Locale.US, "%02d", channel), w - 40, 46f, o)
                if (theme.greeting != null) {
                    val g = textPaint(13f, OSD, osd)
                    g.setShadowLayer(2f, 1.5f, 1.5f, 0xE6000000.toInt())
                    c.drawText(theme.greeting!!.uppercase(Locale.getDefault()), 40f, 66f, g)
                }
                val nm = textPaint(if (art == null) 22f else 17f, 0xFFF6EBD0.toInt(), osd)
                nm.setShadowLayer(2f, 1.5f, 1.5f, 0xE6000000.toInt())
                if (art == null) {
                    val tw = Math.min(w - 60, nm.measureText(name) + 30)
                    if (action == ALL) { p.color = 0xD9140A04.toInt(); c.drawRect(w / 2 - tw / 2, h / 2 - 22, w / 2 + tw / 2, h / 2 + 14, p) }
                    centerText(c, fit(name, nm, tw - 16), w / 2, if (action == ALL) h / 2 + 3 else h / 2 + 52, nm)
                } else c.drawText(fit(name.uppercase(Locale.getDefault()), nm, w - 80), 40f, h - 30, nm)
            }
            // warming up: a bright line that opens into the picture
            if (power < 1f) {
                val open = h / 2 * power
                p.color = 0xFF000000.toInt()
                c.drawRect(0f, 0f, w, h / 2 - open, p)
                c.drawRect(0f, h / 2 + open, w, h, p)
                p.color = alpha(0xFFFFF4DC.toInt(), 1 - power)
                c.drawRect(0f, h / 2 - open - 1.5f, w, h / 2 + open + 1.5f, p)
            }
            return tuning
        }

        private fun bars(c: Canvas, w: Float, h: Float, bright: Boolean) {
            val cs = intArrayOf(0xFFC8C8C0.toInt(), 0xFFC8C060.toInt(), 0xFF60B8B8.toInt(), 0xFF60B060.toInt(), 0xFFA86098.toInt(), 0xFFB05050.toInt(), 0xFF5058A0.toInt())
            val bw = w / cs.size
            for (i in cs.indices) {
                p.color = if (bright) cs[i] else Tile.darken(cs[i], 0.25f)
                c.drawRect(i * bw, 0f, (i + 1) * bw, h * 0.7f, p)
            }
            p.color = 0xFF14141C.toInt()
            c.drawRect(0f, h * 0.7f, w, h, p)
            for (i in 0 until 4) {
                p.color = if (i % 2 == 0) 0xFFE8E8E0.toInt() else 0xFF14141C.toInt()
                c.drawRect(i * w / 8, h * 0.76f, (i + 1) * w / 8, h * 0.94f, p)
            }
        }
    }
}
