package home.aurora

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import java.util.Random

/**
 * Live weather over the wallpaper, drawn to look like the real thing through a window:
 *  - rain: three depth layers of tapered streaks with gusting wind, a blue-grey gloom,
 *    and water beads on the "glass" that sometimes slide down leaving a trail
 *  - drizzle: fine mist streaks, a few beads, light haze
 *  - snow: three depth layers, far flakes sharp and tiny, near flakes big and out of focus,
 *    each swaying at its own pace
 *  - thunderstorm: heavy rain plus lightning (jagged branching bolt, flickering flash)
 *  - fog: soft banks drifting slowly across
 * Also hosts the one-shot wake-up shimmer.
 */
open class WeatherFx(c: Context) : View(c) {

    private val rnd = Random()
    private val d: Float
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmp = Paint(Paint.FILTER_BITMAP_FLAG)
    private val veil = Paint()
    private val flash = Paint()
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shine = Paint()
    private val r = RectF()
    private var flakeSoft: Bitmap? = null
    private var bead: Bitmap? = null
    private var fogBank: Bitmap? = null
    private var mode = NONE
    private var running = false
    private var last = 0L
    private var shimmerAt = -1L
    private var time = 0f

    // falling particles (rain drops / snowflakes)
    private var px = FloatArray(0); private var py = FloatArray(0); private var pz = FloatArray(0)
    private var pv = FloatArray(0); private var pl = FloatArray(0); private var ph = FloatArray(0)
    // beads on the glass
    private var bx = FloatArray(0); private var by = FloatArray(0); private var bs = FloatArray(0)
    private var bvy = FloatArray(0); private var bage = FloatArray(0)
    private val trail = Array(60) { floatArrayOf(0f, 0f, 0f, TRAIL_LIFE) }   // recent positions of sliding beads: x, y, size, age
    private var trailHead = 0
    // fog banks
    private var fx = FloatArray(0); private var fy = FloatArray(0); private var fw = FloatArray(0); private var fv = FloatArray(0)
    // lightning
    private var nextStrike = 0L
    private var strikeAt = -1L
    private val boltPaths: MutableList<Path> = java.util.ArrayList()
    private val boltWidths: MutableList<Float> = java.util.ArrayList()

    init {
        d = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics)
        line.strokeCap = Paint.Cap.ROUND
        bolt.style = Paint.Style.STROKE
        bolt.strokeCap = Paint.Cap.ROUND
        bolt.strokeJoin = Paint.Join.ROUND
    }

    /** Open-Meteo / WMO weather code. */
    fun setWeatherCode(code: Int) {
        val m = if (code >= 95) STORM
                else if ((code >= 71 && code <= 77) || code == 85 || code == 86) SNOW
                else if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) RAIN
                else if (code >= 51 && code <= 57) DRIZZLE
                else if (code == 45 || code == 48) FOG else NONE
        if (m == mode) return
        mode = m
        build()
        invalidate()
    }

    fun start() { running = true; last = SystemClock.uptimeMillis(); invalidate() }
    fun stop() { running = false }

    /** Soft band of light across the screen, used when the TV wakes. */
    fun shimmer() {
        shimmerAt = SystemClock.uptimeMillis()
        invalidate()
    }

    private fun W(): Int = Math.max(width, 1920)
    private fun H(): Int = Math.max(height, 1080)

    // ------------------------------------------------------------------ setup

    private fun build() {
        var far = 0
        var mid = 0
        var near = 0
        var beads = 0
        var banks = 0
        when (mode) {
            DRIZZLE -> { far = 170; mid = 60; beads = 14 }
            RAIN -> { far = 170; mid = 95; near = 26; beads = 34 }
            STORM -> { far = 230; mid = 130; near = 40; beads = 46 }
            SNOW -> { far = 150; mid = 70; near = 16 }
            FOG -> { banks = 7 }
        }
        val n = far + mid + near
        px = FloatArray(n); py = FloatArray(n); pz = FloatArray(n); pv = FloatArray(n); pl = FloatArray(n); ph = FloatArray(n)
        for (i in 0 until n) {
            pz[i] = if (i < far) 0.35f else if (i < far + mid) 0.65f else 1f
            seed(i, true)
        }
        bx = FloatArray(beads); by = FloatArray(beads); bs = FloatArray(beads); bvy = FloatArray(beads); bage = FloatArray(beads)
        for (i in 0 until beads) seedBead(i, true)
        fx = FloatArray(banks); fy = FloatArray(banks); fw = FloatArray(banks); fv = FloatArray(banks)
        for (i in 0 until banks) {
            fw[i] = (500 + rnd.nextFloat() * 500) * d
            fx[i] = rnd.nextFloat() * W() - fw[i] / 2
            fy[i] = H() * (0.15f + rnd.nextFloat() * 0.75f)
            fv[i] = (6 + rnd.nextFloat() * 10) * d * (if (rnd.nextBoolean()) 1 else -1)
        }
        for (t in trail) t[3] = TRAIL_LIFE
        shaders()
        if (mode == SNOW && flakeSoft == null) flakeSoft = softDot(64)
        if ((mode == RAIN || mode == STORM || mode == DRIZZLE) && bead == null) bead = makeBead(96)
        if (mode == FOG && fogBank == null) fogBank = makeFog(256, 128)
        nextStrike = SystemClock.uptimeMillis() + 4000 + rnd.nextInt(5000)
        strikeAt = -1
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { shaders() }

    /** The full-screen gradients depend only on the mode and height: made here, not every frame. */
    private fun shaders() {
        val h = height.toFloat()
        if (h <= 0) return
        veil.shader = when (mode) {
            SNOW -> LinearGradient(0f, 0f, 0f, h, 0x1AC8D6E6, 0x00C8D6E6, Shader.TileMode.CLAMP)
            STORM -> LinearGradient(0f, 0f, 0f, h, 0x6614202E, 0x26101820, Shader.TileMode.CLAMP)
            RAIN -> LinearGradient(0f, 0f, 0f, h, 0x4A1A2633, 0x14101820, Shader.TileMode.CLAMP)
            DRIZZLE -> LinearGradient(0f, 0f, 0f, h, 0x2A202A36, 0x14101820, Shader.TileMode.CLAMP)
            else -> null
        }
        // the lightning flash at full strength; each frame only turns its alpha down
        flash.shader = LinearGradient(0f, 0f, 0f, h, (110 shl 24) or 0xDDE6FF, (35 shl 24) or 0xDDE6FF, Shader.TileMode.CLAMP)
    }

    private fun seed(i: Int, anywhere: Boolean) {
        val z = pz[i]
        px[i] = rnd.nextFloat() * W() * 1.3f - W() * 0.15f
        py[i] = if (anywhere) rnd.nextFloat() * H() else -rnd.nextFloat() * H() * 0.3f - 20 * d
        ph[i] = rnd.nextFloat() * 6.283f
        if (mode == SNOW) {
            pv[i] = (16 + 70 * z * z + rnd.nextFloat() * 18) * d            // near flakes fall faster
            pl[i] = if (z < 0.5f) (0.8f + rnd.nextFloat() * 0.8f) * d
                    else if (z < 0.9f) (1.8f + rnd.nextFloat() * 1.4f) * d
                    else (6f + rnd.nextFloat() * 7f) * d                        // near: big bokeh flakes
        } else {
            val base = if (mode == DRIZZLE) 420f else 760f
            pv[i] = (base * (0.55f + 0.75f * z) + rnd.nextFloat() * 160) * d
            pl[i] = (if (mode == DRIZZLE) 6 else 10) * d * (0.6f + 2.6f * z * z) * (0.8f + rnd.nextFloat() * 0.5f)
        }
    }

    private fun seedBead(i: Int, anywhere: Boolean) {
        bx[i] = rnd.nextFloat() * W()
        by[i] = if (anywhere) rnd.nextFloat() * H() else rnd.nextFloat() * H() * 0.7f
        bs[i] = (2.5f + Math.pow(rnd.nextFloat().toDouble(), 2.2).toFloat() * 9f) * d   // mostly small, a few big
        bvy[i] = 0f
        bage[i] = if (anywhere) 1f else 0f                                       // new beads fade in
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = Math.min(0.1f, (now - last) / 1000f)
        last = now
        time += dt
        val w = width
        val h = height

        // gusting wind: slow, layered swings (px per second at full depth)
        val gust = (Math.sin(time * 0.21) * 0.6 + Math.sin(time * 0.57 + 1.3) * 0.3 + Math.sin(time * 1.31) * 0.1).toFloat()
        val wind = (if (mode == STORM) 0.34f else if (mode == SNOW) 0.12f else 0.16f) + gust * (if (mode == STORM) 0.14f else 0.07f)

        if (mode == RAIN || mode == STORM || mode == DRIZZLE) {
            drawGloom(c, w, h)
            drawRain(c, h, dt, wind)
            if (mode == STORM) drawLightning(c, now, w, h)
            drawBeads(c, h, dt)
        } else if (mode == SNOW) {
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), veil)
            drawSnow(c, h, dt, wind)
        } else if (mode == FOG) {
            drawFog(c, w, dt)
        }

        val shimmering = drawShimmer(c, now, w, h)
        if (shimmering) postInvalidateOnAnimation()
        else if (running && mode != NONE) postInvalidateDelayed(if (WallpaperView.idle()) IDLE_FRAME_MS else FRAME_MS)   // nobody at the remote: fewer frames
    }

    private fun drawGloom(c: Canvas, w: Int, h: Int) {
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), veil)
    }

    private fun drawRain(c: Canvas, h: Int, dt: Float, wind: Float) {
        for (i in px.indices) {
            val z = pz[i]
            val v = pv[i]
            val len = pl[i]
            val vx = v * wind
            py[i] += v * dt
            px[i] += vx * dt
            if (py[i] - len > h) seed(i, false)
            val dx = -len * wind
            val dy = -len                        // streak points back along the fall
            val hx = px[i]
            val hy = py[i]
            val a = ((if (mode == DRIZZLE) 38 else 58) * (0.45f + 0.55f * z) * (if (z >= 1f) 0.7f else 1f)).toInt()
            line.strokeWidth = Math.max(0.6f * d, (if (z >= 1f) 2.2f else if (z > 0.5f) 1.1f else 0.7f) * d)
            // tapered streak: faint tail, brighter head
            line.color = (a / 3) shl 24 or 0xD6E2F0
            c.drawLine(hx + dx, hy + dy, hx + dx * 0.45f, hy + dy * 0.45f, line)
            line.color = a shl 24 or 0xE4EDF7
            c.drawLine(hx + dx * 0.45f, hy + dy * 0.45f, hx, hy, line)
        }
    }

    private fun drawBeads(c: Canvas, h: Int, dt: Float) {
        val bead = bead!!
        // trails left by sliding beads fade out
        for (t in trail) {
            if (t[3] >= TRAIL_LIFE) continue
            t[3] += dt
            val life = 1f - t[3] / TRAIL_LIFE
            if (life <= 0) continue
            bmp.alpha = (110 * life).toInt()
            val s = t[2] * 0.42f
            r.set(t[0] - s, t[1] - s, t[0] + s, t[1] + s)
            c.drawBitmap(bead, null, r, bmp)
        }
        for (i in bx.indices) {
            if (bage[i] < 1f) bage[i] = Math.min(1f, bage[i] + dt * 0.5f)
            // big beads sometimes break loose and slide
            if (bvy[i] == 0f && bs[i] > 6 * d && rnd.nextFloat() < dt * 0.06f) bvy[i] = 20 * d
            if (bvy[i] > 0) {
                bvy[i] = Math.min(bvy[i] + 240 * d * dt, 260 * d)
                by[i] += bvy[i] * dt
                bx[i] += Math.sin((time * 9 + i).toDouble()).toFloat() * 6 * d * dt     // wobble on the way down
                if (rnd.nextFloat() < 0.6f) addTrail(bx[i], by[i] - bs[i] * 0.6f, bs[i])
                if (rnd.nextFloat() < dt * 0.8f) bvy[i] = 0f              // sometimes it catches and stops
                if (by[i] - bs[i] > h) seedBead(i, false)
            }
            bmp.alpha = (235 * bage[i]).toInt()
            val s = bs[i]
            r.set(bx[i] - s, by[i] - s * (if (bvy[i] > 0) 1.25f else 1f), bx[i] + s, by[i] + s)   // stretches while sliding
            c.drawBitmap(bead, null, r, bmp)
        }
    }

    private fun addTrail(x: Float, y: Float, s: Float) {
        val t = trail[trailHead]   // reused: no garbage per bead per frame
        t[0] = x; t[1] = y; t[2] = s; t[3] = 0f
        trailHead = (trailHead + 1) % trail.size
    }

    private fun drawSnow(c: Canvas, h: Int, dt: Float, wind: Float) {
        for (i in px.indices) {
            val z = pz[i]
            py[i] += pv[i] * dt
            val sway = Math.sin(time * (0.5 + (i % 7) * 0.12) + ph[i]).toFloat() * (14 + 26 * z) * d
            px[i] += (pv[i] * wind * 3 + sway) * dt
            if (py[i] - pl[i] > h) seed(i, false)
            val s = pl[i]
            val a = if (z >= 1f) 70 else if (z > 0.5f) 200 else 150                 // near flakes are out of focus
            bmp.alpha = a
            val soft = if (z >= 1f) 1.6f else 1.15f
            r.set(px[i] - s * soft, py[i] - s * soft, px[i] + s * soft, py[i] + s * soft)
            c.drawBitmap(flakeSoft!!, null, r, bmp)
        }
    }

    private fun drawFog(c: Canvas, w: Int, dt: Float) {
        for (i in fx.indices) {
            fx[i] += fv[i] * dt
            if (fx[i] > w + fw[i] * 0.5f) fx[i] = -fw[i] * 1.5f
            if (fx[i] < -fw[i] * 1.5f) fx[i] = w + fw[i] * 0.5f
            val breathe = 0.75f + 0.25f * Math.sin(time * 0.15 + i).toFloat()
            bmp.alpha = (95 * breathe).toInt()
            r.set(fx[i], fy[i] - fw[i] * 0.22f, fx[i] + fw[i], fy[i] + fw[i] * 0.22f)
            c.drawBitmap(fogBank!!, null, r, bmp)
        }
    }

    private fun drawLightning(c: Canvas, now: Long, w: Int, h: Int) {
        if (strikeAt < 0 && now > nextStrike) {
            strikeAt = now
            makeBolt(w * (0.15f + rnd.nextFloat() * 0.7f), h * (0.45f + rnd.nextFloat() * 0.3f))
            nextStrike = now + 7000 + rnd.nextInt(11000)
        }
        if (strikeAt < 0) return
        val t = now - strikeAt
        // flicker: bright, dim, bright again, then fade
        val k = if (t < 70) 1f else if (t < 140) 0.25f else if (t < 230) 0.85f else if (t < 700) 0.85f * (1 - (t - 230) / 470f) else 0f
        if (t > 700) { strikeAt = -1; return }
        flash.alpha = (255 * k).toInt()
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), flash)
        if (t < 230) {
            // three passes: wide blue haze, glow, white-hot core; branches thinner and fainter
            for (i in boltPaths.indices) {
                val p = boltPaths[i]
                val wf = boltWidths[i]
                bolt.strokeWidth = 22 * d * wf
                bolt.color = ((26 * k * wf).toInt() shl 24) or 0x9FB4FF
                c.drawPath(p, bolt)
                bolt.strokeWidth = 6 * d * wf
                bolt.color = ((90 * k * wf).toInt() shl 24) or 0xC9D6FF
                c.drawPath(p, bolt)
                bolt.strokeWidth = Math.max(0.8f, 1.8f * wf) * d
                bolt.color = ((255 * k * Math.min(1f, 0.4f + wf)).toInt() shl 24) or 0xFFFFFF
                c.drawPath(p, bolt)
            }
        }
        postInvalidateOnAnimation()
    }

    /**
     * Fractal lightning: midpoint displacement gives hundreds of small kinks like a real
     * channel, and forks split off along the way, each thinner and shorter.
     */
    private fun makeBolt(x: Float, endY: Float) {
        boltPaths.clear()
        boltWidths.clear()
        val x1 = x + (rnd.nextFloat() - 0.5f) * 260 * d
        channel(x, -10 * d, x1, endY, 1f, 7, 0)
    }

    private fun channel(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, detail: Int, depth: Int) {
        var pts: MutableList<FloatArray> = java.util.ArrayList()
        pts.add(floatArrayOf(x0, y0))
        pts.add(floatArrayOf(x1, y1))
        var spread = Math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat() * 0.22f
        for (pass in 0 until detail) {
            val next: MutableList<FloatArray> = java.util.ArrayList()
            for (i in 0 until pts.size - 1) {
                val a = pts[i]
                val b = pts[i + 1]
                next.add(a)
                next.add(floatArrayOf((a[0] + b[0]) / 2 + (rnd.nextFloat() - 0.5f) * spread, (a[1] + b[1]) / 2 + (rnd.nextFloat() - 0.5f) * spread * 0.3f))
            }
            next.add(pts[pts.size - 1])
            pts = next
            spread *= 0.52f
        }
        val p = Path()
        p.moveTo(pts[0][0], pts[0][1])
        for (i in 1 until pts.size) p.lineTo(pts[i][0], pts[i][1])
        boltPaths.add(p)
        boltWidths.add(width)
        if (depth >= 2) return
        // forks
        val forks = if (depth == 0) 3 + rnd.nextInt(3) else rnd.nextInt(2)
        for (f in 0 until forks) {
            val at = pts[(pts.size * (0.15f + rnd.nextFloat() * 0.65f)).toInt()]
            val len = Math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat() * (0.18f + rnd.nextFloat() * 0.25f) / (depth + 1)
            val ang = Math.toRadians((90 + (if (rnd.nextBoolean()) 1 else -1) * (20 + rnd.nextFloat() * 40)).toDouble())
            channel(at[0], at[1], at[0] + Math.cos(ang).toFloat() * len, at[1] + Math.sin(ang).toFloat() * len,
                    width * 0.45f, detail - 2, depth + 1)
        }
    }

    private fun drawShimmer(c: Canvas, now: Long, w: Int, h: Int): Boolean {
        if (shimmerAt < 0) return false
        val t = (now - shimmerAt) / SHIMMER_MS.toFloat()
        if (t >= 1f) { shimmerAt = -1; return false }
        val e = 1 - (1 - t) * (1 - t)
        val band = w * 0.28f
        val cx = -band + e * (w + band * 2)
        val fade = if (t < 0.15f) t / 0.15f else if (t > 0.8f) (1 - t) / 0.2f else 1f
        val peak = (0x52 * fade).toInt() shl 24 or 0xFFFFFF
        shine.shader = LinearGradient(cx - band, 0f, cx + band, h * 0.25f,
                intArrayOf(0x00FFFFFF, peak, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shine)
        return true
    }

    companion object {
        private const val NONE = 0
        private const val DRIZZLE = 1
        private const val RAIN = 2
        private const val SNOW = 3
        private const val STORM = 4
        private const val FOG = 5
        private const val FRAME_MS = 33L
        private const val IDLE_FRAME_MS = 66L
        private const val TRAIL_LIFE = 4f   // seconds
        private const val SHIMMER_MS = 1500L

        // ------------------------------------------------------------------ sprites

        /** Soft round flake: bright core falling off to nothing. */
        private fun softDot(n: Int): Bitmap {
            val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(n / 2f, n / 2f, n / 2f,
                    intArrayOf(0xFFFFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0x33FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.35f, 0.7f, 1f),
                    Shader.TileMode.CLAMP)
            Canvas(b).drawCircle(n / 2f, n / 2f, n / 2f, p)
            return b
        }

        /** Water bead on glass: clear body, darker refracted rim, a sharp highlight and a soft lower glint. */
        private fun makeBead(n: Int): Bitmap {
            val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val cx = n / 2f
            val cy = n / 2f
            val r = n * 0.42f
            p.shader = RadialGradient(cx + r * 0.08f, cy + r * 0.25f, r * 1.15f,
                    intArrayOf(0x00000000, 0x30000000, 0x00000000), floatArrayOf(0.55f, 0.85f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(cx + r * 0.08f, cy + r * 0.2f, r * 1.12f, p)                 // contact shadow
            p.shader = RadialGradient(cx, cy, r,
                    intArrayOf(0x14E8F0FF, 0x1AE8F0FF, 0x5A0A1018, 0x8C060A10.toInt()), floatArrayOf(0f, 0.55f, 0.88f, 1f),
                    Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)                                                   // lens body + rim
            p.shader = RadialGradient(cx, cy + r * 0.45f, r * 0.55f,
                    intArrayOf(0x66FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy + r * 0.45f, r * 0.55f, p)                               // light focused at the bottom
            p.shader = null
            p.color = 0xE6FFFFFF.toInt()
            c.drawOval(RectF(cx - r * 0.5f, cy - r * 0.62f, cx - r * 0.12f, cy - r * 0.32f), p)   // highlight
            return b
        }

        /** Wide, very soft fog bank. */
        private fun makeFog(w: Int, h: Int): Bitmap {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val r = Random(5)
            for (i in 0 until 9) {
                val x = w * (0.15f + r.nextFloat() * 0.7f)
                val y = h * (0.35f + r.nextFloat() * 0.3f)
                val rad = h * (0.25f + r.nextFloat() * 0.3f)
                p.shader = RadialGradient(x, y, rad, intArrayOf(0x55E6EBF0, 0x00E6EBF0), null, Shader.TileMode.CLAMP)
                c.drawCircle(x, y, rad, p)
            }
            return b
        }
    }
}
