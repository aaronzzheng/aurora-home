package home.aurora

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import java.util.ArrayList
import java.util.Random

/**
 * Gentle things drifting over a theme's background: stars, falling snow, cherry
 * petals, autumn leaves, warm dust in lamp light, bubbles, Halloween bats, and
 * fireworks. Also an optional soft color glow from the top-right corner.
 */
open class ThemeFx(c: Context, private val kind: Int, private var wash: Int) : View(c) {

    private val u: Float
    private val rnd = Random()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val washPaint = Paint()
    private val firePaint = Paint()
    private val path = Path()
    private lateinit var x: FloatArray
    private lateinit var y: FloatArray
    private lateinit var vx: FloatArray
    private lateinit var vy: FloatArray
    private lateinit var rot: FloatArray
    private lateinit var vr: FloatArray
    private lateinit var size: FloatArray
    private lateinit var ph: FloatArray
    private lateinit var color: IntArray
    private var n = 0
    private var last = 0L
    private var running = false
    private var burstColors = intArrayOf(0xFFFFD45E.toInt(), 0xFFFFF1C2.toInt(), 0xFFFFB347.toInt())

    // fireworks
    private class Burst {
        var x = 0f; var y = 0f; var rise = 0f; var born = 0L; var color = 0
        lateinit var ang: FloatArray; lateinit var spd: FloatArray
    }
    private val bursts: MutableList<Burst> = ArrayList()
    private var nextBurst = 0L
    // stars: an occasional shooting star
    private var shootAt = 0L
    private var shootX = 0f
    private var shootY = 0f
    private var streak: android.graphics.LinearGradient? = null   // made once, moved with a matrix
    private val streakAt = android.graphics.Matrix()

    init {
        u = c.resources.displayMetrics.density
        setWillNotDraw(false)
    }

    fun setFireworkColors(vararg c: Int) { burstColors = c }


    fun start() { running = true; last = SystemClock.uptimeMillis(); invalidate() }
    fun stop() { running = false }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (wash != 0) washPaint.shader = RadialGradient(w.toFloat(), 0f, w * 0.75f, wash, wash and 0xFFFFFF, Shader.TileMode.CLAMP)
        when (kind) {
            STARS -> n = 140
            SNOW -> n = 70
            PETALS, LEAVES -> n = 22
            MOTES -> n = 34
            EMBERS -> {
                n = 40
                firePaint.shader = android.graphics.LinearGradient(0f, h.toFloat(), 0f, h * 0.45f,
                        intArrayOf(0xFFFF5A12.toInt(), 0x88FF7A22.toInt(), 0x00FF8A2E), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            }
            CONFETTI -> n = 50
            BATS -> n = 7
            BUBBLES -> n = 16
            else -> n = 0
        }
        x = FloatArray(n); y = FloatArray(n); vx = FloatArray(n); vy = FloatArray(n)
        rot = FloatArray(n); vr = FloatArray(n); size = FloatArray(n); ph = FloatArray(n); color = IntArray(n)
        for (i in 0 until n) seed(i, true)
    }

    private fun seed(i: Int, anywhere: Boolean) {
        val w = width
        val h = height
        x[i] = rnd.nextFloat() * w
        y[i] = if (anywhere) rnd.nextFloat() * h else -20 * u
        rot[i] = rnd.nextFloat() * 360
        vr[i] = (rnd.nextFloat() - 0.5f) * 90
        ph[i] = rnd.nextFloat() * 6.28f
        when (kind) {
            STARS -> {
                size[i] = (0.4f + rnd.nextFloat() * rnd.nextFloat() * 1.4f) * u
                vx[i] = -(2 + size[i] / u * 4) * u   // nearer stars drift faster
                vy[i] = 0f
                if (!anywhere) { x[i] = (w + 5).toFloat(); y[i] = rnd.nextFloat() * h }
            }
            SNOW -> {
                size[i] = (1.2f + rnd.nextFloat() * 2.8f) * u
                vy[i] = (18 + size[i] / u * 9) * u
                vx[i] = 4 * u
            }
            PETALS -> {
                size[i] = (5 + rnd.nextFloat() * 4) * u
                vy[i] = (22 + rnd.nextFloat() * 18) * u
                vx[i] = (14 + rnd.nextFloat() * 14) * u
                color[i] = PETAL[rnd.nextInt(PETAL.size)]
                if (!anywhere) x[i] = rnd.nextFloat() * w * 1.2f - w * 0.3f
            }
            LEAVES -> {
                size[i] = (9 + rnd.nextFloat() * 7) * u
                vy[i] = (26 + rnd.nextFloat() * 20) * u
                vx[i] = (6 + rnd.nextFloat() * 10) * u
                color[i] = LEAF[rnd.nextInt(LEAF.size)]
            }
            EMBERS -> {
                size[i] = (1 + rnd.nextFloat() * 1.8f) * u
                vx[i] = (rnd.nextFloat() - 0.5f) * 14 * u
                vy[i] = -(30 + rnd.nextFloat() * 50) * u
                if (!anywhere) y[i] = (h + 5).toFloat()
                else y[i] = h * (0.5f + rnd.nextFloat() * 0.5f)
            }
            MOTES -> {
                size[i] = (1 + rnd.nextFloat() * 2.4f) * u
                vx[i] = (rnd.nextFloat() - 0.5f) * 8 * u
                vy[i] = -(2 + rnd.nextFloat() * 6) * u
                if (!anywhere) y[i] = (h + 10).toFloat()
            }
            CONFETTI -> {
                size[i] = (5 + rnd.nextFloat() * 4) * u
                vy[i] = (40 + rnd.nextFloat() * 30) * u
                vx[i] = (rnd.nextFloat() - 0.5f) * 20 * u
                vr[i] = (rnd.nextFloat() - 0.5f) * 360
                color[i] = CONFETTI_C[rnd.nextInt(CONFETTI_C.size)]
            }
            BATS -> {
                size[i] = (10 + rnd.nextFloat() * 12) * u
                val right = rnd.nextBoolean()
                vx[i] = (if (right) 1 else -1) * (40 + rnd.nextFloat() * 40) * u
                vy[i] = 0f
                x[i] = if (anywhere) rnd.nextFloat() * w else if (right) -40 * u else w + 40 * u
                y[i] = h * (0.08f + rnd.nextFloat() * 0.45f)
            }
            BUBBLES -> {
                size[i] = (8 + rnd.nextFloat() * 22) * u
                vy[i] = -(14 + rnd.nextFloat() * 18) * u
                vx[i] = 0f
                color[i] = CONFETTI_C[rnd.nextInt(CONFETTI_C.size)]
                if (!anywhere) y[i] = h + size[i]
            }
        }
    }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = Math.min(0.1f, (now - last) / 1000f)
        last = now
        val w = width
        val h = height
        if (wash != 0) c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), washPaint)
        val t = now / 1000f
        if (kind == EMBERS) {   // the fire below the screen: a warm glow that flickers
            val f = 0.75f + 0.12f * Math.sin((t * 7.1f).toDouble()).toFloat() + 0.08f * Math.sin((t * 13.7f + 1).toDouble()).toFloat() + 0.05f * Math.sin((t * 23.3f).toDouble()).toFloat()
            firePaint.alpha = (255 * Math.min(1f, f)).toInt()
            c.drawRect(0f, h * 0.45f, w.toFloat(), h.toFloat(), firePaint)
        }
        for (i in 0 until n) {
            val sway = Math.sin((t * 1.3f + ph[i]).toDouble()).toFloat()
            x[i] += (vx[i] + (if (kind == LEAVES || kind == PETALS || kind == SNOW || kind == BUBBLES) sway * 18 * u else 0f)) * dt
            y[i] += vy[i] * dt
            rot[i] += vr[i] * dt
            if (kind == EMBERS && y[i] < h * (0.25f + 0.3f * ph[i] / 6.28f)) seed(i, false)   // burnt out
            if (y[i] > h + 30 * u || x[i] < -60 * u || x[i] > w + 60 * u || y[i] < -60 * u) seed(i, false)
            drawOne(c, i, t)
        }
        if (kind == STARS) shootingStar(c, now, w, h)
        if (kind == FIREWORKS) fireworks(c, now, w, h)
        // nobody at the remote for a few minutes: fewer frames
        if (running) postInvalidateDelayed(if (n == 0 || WallpaperView.idle()) 66 else 33)
    }

    private fun drawOne(c: Canvas, i: Int, t: Float) {
        val s = size[i]
        when (kind) {
            STARS -> {
                val tw = 0.55f + 0.45f * Math.sin((t * (1 + ph[i]) + ph[i] * 7).toDouble()).toFloat()
                p.color = Color.alpha(0xFFFFFFFF.toInt(), tw)
                c.drawCircle(x[i], y[i], s, p)
            }
            SNOW -> {
                p.color = 0xCCFFFFFF.toInt()
                c.drawCircle(x[i], y[i], s, p)
            }
            PETALS -> {
                c.save()
                c.translate(x[i], y[i])
                c.rotate(rot[i])
                c.scale(1f, 0.55f + 0.45f * Math.abs(Math.sin((t * 2 + ph[i]).toDouble())).toFloat())   // tumbling
                path.reset()
                path.moveTo(0f, -s)
                path.cubicTo(s, -s, s * 0.8f, s * 0.6f, 0f, s)
                path.cubicTo(-s * 0.8f, s * 0.6f, -s, -s, 0f, -s)
                p.color = Color.alpha(color[i], 0.9f)
                c.drawPath(path, p)
                c.restore()
            }
            LEAVES -> {
                c.save()
                c.translate(x[i], y[i])
                c.rotate(rot[i] + Math.sin((t * 1.3f + ph[i]).toDouble()).toFloat() * 35)
                path.reset()
                path.moveTo(0f, -s)
                path.quadTo(s * 0.75f, -s * 0.2f, 0f, s)
                path.quadTo(-s * 0.75f, -s * 0.2f, 0f, -s)
                p.color = color[i]
                c.drawPath(path, p)
                p.color = 0x66000000
                p.strokeWidth = u
                c.drawLine(0f, -s * 0.8f, 0f, s * 1.25f, p)
                c.restore()
            }
            EMBERS -> {
                val life = Math.max(0f, Math.min(1f, (y[i] - height * 0.25f) / (height * 0.6f)))
                val fl = 0.6f + 0.4f * Math.sin((t * 20 + ph[i] * 5).toDouble()).toFloat()
                p.color = Color.alpha(0xFFFF7A1A.toInt(), life * fl * 0.35f)
                c.drawCircle(x[i] + sway(t, i), y[i], s * 3.2f, p)
                p.color = Color.alpha(if (life > 0.5f) 0xFFFFD27A.toInt() else 0xFFFF8A2E.toInt(), life * fl)
                c.drawCircle(x[i] + sway(t, i), y[i], s, p)
            }
            MOTES -> {
                val glow = 0.35f + 0.35f * Math.sin((t * 0.8f + ph[i]).toDouble()).toFloat()
                p.color = Color.alpha(0xFFFFD08A.toInt(), glow * 0.35f)
                c.drawCircle(x[i], y[i], s * 3, p)
                p.color = Color.alpha(0xFFFFE2B0.toInt(), glow + 0.2f)
                c.drawCircle(x[i], y[i], s, p)
            }
            CONFETTI -> {
                c.save()
                c.translate(x[i], y[i])
                c.rotate(rot[i])
                c.scale(Math.cos((t * 4 + ph[i]).toDouble()).toFloat(), 1f)
                p.color = color[i]
                c.drawRect(-s / 2, -s * 0.3f, s / 2, s * 0.3f, p)
                c.restore()
            }
            BATS -> {
                val flap = Math.sin((t * 14 + ph[i]).toDouble()).toFloat()
                val bob = Math.sin((t * 2 + ph[i]).toDouble()).toFloat() * 10 * u
                c.save()
                c.translate(x[i], y[i] + bob)
                if (vx[i] < 0) c.scale(-1f, 1f)
                path.reset()
                val wy = -s * 0.6f * flap   // wing tips go up and down
                path.moveTo(0f, 0f)
                path.quadTo(-s * 0.5f, wy - s * 0.2f, -s * 1.3f, wy)
                path.quadTo(-s * 0.9f, s * 0.15f, -s * 0.75f, s * 0.35f)
                path.quadTo(-s * 0.5f, s * 0.1f, -s * 0.3f, s * 0.35f)
                path.lineTo(0f, s * 0.2f)
                path.lineTo(s * 0.3f, s * 0.35f)
                path.quadTo(s * 0.5f, s * 0.1f, s * 0.75f, s * 0.35f)
                path.quadTo(s * 0.9f, s * 0.15f, s * 1.3f, wy)
                path.quadTo(s * 0.5f, wy - s * 0.2f, 0f, 0f)
                path.close()
                p.color = 0xF0140A1C.toInt()
                c.drawPath(path, p)
                c.drawCircle(0f, s * 0.05f, s * 0.22f, p)
                c.restore()
            }
            BUBBLES -> {
                p.style = Paint.Style.FILL
                p.color = Color.alpha(color[i], 0.18f)
                c.drawCircle(x[i], y[i], s, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2 * u
                p.color = Color.alpha(0xFFFFFFFF.toInt(), 0.8f)
                c.drawCircle(x[i], y[i], s, p)
                p.style = Paint.Style.FILL
                p.color = 0xCCFFFFFF.toInt()
                c.drawCircle(x[i] - s * 0.35f, y[i] - s * 0.35f, s * 0.18f, p)
            }
        }
    }

    private fun shootingStar(c: Canvas, now: Long, w: Int, h: Int) {
        if (shootAt == 0L || now - shootAt > 12_000 + rnd.nextInt(8_000)) {
            shootAt = now
            shootX = w * (0.3f + rnd.nextFloat() * 0.6f)
            shootY = h * rnd.nextFloat() * 0.3f
        }
        val k = (now - shootAt) / 900f
        if (k > 1) return
        val len = 140 * u
        val dx = -k * 380 * u
        val dy = k * 160 * u
        p.strokeWidth = 1.6f * u
        val g = streak ?: android.graphics.LinearGradient(0f, 0f, len, -len * 0.42f,
                0xFFFFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP).also { streak = it }
        streakAt.setTranslate(shootX + dx, shootY + dy)
        g.setLocalMatrix(streakAt)
        p.shader = g
        p.color = Color.alpha(0xFFFFFFFF.toInt(), 1 - k)   // the fade, through the paint's alpha
        c.drawLine(shootX + dx, shootY + dy, shootX + dx + len, shootY + dy - len * 0.42f, p)
        p.shader = null
    }

    private fun fireworks(c: Canvas, now: Long, w: Int, h: Int) {
        if (now > nextBurst) {
            val b = Burst()
            b.x = w * (0.15f + rnd.nextFloat() * 0.7f)
            b.y = h * (0.12f + rnd.nextFloat() * 0.35f)
            b.rise = h * 0.35f
            b.born = now
            b.color = burstColors[rnd.nextInt(burstColors.size)]
            val k = 46
            b.ang = FloatArray(k); b.spd = FloatArray(k)
            for (i in 0 until k) { b.ang[i] = (i * 2 * Math.PI / k + rnd.nextFloat() * 0.1f).toFloat(); b.spd[i] = (90 + rnd.nextFloat() * 60) * u }
            bursts.add(b)
            nextBurst = now + 1400 + rnd.nextInt(2200)
        }
        p.strokeWidth = 2 * u
        for (j in bursts.size - 1 downTo 0) {
            val b = bursts[j]
            val age = (now - b.born) / 1000f
            if (age < 0.6f) {   // the rocket going up
                val k = age / 0.6f
                val yy = b.y + b.rise * (1 - k * (2 - k))
                p.color = 0xCCFFE8B0.toInt()
                c.drawCircle(b.x, yy, 2 * u, p)
                continue
            }
            val a = age - 0.6f
            if (a > 2f) { bursts.removeAt(j); continue }
            val fade = 1 - a / 2f
            for (i in b.ang.indices) {
                val r = b.spd[i] * (1 - Math.exp((-a * 2.2f).toDouble()).toFloat()) / 1.1f
                val px = b.x + Math.cos(b.ang[i].toDouble()).toFloat() * r
                val py = b.y + Math.sin(b.ang[i].toDouble()).toFloat() * r + 30 * u * a * a
                p.color = Color.alpha(b.color, fade * (0.6f + 0.4f * Math.sin((a * 30 + i).toDouble()).toFloat()))
                c.drawCircle(px, py, 1.8f * u, p)
            }
        }
    }

    private fun sway(t: Float, i: Int): Float = Math.sin((t * 3 + ph[i]).toDouble()).toFloat() * 6 * u

    /** Small color helpers. */
    object Color {
        @JvmStatic
        fun alpha(c: Int, a: Float): Int = ((Math.max(0f, Math.min(1f, a)) * 255).toInt() shl 24) or (c and 0xFFFFFF)
    }

    companion object {
        const val NONE = 0
        const val STARS = 1
        const val PETALS = 2
        const val LEAVES = 3
        const val SNOW = 4
        const val MOTES = 5
        const val CONFETTI = 6
        const val BATS = 7
        const val FIREWORKS = 8
        const val BUBBLES = 9
        const val EMBERS = 10

        private val LEAF = intArrayOf(0xFFE0602A.toInt(), 0xFFC93B1E.toInt(), 0xFFF0A830.toInt(), 0xFF9C5A26.toInt(), 0xFFD9822B.toInt())
        private val PETAL = intArrayOf(0xFFFFC4D6.toInt(), 0xFFFFD9E4.toInt(), 0xFFFFB0C8.toInt(), 0xFFFFFFFF.toInt())
        private val CONFETTI_C = intArrayOf(0xFFFF5A5F.toInt(), 0xFFFFD23F.toInt(), 0xFF2EC4B6.toInt(), 0xFF3A86FF.toInt(), 0xFFFF9F1C.toInt())
    }
}
