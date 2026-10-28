package home.aurora

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Random
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The Art gallery's built-in painting: "Harbor at First Light", an original
 * impressionist harbor painted in code.  A smooth underpainting is laid down
 * first, then thousands of short, color-jittered brush strokes sample it, the
 * way broken color works in a real canvas.  Rendered once and cached by
 * [ArtView].
 */
object Exhibit {

    @JvmStatic
    fun paint(w: Int, h: Int): Bitmap {
        val under = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        underpainting(Canvas(under), w, h)
        val px = IntArray(w * h)
        under.getPixels(px, 0, w, 0, 0, w, h)

        val out = under.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.strokeCap = Paint.Cap.ROUND
        val r = Random(1874)
        val u = w / 1280f
        val horizon = h * 0.56f
        val sunX = w * 0.66f

        val strokes = 18000
        for (i in 0 until strokes) {
            val x = r.nextFloat() * w
            val y = r.nextFloat() * h
            val base = px[min(h - 1, y.toInt()) * w + min(w - 1, x.toInt())]
            val water = y > horizon
            val len = (if (water) 8 + r.nextFloat() * 18 else 12 + r.nextFloat() * 22) * u
            val ang: Double = if (water) (r.nextFloat() - 0.5f) * 0.12 else (r.nextFloat() - 0.5f) * 0.35 + sin((x / w * 6 + y / h * 3).toDouble()) * 0.12
            val jitter = if (water) 22 else 16
            val cr = clamp(((base shr 16) and 0xFF) + r.nextInt(jitter * 2 + 1) - jitter + (if (water) -4 else 6))
            val cg = clamp(((base shr 8) and 0xFF) + r.nextInt(jitter * 2 + 1) - jitter)
            val cb = clamp((base and 0xFF) + r.nextInt(jitter * 2 + 1) - jitter + (if (water) 8 else -2))
            p.color = 0xD8000000.toInt() or (cr shl 16) or (cg shl 8) or cb
            p.strokeWidth = (if (water) 2.5f + r.nextFloat() * 3.5f else 3 + r.nextFloat() * 5) * u
            val dx = cos(ang).toFloat() * len / 2
            val dy = sin(ang).toFloat() * len / 2
            c.drawLine(x - dx, y - dy, x + dx, y + dy, p)
        }

        // broken light on the water under the sun
        for (i in 0 until 900) {
            val t = r.nextFloat()
            val y = horizon + 4 * u + t * t * (h - horizon) * 0.85f
            val spread = (18 + t * 150) * u
            val x = sunX + r.nextGaussian().toFloat() * spread * 0.55f
            p.color = if (r.nextInt(4) == 0) 0xE6FFE9B8.toInt() else 0xCCF7C98A.toInt()
            p.strokeWidth = (1.5f + r.nextFloat() * 2.5f) * u
            val len = (6 + r.nextFloat() * 16) * u
            c.drawLine(x - len / 2, y, x + len / 2, y + (r.nextFloat() - 0.5f) * u, p)
        }

        boats(c, w, h, u, r)

        // canvas weave
        p.strokeWidth = 1f
        p.color = 0x0C000000
        run { var x = 0; while (x < w) { c.drawLine(x.toFloat(), 0f, x.toFloat(), h.toFloat(), p); x += 3 } }
        p.color = 0x08FFFFFF
        run { var y = 0; while (y < h) { c.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), p); y += 3 } }
        under.recycle()
        return out
    }

    private fun clamp(v: Int): Int = if (v < 0) 0 else if (v > 255) 255 else v

    private fun underpainting(c: Canvas, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val horizon = h * 0.56f
        val sunX = w * 0.66f
        val sunY = h * 0.5f
        val u = w / 1280f
        p.shader = LinearGradient(0f, 0f, 0f, horizon, intArrayOf(0xFF2E3F66.toInt(), 0xFF6C6A96.toInt(), 0xFFD99A86.toInt(), 0xFFF4C892.toInt()),
                floatArrayOf(0f, 0.38f, 0.78f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), horizon, p)
        p.shader = RadialGradient(sunX, sunY, w * 0.34f, intArrayOf(0xCCFFE2A8.toInt(), 0x55F3A878, 0x00F3A878), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), horizon, p)
        // cloud bands
        val r = Random(9)
        val cloud = intArrayOf(0x66E9A6A0, 0x557C6E9E, 0x55F2C6A0, 0x44B98FA8)
        for (i in 0 until 9) {
            val y = h * (0.08f + r.nextFloat() * 0.36f)
            val x = r.nextFloat() * w
            val cw = w * (0.18f + r.nextFloat() * 0.3f)
            val ch = h * (0.02f + r.nextFloat() * 0.03f)
            p.shader = RadialGradient(x, y, cw / 2, cloud[i % cloud.size], cloud[i % cloud.size] and 0xFFFFFF, Shader.TileMode.CLAMP)
            c.save()
            c.scale(1f, ch / (cw / 2), x, y)
            c.drawCircle(x, y, cw / 2, p)
            c.restore()
        }
        p.shader = null
        // the sun
        p.color = 0xFFFFEBC0.toInt()
        c.drawCircle(sunX, sunY, 26 * u, p)
        // far shore: hills, a town and a spire
        val hills = Path()
        hills.moveTo(0f, horizon)
        hills.lineTo(0f, horizon - 44 * u)
        hills.quadTo(w * 0.12f, horizon - 70 * u, w * 0.26f, horizon - 36 * u)
        hills.quadTo(w * 0.38f, horizon - 18 * u, w * 0.48f, horizon - 10 * u)
        hills.lineTo(w * 0.48f, horizon)
        hills.close()
        p.color = 0xFF6A6488.toInt()
        c.drawPath(hills, p)
        p.color = 0xFF5A5478.toInt()
        for (i in 0 until 14) {
            val x = w * (0.03f + i * 0.026f)
            val bh = (10 + (i * 7 % 13)) * u
            c.drawRect(x, horizon - 30 * u - bh + i * 1.4f * u, x + 22 * u, horizon, p)
        }
        val spire = Path()
        spire.moveTo(w * 0.17f, horizon - 98 * u)
        spire.lineTo(w * 0.177f, horizon - 50 * u)
        spire.lineTo(w * 0.163f, horizon - 50 * u)
        spire.close()
        c.drawPath(spire, p)
        c.drawRect(w * 0.163f, horizon - 50 * u, w * 0.177f, horizon, p)
        // water
        p.shader = LinearGradient(0f, horizon, 0f, h.toFloat(), intArrayOf(0xFFE0A884.toInt(), 0xFF8A7C9C.toInt(), 0xFF3C4C72.toInt(), 0xFF243252.toInt()),
                floatArrayOf(0f, 0.18f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, horizon, w.toFloat(), h.toFloat(), p)
        p.shader = LinearGradient(sunX - 120 * u, 0f, sunX + 120 * u, 0f, intArrayOf(0x00F7C98A, 0x88F7C98A.toInt(), 0x00F7C98A), null, Shader.TileMode.CLAMP)
        c.drawRect(sunX - 160 * u, horizon, sunX + 160 * u, h.toFloat(), p)
        p.shader = null
        // reflection of the far shore
        p.color = 0x556A6488
        c.drawRect(0f, horizon, w * 0.48f, horizon + 26 * u, p)
    }

    private fun boats(c: Canvas, w: Int, h: Int, u: Float, r: Random) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        boat(c, p, w * 0.3f, h * 0.76f, 120 * u, true)
        boat(c, p, w * 0.47f, h * 0.67f, 74 * u, true)
        // a sailboat on the far water
        val sx = w * 0.17f
        val sy = h * 0.615f
        val s = 44 * u
        p.color = 0xFF3A3448.toInt()
        c.drawRect(sx - s * 0.6f, sy - s * 0.1f, sx + s * 0.6f, sy + s * 0.06f, p)
        p.color = 0xFFF0D8C0.toInt()
        val sail = Path()
        sail.moveTo(sx, sy - s * 1.3f); sail.lineTo(sx + s * 0.55f, sy - s * 0.16f); sail.lineTo(sx, sy - s * 0.16f); sail.close()
        c.drawPath(sail, p)
        p.color = 0xFFC8A890.toInt()
        val jib = Path()
        jib.moveTo(sx - s * 0.04f, sy - s * 1.1f); jib.lineTo(sx - s * 0.4f, sy - s * 0.16f); jib.lineTo(sx - s * 0.04f, sy - s * 0.16f); jib.close()
        c.drawPath(jib, p)
        // distant masts
        p.strokeWidth = 2 * u
        p.color = 0xCC4A4460.toInt()
        for (i in 0 until 6) {
            val x = w * (0.82f + i * 0.025f)
            c.drawLine(x, h * 0.56f, x + r.nextFloat() * 2 * u, h * (0.44f + r.nextFloat() * 0.05f), p)
        }
    }

    private fun boat(c: Canvas, p: Paint, x: Float, y: Float, s: Float, rower: Boolean) {
        // reflection
        p.color = 0x55201C2C
        for (i in 0 until 6) c.drawRect(x - s * (0.5f - i * 0.04f), y + s * (0.1f + i * 0.06f), x + s * (0.45f - i * 0.05f), y + s * (0.13f + i * 0.06f), p)
        p.color = 0xFF2A2638.toInt()
        val hull = Path()
        hull.moveTo(x - s * 0.55f, y - s * 0.1f)
        hull.quadTo(x, y + s * 0.16f, x + s * 0.55f, y - s * 0.14f)
        hull.lineTo(x + s * 0.4f, y + s * 0.06f)
        hull.lineTo(x - s * 0.42f, y + s * 0.06f)
        hull.close()
        c.drawPath(hull, p)
        if (!rower) return
        p.color = 0xFF1E1A2A.toInt()
        c.drawOval(x - s * 0.06f, y - s * 0.36f, x + s * 0.06f, y - s * 0.24f, p)
        c.drawRoundRect(x - s * 0.08f, y - s * 0.26f, x + s * 0.08f, y - s * 0.04f, s * 0.04f, s * 0.04f, p)
        p.strokeWidth = s * 0.025f
        c.drawLine(x - s * 0.05f, y - s * 0.14f, x - s * 0.5f, y + s * 0.12f, p)
        c.drawLine(x + s * 0.05f, y - s * 0.14f, x + s * 0.48f, y + s * 0.1f, p)
    }
}
