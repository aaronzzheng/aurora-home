package home.aurora

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Art: one painting in a real museum room.  A claret damask wall under a
 * ceiling spotlight; the work hangs from the picture rail on two wires in a
 * carved, gilded frame, with a proper wall label beside it.  The apps are a
 * row of small works in black-and-gilt frames below, and the everyday
 * controls are engraved brass plaques under the dado rail.  The time is wall
 * lettering, the way a gallery names its rooms.
 */
class ArtRoom(h: HomeActivity, t: HomeTheme) : Room(h, t) {

    private val serif = Room.tf("serif", Typeface.NORMAL)
    private val italic = Room.tf("serif", Typeface.ITALIC)
    private val serifBold = Room.tf("serif", Typeface.BOLD)
    private lateinit var gallery: ArtView
    private lateinit var lettering: View
    private var brushed: Bitmap? = null

    // the framed painting, rendered once per work (software, so shadows can blur)
    private class Hung(val src: Bitmap, val bmp: Bitmap, val x: Float, val y: Float)
    private val hung = ArrayList<Hung>()
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun build() {
        art { c -> wall(c) }
        gallery = ArtView(home)
        gallery.setGallery()
        gallery.setListener { }
        gallery.setHanger { c, bmp, title, byline, credit, alpha -> hang(c, bmp, title, byline, credit, alpha) }
        place(gallery, 0f, 0f, W.toFloat(), H.toFloat())
        lettering = drawn({ c -> lettering(c) }, 640f, 22f, 290f, 110f)

        brushed = brushedBrass()
        val order = intArrayOf(GAMES, INPUTS, LIVE, TIMER, SLEEP, SETTINGS)
        for (i in order.indices) {
            val a = order[i]
            val v = control(a, X0 + i * PITCH, PLAQUE_Y, SLOT_W, PLAQUE_H) { c, w, h, f -> plaque(c, w, h, f, a) }
            outline(v, PLAQUE_H, 0f)
            v.setOnFocusChangeListener { x, has -> lift(x, has, 1.07f, 8f) }
        }
    }

    override fun setApps(apps: List<Apps.App>, back: Apps.App?) {
        clearApps()
        val n = min(apps.size, SLOTS - 1)
        for (i in 0 until n) {
            val a = apps[i]
            val art = artOf(a)
            val v = app(a, X0 + i * PITCH, MINI_Y, SLOT_W, MINI_H + 20) { c, w, _, f -> miniature(c, w, f, art, art.label) }
            outline(v, MINI_H, 0f)
            v.setOnFocusChangeListener { x, has -> lift(x, has, 1.12f, 16f) }
        }
        val all = Widget(home, this) { c, w, _, f -> miniature(c, w, f, null, "The Collection") }
        all.contentDescription = "All apps"
        all.setOnClickListener { v -> home.act(ALL, v) }
        all.hook = Room.Hook { has -> if (has) home.actionFocused("All apps") }
        all.setOnFocusChangeListener { x, has -> lift(x, has, 1.12f, 16f) }
        appViews.add(all)
        place(all, X0 + n * PITCH, MINI_Y, SLOT_W, MINI_H + 20)
        outline(all, MINI_H, 0f)
    }

    override fun tick(now: Date, hhmm: String) {
        super.tick(now, hhmm)
        lettering.invalidate()
    }

    override fun start() { gallery.start() }

    override fun stop() { gallery.stop() }

    /** Lights come up slowly in a gallery. */
    override fun onReturn() {
        stage.alpha = 0.15f
        stage.animate().alpha(1f).setDuration(700).start()
    }

    // ------------------------------------------------------------------ focus

    /** The rectangle the system shadow is cast from (the frame or plate, not its caption). */
    private fun outline(v: View, height: Float, top: Float) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, o: Outline) {
                o.setRect(0, Math.round(px(top)), view.width, Math.round(px(height)))
                o.alpha = 0.85f
            }
        }
    }

    private fun lift(v: View, has: Boolean, scale: Float, z: Float) {
        v.pivotX = v.width / 2f
        v.pivotY = v.height / 2f
        if (has) v.animate().scaleX(scale).scaleY(scale).translationZ(px(z)).setDuration(300).setInterpolator(DecelerateInterpolator(2f)).start()
        else v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(200).start()
    }

    // ------------------------------------------------------------------ the room

    private fun texture(id: Int, sx: Float, sy: Float): BitmapShader {
        val o = BitmapFactory.Options()
        o.inScaled = false
        val s = BitmapShader(BitmapFactory.decodeResource(home.resources, id, o), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val m = Matrix()
        m.setScale(sx, sy)
        s.setLocalMatrix(m)
        return s
    }

    private fun wall(c: Canvas) {
        val p = Room.paint()
        val w = W.toFloat()
        // claret damask, lit by one spotlight on the painting and a softer wash on the small works
        p.alpha = 255; p.shader = texture(R.drawable.art_damask, 0.32f, 0.32f)
        c.drawRect(0f, 0f, w, FLOOR, p)
        p.alpha = 255; p.shader = RadialGradient(HERO_CX, 150f, 560f, intArrayOf(0x00000000, 0x55000000, 0xCC000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, FLOOR, p)
        c.save()
        c.scale(1f, 0.78f, HERO_CX, 120f)
        p.alpha = 255; p.shader = RadialGradient(HERO_CX, 120f, 330f, intArrayOf(0x58FFDDA8, 0x2AFFD49A, 0x00FFD49A), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, -200f, w, FLOOR + 200, p)
        c.restore()
        c.save()
        c.scale(1f, 0.32f, 480f, MINI_Y + 40)
        p.alpha = 255; p.shader = RadialGradient(480f, MINI_Y + 40, 470f, intArrayOf(0x22FFDDA8, 0x00FFDDA8), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, H.toFloat() * 3, p)
        c.restore()
        p.shader = null

        // picture rail: a thin brass rod under the cornice
        p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, 14f, intArrayOf(0xFF140A0A.toInt(), 0xFF2A1515.toInt(), 0xFF0E0707.toInt()), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, 12f, p)
        p.alpha = 255; p.shader = LinearGradient(0f, 12f, 0f, 16f, intArrayOf(0xFFE9CC86.toInt(), 0xFF9C7634.toInt(), 0xFF4A3414.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 12f, w, 16f, p)
        p.shader = null
        p.color = 0x55000000
        c.drawRect(0f, 16f, w, 18f, p)

        // dado rail above the plaques
        p.alpha = 255; p.shader = LinearGradient(0f, DADO, 0f, DADO + 7, intArrayOf(0xFF3A1E14.toInt(), 0xFF1A0C08.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, DADO, w, DADO + 7, p)
        p.shader = null
        p.color = 0x40FFE2B0
        c.drawRect(0f, DADO, w, DADO + 0.8f, p)
        p.color = 0x66000000
        c.drawRect(0f, DADO + 7, w, DADO + 10, p)

        // skirting board, then a herringbone parquet floor seen low and front-on
        p.alpha = 255; p.shader = LinearGradient(0f, FLOOR - 12, 0f, FLOOR, intArrayOf(0xFF2A1510.toInt(), 0xFF120806.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, FLOOR - 12, w, FLOOR, p)
        p.shader = null
        p.color = 0x33FFE2B0
        c.drawRect(0f, FLOOR - 12, w, FLOOR - 11.2f, p)
        p.alpha = 255; p.shader = texture(R.drawable.art_parquet, 0.22f, 0.09f)
        c.drawRect(0f, FLOOR, w, H.toFloat(), p)
        p.alpha = 255; p.shader = LinearGradient(0f, FLOOR, 0f, H.toFloat(), intArrayOf(0xCC000000.toInt(), 0x44000000, 0x99000000.toInt()), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, FLOOR, w, H.toFloat(), p)
        c.save()
        c.scale(1f, 0.12f, HERO_CX, FLOOR + 12)
        p.alpha = 255; p.shader = RadialGradient(HERO_CX, FLOOR + 12, 360f, intArrayOf(0x30FFDDA8, 0x00FFDDA8), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, H.toFloat() * 10, p)
        c.restore()
        p.shader = null
        // the corners of the room fall into shadow
        p.alpha = 255; p.shader = RadialGradient(480f, 250f, 640f, intArrayOf(0x00000000, 0x00000000, 0x80000000.toInt()), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, H.toFloat(), p)
        p.shader = null
        holidayProps(c)
    }

    private fun holidayProps(c: Canvas) {
        if (holiday == null) return
        when (holiday) {
            "halloween" -> {
                Room.pumpkin(c, 36f, 530f, 16f)
                Room.pumpkin(c, 66f, 532f, 10f)
                Room.pumpkin(c, 920f, 530f, 13f)
                Room.bat(c, 74f, 70f, 12f, 0xE60B0606.toInt())
                Room.bat(c, 104f, 98f, 8f, 0xE60B0606.toInt())
            }
            "christmas" -> {
                Room.garland(c, 0f, W.toFloat(), 18f, 8f, true)
                Room.wreath(c, 70f, 150f, 16f)
            }
            "newyear" -> Room.bunting(c, 0f, W.toFloat(), 18f, 12f, NEWYEAR)
            else -> Room.bunting(c, 0f, W.toFloat(), 18f, 12f, JULY4)
        }
    }

    /** Wall lettering: where a gallery names the room, the time. */
    private fun lettering(c: Canvas) {
        val w = 290f
        val eb = Room.textPaint(7.5f, 0xB3E6D3A6.toInt(), serif)
        eb.letterSpacing = 0.42f
        eb.textAlign = Paint.Align.RIGHT
        val g = theme.greeting
        c.drawText(g?.uppercase(Locale.getDefault()) ?: "EAST WING  ·  GALLERY 7", w - 2, 14f, eb)
        val big = Room.textPaint(50f, 0xFFF0E4C8.toInt(), serif)
        big.textAlign = Paint.Align.RIGHT
        big.letterSpacing = 0.02f
        big.setShadowLayer(2f, 0f, 1f, 0x66000000)
        c.drawText(Room.time(hhmm), w, 64f, big)
        val p = Room.paint()
        p.color = 0x80D8B15B.toInt()
        c.drawRect(w - 118f, 74f, w, 74.8f, p)
        val d = Room.textPaint(13f, 0xE6EADBB8.toInt(), italic)
        d.textAlign = Paint.Align.RIGHT
        c.drawText(Room.fmt("EEEE, d MMMM", now), w, 94f, d)
    }

    // ------------------------------------------------------------------ the painting

    private fun hang(c: Canvas, bmp: Bitmap, title: String, byline: String, credit: String, alpha: Int) {
        var h: Hung? = null
        for (x in hung) if (x.src === bmp) h = x
        if (h == null) {
            h = render(bmp, title, byline, credit)
            hung.add(h)
            while (hung.size > 2) hung.removeAt(0).bmp.recycle()
        }
        blit.alpha = alpha
        c.drawBitmap(h.bmp, h.x * k, h.y * k, blit)
    }

    /** The painting in its frame, the wires and the wall label, as one bitmap in stage pixels. */
    private fun render(src: Bitmap, title: String, byline: String, credit: String): Hung {
        val iw = src.width.toFloat()
        val ih = src.height.toFloat()
        val s = min(PIC_W / iw, PIC_H / ih)
        val pw = iw * s
        val ph = ih * s
        val pic = RectF(HERO_CX - pw / 2, FRAME_TOP + FW, HERO_CX + pw / 2, FRAME_TOP + FW + ph)
        val fr = RectF(pic.left - FW, pic.top - FW, pic.right + FW, pic.bottom + FW)

        // the label, beside the frame near its foot
        val parts = byline.split("  ·  ")
        val artist = parts[0].trim().ifEmpty { "Unknown artist" }
        val date = if (parts.size > 1) parts.subList(1, parts.size).joinToString(", ").trim() else ""
        val tp = Room.textPaint(10.5f, 0xFF231F1B.toInt(), italic)
        val lines = wrap(title, tp, LABEL_W - 20, 2)
        val labH = 46f + lines.size * 13f
        val lab = RectF(fr.right + 26, fr.bottom - labH - 4, fr.right + 26 + LABEL_W, fr.bottom - 4)

        val box = RectF(fr.left - 30, 0f, max(fr.right + 30, lab.right + 12), fr.bottom + 34)
        val out = Bitmap.createBitmap(max(1, Math.round(box.width() * k)), max(1, Math.round(box.height() * k)), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.scale(k, k)
        c.translate(-box.left, -box.top)
        val p = Room.paint()

        // hanging wires from the rail
        p.color = 0x99100A06.toInt()
        p.strokeWidth = 0.9f
        val hook = 16f
        c.drawLine(fr.centerX(), hook, fr.left + fr.width() * 0.18f, fr.top + 6, p)
        c.drawLine(fr.centerX(), hook, fr.right - fr.width() * 0.18f, fr.top + 6, p)
        p.color = 0x40FFE7B8
        p.strokeWidth = 0.4f
        c.drawLine(fr.centerX() - 0.5f, hook, fr.left + fr.width() * 0.18f - 0.5f, fr.top + 6, p)
        p.color = 0xFF2A1C10.toInt()
        c.drawCircle(fr.centerX(), hook + 1, 2.2f, p)

        // cast shadow on the wall: the spotlight is above and slightly in front
        p.color = 0xCC000000.toInt()
        p.maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
        c.drawRect(fr.left + 2, fr.top + 10, fr.right + 2, fr.bottom + 16, p)
        p.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        p.color = 0x99000000.toInt()
        c.drawRect(fr.left, fr.top + 3, fr.right + 1, fr.bottom + 5, p)
        p.maskFilter = null

        // the canvas itself, lit from above, with the frame's own shadow across its top edge
        c.drawBitmap(src, null, pic, Room.paint())
        p.alpha = 255; p.shader = LinearGradient(0f, pic.top, 0f, pic.bottom, intArrayOf(0x10FFF0D0, 0x00000000, 0x30000000), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(pic, p)
        p.alpha = 255; p.shader = LinearGradient(0f, pic.top, 0f, pic.top + 9, 0x99000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(pic, p)
        p.alpha = 255; p.shader = LinearGradient(pic.left, 0f, pic.left + 5, 0f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(pic, p)
        p.alpha = 255; p.shader = LinearGradient(pic.right, 0f, pic.right - 4, 0f, 0x44000000, 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(pic, p)
        p.shader = null

        gildedFrame(c, fr, FW)
        label(c, lab, artist, lines, date, credit)
        return Hung(src, out, box.left, box.top)
    }

    /** A carved, gilded moulding: a profile of rounded and hollow bands lit from the upper left. */
    private fun gildedFrame(c: Canvas, o: RectF, fw: Float) {
        val p = Room.paint()
        val n = PROFILE.size / 2
        // outward screen direction of each side: top, right, bottom, left
        val ox = floatArrayOf(0f, 1f, 0f, -1f)
        val oy = floatArrayOf(-1f, 0f, 1f, 0f)
        for (i in 0 until n - 1) {
            val d0 = PROFILE[i * 2] * fw
            val d1 = PROFILE[i * 2 + 2] * fw
            for (side in 0 until 4) {
                val b0 = light(PROFILE[i * 2 + 1], ox[side], oy[side])
                val b1 = light(PROFILE[i * 2 + 3], ox[side], oy[side])
                val path = Path()
                val a = RectF(o.left + d0, o.top + d0, o.right - d0, o.bottom - d0)
                val b = RectF(o.left + d1, o.top + d1, o.right - d1, o.bottom - d1)
                val g: LinearGradient
                when (side) {
                    0 -> { path.moveTo(a.left, a.top); path.lineTo(a.right, a.top); path.lineTo(b.right, b.top); path.lineTo(b.left, b.top)
                        g = LinearGradient(0f, a.top, 0f, b.top, gold(b0), gold(b1), Shader.TileMode.CLAMP) }
                    1 -> { path.moveTo(a.right, a.top); path.lineTo(a.right, a.bottom); path.lineTo(b.right, b.bottom); path.lineTo(b.right, b.top)
                        g = LinearGradient(a.right, 0f, b.right, 0f, gold(b0), gold(b1), Shader.TileMode.CLAMP) }
                    2 -> { path.moveTo(a.right, a.bottom); path.lineTo(a.left, a.bottom); path.lineTo(b.left, b.bottom); path.lineTo(b.right, b.bottom)
                        g = LinearGradient(0f, a.bottom, 0f, b.bottom, gold(b0), gold(b1), Shader.TileMode.CLAMP) }
                    else -> { path.moveTo(a.left, a.bottom); path.lineTo(a.left, a.top); path.lineTo(b.left, b.top); path.lineTo(b.left, b.bottom)
                        g = LinearGradient(a.left, 0f, b.left, 0f, gold(b0), gold(b1), Shader.TileMode.CLAMP) }
                }
                path.close()
                p.alpha = 255; p.shader = g
                c.drawPath(path, p)
            }
        }
        p.shader = null
        // gold leaf is never even: mottling and worn bole showing through, only on the frame
        val ring = Path()
        ring.fillType = Path.FillType.EVEN_ODD
        ring.addRect(o, Path.Direction.CW)
        ring.addRect(o.left + fw, o.top + fw, o.right - fw, o.bottom - fw, Path.Direction.CW)
        c.save()
        c.clipPath(ring)
        val r = Random(41)
        val per = (o.width() + o.height()) * 2
        for (i in 0 until (per * 1.6f).toInt()) {
            val side = r.nextInt(4)
            val along = r.nextFloat()
            val depth = r.nextFloat() * fw
            val x: Float
            val y: Float
            when (side) {
                0 -> { x = o.left + along * o.width(); y = o.top + depth }
                1 -> { x = o.right - depth; y = o.top + along * o.height() }
                2 -> { x = o.left + along * o.width(); y = o.bottom - depth }
                else -> { x = o.left + depth; y = o.top + along * o.height() }
            }
            p.color = when (r.nextInt(5)) { 0 -> 0x16FFF4C8; 1 -> 0x1A201006; 2 -> 0x12B05A30; else -> 0x0C000000 }
            c.drawCircle(x, y, 0.3f + r.nextFloat() * 0.7f, p)
        }
        c.restore()
        // a row of carved beads in the hollow
        val bt = 0.70f * fw
        val br = 0.09f * fw * 0.5f + 0.6f
        val bead = RectF(o.left + bt, o.top + bt, o.right - bt, o.bottom - bt)
        val step = br * 2.3f
        fun beadAt(x: Float, y: Float) {
            p.color = 0x66000000
            c.drawCircle(x + br * 0.25f, y + br * 0.35f, br * 1.05f, p)
            p.alpha = 255; p.shader = RadialGradient(x - br * 0.35f, y - br * 0.4f, br * 1.4f, intArrayOf(0xFFFFF0B8.toInt(), 0xFFC9A04C.toInt(), 0xFF5E4116.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, br, p)
            p.shader = null
        }
        run { var x = bead.left; while (x <= bead.right) { beadAt(x, bead.top); beadAt(x, bead.bottom); x += step } }
        run { var y = bead.top + step; while (y < bead.bottom - step * 0.5f) { beadAt(bead.left, y); beadAt(bead.right, y); y += step } }
        // corner mitres
        p.color = 0x55301C08
        p.strokeWidth = 0.6f
        c.drawLine(o.left, o.top, o.left + fw, o.top + fw, p)
        c.drawLine(o.right, o.top, o.right - fw, o.top + fw, p)
        c.drawLine(o.left, o.bottom, o.left + fw, o.bottom - fw, p)
        c.drawLine(o.right, o.bottom, o.right - fw, o.bottom - fw, p)
        // the spotlight catches the top of the frame most
        p.alpha = 255; p.shader = LinearGradient(0f, o.top, 0f, o.bottom, intArrayOf(0x24FFF2CC, 0x00000000, 0x38000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(ring, p)
        p.shader = null
    }

    private fun light(m: Float, ox: Float, oy: Float): Float {
        // surface normal for a profile rising toward the picture with slope m, on a side facing (ox, oy)
        var nx = ox * m * 1.6f
        var ny = oy * m * 1.6f
        var nz = 1f
        val len = sqrt(nx * nx + ny * ny + nz * nz)
        nx /= len; ny /= len; nz /= len
        val d = max(0f, nx * LX + ny * LY + nz * LZ)
        // half vector between the light and the viewer, for a glint
        var hx = LX; var hy = LY; var hz = LZ + 1f
        val hl = sqrt(hx * hx + hy * hy + hz * hz)
        hx /= hl; hy /= hl; hz /= hl
        val sp = max(0f, nx * hx + ny * hy + nz * hz)
        val spec = Math.pow(sp.toDouble(), 24.0).toFloat()
        return 0.14f + 0.72f * d + 0.55f * spec
    }

    private fun gold(b: Float): Int {
        // dark umber bole -> burnished gold -> pale highlight
        val stops = floatArrayOf(0f, 0.4f, 0.75f, 1f, 1.25f)
        val cols = intArrayOf(0xFF1E1206.toInt(), 0xFF5E3F12.toInt(), 0xFFA8782A.toInt(), 0xFFE0B654.toInt(), 0xFFFFF0C0.toInt())
        val v = min(1.3f, max(0f, b))
        var i = 0
        while (i < stops.size - 2 && v > stops[i + 1]) i++
        val f = (v - stops[i]) / (stops[i + 1] - stops[i])
        return mix(cols[i], cols[i + 1], f)
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        val r = ((a shr 16 and 255) + ((b shr 16 and 255) - (a shr 16 and 255)) * f).toInt()
        val g = ((a shr 8 and 255) + ((b shr 8 and 255) - (a shr 8 and 255)) * f).toInt()
        val bl = ((a and 255) + ((b and 255) - (a and 255)) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    /** A museum wall label: a thin mounted card with artist, title, date and credit line. */
    private fun label(c: Canvas, r: RectF, artist: String, title: List<String>, date: String, credit: String) {
        val p = Room.paint()
        p.color = 0x99000000.toInt()
        p.maskFilter = BlurMaskFilter(3.5f, BlurMaskFilter.Blur.NORMAL)
        c.drawRect(r.left + 1.5f, r.top + 2.5f, r.right + 1.5f, r.bottom + 3f, p)
        p.maskFilter = null
        p.color = 0xFFB9B1A2.toInt()
        c.drawRect(r.left, r.top + 1, r.right + 1, r.bottom + 1, p)
        p.alpha = 255; p.shader = LinearGradient(0f, r.top, 0f, r.bottom, 0xFFF6F2E9.toInt(), 0xFFE3DCCD.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        p.shader = null
        val x = r.left + 10
        var y = r.top + 15
        val a = Room.textPaint(9f, 0xFF1E1A16.toInt(), serifBold)
        c.drawText(Room.fit(artist, a, r.width() - 20), x, y, a)
        y += 14
        val t = Room.textPaint(10.5f, 0xFF231F1B.toInt(), italic)
        for (l in title) { c.drawText(l, x, y, t); y += 13 }
        val d = Room.textPaint(8.5f, 0xFF4A443C.toInt(), serif)
        if (date.isNotEmpty()) c.drawText(Room.fit(date, d, r.width() - 20), x, y, d)
        val cr = Room.textPaint(6.5f, 0xFF7A7266.toInt(), serif)
        cr.letterSpacing = 0.04f
        c.drawText(Room.fit(credit, cr, r.width() - 20), x, r.bottom - 8, cr)
    }

    private fun wrap(s: String, p: Paint, max: Float, maxLines: Int): List<String> {
        val out = ArrayList<String>()
        var line = ""
        for (w in s.split(" ")) {
            val tryLine = if (line.isEmpty()) w else "$line $w"
            if (p.measureText(tryLine) <= max || line.isEmpty()) line = tryLine
            else { out.add(line); line = w }
        }
        if (line.isNotEmpty()) out.add(line)
        if (out.size > maxLines) {
            val keep = ArrayList(out.subList(0, maxLines))
            keep[maxLines - 1] = Room.fit(out.subList(maxLines - 1, out.size).joinToString(" "), p, max)
            return keep
        }
        return out.map { Room.fit(it, p, max) }
    }

    // ------------------------------------------------------------------ the small works and plaques

    private fun miniature(c: Canvas, w: Float, f: Boolean, art: AppArt?, name: String) {
        val p = Room.paint()
        val fr = RectF(0f, 0f, w, MINI_H)
        // black lacquer frame with a top highlight
        p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, MINI_H, intArrayOf(0xFF3A332E.toInt(), 0xFF141110.toInt(), 0xFF0A0908.toInt()), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(fr, p)
        p.shader = null
        p.color = if (f) 0x80FFF0D0.toInt() else 0x40FFF0D0
        c.drawRect(0f, 0f, w, 0.8f, p)
        p.color = 0x33FFFFFF
        c.drawRect(0f, 0f, 0.8f, MINI_H, p)
        // the gilt slip
        val slip = RectF(6f, 6f, w - 6, MINI_H - 6)
        p.alpha = 255; p.shader = LinearGradient(slip.left, slip.top, slip.right, slip.bottom, intArrayOf(0xFFF0D48C.toInt(), 0xFF9C7634.toInt(), 0xFFD9B25C.toInt(), 0xFF6B4E1E.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(slip, p)
        p.shader = null
        val pic = RectF(slip.left + 2, slip.top + 2, slip.right - 2, slip.bottom - 2)
        if (art != null) art.fill(c, pic)
        else {
            p.alpha = 255; p.shader = LinearGradient(0f, pic.top, 0f, pic.bottom, 0xFF3A1416.toInt(), 0xFF1C0809.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(pic, p)
            p.shader = null
            Room.icon(c, ALL, pic.centerX(), pic.centerY() - 7, 18f, 0xFFE3C57E.toInt(), 1.4f)
            val t = Room.textPaint(7.5f, 0xFFE3C57E.toInt(), serif)
            t.letterSpacing = 0.3f
            Room.centerText(c, "ALL APPS", pic.centerX(), pic.bottom - 9, t)
        }
        // the frame's shadow on the picture; the room is dim until a work is chosen
        p.alpha = 255; p.shader = LinearGradient(0f, pic.top, 0f, pic.top + 6, 0x80000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(pic, p)
        p.shader = null
        if (!f) {
            p.color = 0x59100A06
            c.drawRect(pic, p)
        } else {
            p.alpha = 255; p.shader = LinearGradient(pic.left, pic.top, pic.right, pic.bottom, intArrayOf(0x26FFFFFF, 0x00FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(pic, p)
            p.shader = null
        }
        val t = Room.textPaint(if (f) 9.5f else 8.5f, if (f) 0xFFF2DCA0.toInt() else 0xB3E9DCC0.toInt(), italic)
        t.setShadowLayer(2f, 0f, 1f, 0xCC000000.toInt())
        Room.centerText(c, Room.fit(name, t, w + 8), w / 2, MINI_H + 14, t)
    }

    /** Small brushed-brass tile, generated once, for the plaques. */
    private fun brushedBrass(): Bitmap {
        val b = Bitmap.createBitmap(256, 64, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint()
        val r = Random(7)
        for (y in 0 until 64) {
            val v = r.nextInt(40) - 20
            p.color = if (v > 0) ((v * 3) shl 24) or 0xFFFFFF else ((-v * 3) shl 24)
            c.drawRect(0f, y.toFloat(), 256f, y + 1f, p)
        }
        for (i in 0 until 160) {
            val y = r.nextInt(64).toFloat()
            val x = r.nextFloat() * 256
            p.color = if (r.nextBoolean()) 0x30FFFFFF else 0x30000000
            c.drawRect(x, y, x + 20 + r.nextFloat() * 90, y + 1, p)
        }
        return b
    }

    private fun plaque(c: Canvas, w: Float, h: Float, f: Boolean, action: Int) {
        val p = Room.paint()
        val r = RectF(0f, 0f, w, h)
        val cols = if (f) intArrayOf(0xFFFFF0BE.toInt(), 0xFFEAC676.toInt(), 0xFFC29A48.toInt(), 0xFFE2C070.toInt())
                   else intArrayOf(0xFF9E7F44.toInt(), 0xFF7E602C.toInt(), 0xFF5A421A.toInt(), 0xFF6E5426.toInt())
        p.alpha = 255; p.shader = LinearGradient(0f, 0f, 0f, h, cols, floatArrayOf(0f, 0.35f, 0.8f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        val bb = brushed
        if (bb != null) {
            val s = BitmapShader(bb, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            val m = Matrix()
            m.setScale(0.5f, 0.5f)
            s.setLocalMatrix(m)
            p.shader = s
            p.alpha = 110
            c.drawRect(r, p)
            p.alpha = 255
        }
        p.shader = null
        // bevelled edge
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = if (f) 0xCCFFF6D8.toInt() else 0x80E8CC8A.toInt()
        c.drawLine(0.5f, 0.5f, w - 0.5f, 0.5f, p)
        c.drawLine(0.5f, 0.5f, 0.5f, h - 0.5f, p)
        p.color = 0xB33A2A0E.toInt()
        c.drawLine(0.5f, h - 0.5f, w - 0.5f, h - 0.5f, p)
        c.drawLine(w - 0.5f, 0.5f, w - 0.5f, h - 0.5f, p)
        p.color = if (f) 0x664A3410 else 0x55241808
        p.strokeWidth = 0.6f
        c.drawRect(3.5f, 3.5f, w - 3.5f, h - 3.5f, p)
        p.style = Paint.Style.FILL
        // two slotted screws
        for (sx in floatArrayOf(7.5f, w - 7.5f)) {
            p.color = 0x664A3410.toInt()
            c.drawCircle(sx + 0.3f, h / 2 + 0.4f, 2.1f, p)
            p.alpha = 255; p.shader = RadialGradient(sx - 0.6f, h / 2 - 0.6f, 2.4f, if (f) 0xFFFFF6DA.toInt() else 0xFFE6CC8E.toInt(), 0xFF6A5020.toInt(), Shader.TileMode.CLAMP)
            c.drawCircle(sx, h / 2, 1.8f, p)
            p.shader = null
            p.color = 0xB33A2A0E.toInt()
            p.strokeWidth = 0.5f
            c.drawLine(sx - 1.3f, h / 2 + 0.6f, sx + 1.3f, h / 2 - 0.6f, p)
        }
        // engraved, black-filled lettering with a bright lower lip
        val ink = 0xFF1A1206.toInt()
        val lip = if (f) 0x99FFFFFF.toInt() else 0x59FFE6A8
        Room.icon(c, action, 23f, h / 2 + 0.5f, 12f, lip, 1.3f)
        Room.icon(c, action, 23f, h / 2, 12f, ink, 1.3f)
        val t = Room.textPaint(10.5f, lip, serifBold)
        t.letterSpacing = 0.08f
        val s = Room.fit(label(action).uppercase(Locale.getDefault()), t, w - 46)
        c.drawText(s, 34f, h / 2 + 4.3f, t)
        t.color = ink
        c.drawText(s, 34f, h / 2 + 3.7f, t)
        if (f) {
            p.alpha = 255; p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0x00FFFFFF, 0x40FFFFFF, 0x00FFFFFF), floatArrayOf(0.2f, 0.45f, 0.7f), Shader.TileMode.CLAMP)
            c.drawRect(r, p)
            p.shader = null
        }
    }

    override fun label(action: Int): String {
        val s = super.label(action)
        return if (action == SLEEP && s == "Sleep timer") "Sleep" else s
    }

    companion object {
        private const val HERO_CX = 360f
        private const val FRAME_TOP = 34f
        private const val FW = 24f
        private const val PIC_W = 440f
        private const val PIC_H = 222f
        private const val LABEL_W = 176f
        private const val X0 = 70f
        private const val PITCH = 140f
        private const val SLOT_W = 120f
        private const val SLOTS = 6
        private const val MINI_Y = 340f
        private const val MINI_H = 76f
        private const val DADO = 440f
        private const val PLAQUE_Y = 456f
        private const val PLAQUE_H = 26f
        private const val FLOOR = 500f
        // light from the upper left, in front
        private const val LX = -0.32f
        private const val LY = -0.62f
        private const val LZ = 0.716f
        /** The moulding's cross-section, outside to inside: (fraction across, slope rising toward the picture). */
        private val PROFILE = floatArrayOf(
                0f, 2.6f, 0.06f, 1.0f, 0.12f, 0.1f, 0.17f, -1.2f, 0.21f, -2.4f,
                0.23f, 0.4f, 0.34f, 1.1f, 0.48f, 0.5f, 0.58f, -0.3f, 0.62f, -1.8f,
                0.64f, 0.2f, 0.76f, 0.2f, 0.78f, 1.4f, 0.86f, 0.4f, 0.93f, -0.6f, 1f, -2.6f)
    }
}
