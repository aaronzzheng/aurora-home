package home.aurora

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Random

/**
 * Art mode: famous public-domain paintings from the Art Institute of Chicago's
 * open collection, filling the screen with a slow zoom and a tiny caption in the
 * corner. Paintings much taller than the screen are shown at full height over a
 * blurred copy of themselves instead of being cropped. Changes every 90 seconds;
 * the list of works is cached for a week.
 */
open class ArtView(c: Context) : View(c) {

    fun interface Listener { fun onUnavailable() }

    private class Work {
        lateinit var bmp: Bitmap
        lateinit var soft: Bitmap   // soft: tiny copy, drawn large = blurred backdrop
        lateinit var title: String
        lateinit var byline: String
        var credit = "Art Institute of Chicago  ·  public domain"
        var shownAt = 0L
    }

    private val main = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences
    private val rnd = Random()
    private val img = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG)
    private val byline = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var works: JSONArray? = null
    private var cur: Work? = null
    private var prev: Work? = null
    private var listener: Listener? = null
    private var running = false
    private var captionTop = -1f
    private var gallery = false
    private var wall: Bitmap? = null   // dp from the top, right-aligned; -1 = bottom-left

    private val next = Runnable { loadNext() }

    init {
        prefs = Prefs.of(c)
        shadow.color = 0x8C000000.toInt()
        title.color = 0xE6FFFFFF.toInt()
        title.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        title.textSize = dp(11f)
        title.setShadowLayer(dp(4f), 0f, 0f, 0xCC000000.toInt())
        byline.color = 0xB3FFFFFF.toInt()
        byline.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        byline.textSize = dp(8f)
        byline.letterSpacing = 0.16f
        byline.setShadowLayer(dp(4f), 0f, 0f, 0xCC000000.toInt())
    }

    fun setListener(l: Listener?) { listener = l }

    /** Lets a room hang the work itself (frame, light, label); `alpha` is the crossfade. */
    fun interface Hanger { fun hang(c: Canvas, bmp: Bitmap, title: String, byline: String, credit: String, alpha: Int) }

    private var hanger: Hanger? = null

    /** Gallery mode with the room's own wall: this view then draws only what the hanger paints. */
    fun setHanger(h: Hanger?) { hanger = h; invalidate() }

    /**
     * Museum mode for the home screen: a dark gallery wall under a spotlight,
     * the painting in a gold frame with a cream mat, and its label beside it.
     */
    fun setGallery() {
        gallery = true
        title.typeface = Typeface.create("serif", Typeface.ITALIC)
        title.textSize = dp(12f)
        loadExhibit()
    }

    /**
     * The gallery's own painting: an original harbor at first light, built from a
     * few thousand brush strokes the first time and cached on disk. It hangs
     * until the online collection answers, so an offline TV is never an empty wall.
     */
    private fun loadExhibit() {
        Thread({
            val f = java.io.File(context.cacheDir, "exhibit_harbor_v1.png")
            var b: Bitmap? = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
            if (b == null) {
                b = Exhibit.paint(1280, 860)
                try {
                    java.io.FileOutputStream(f).use { out ->
                        b.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                } catch (ignored: Exception) { }
            }
            val w = Work()
            w.bmp = b
            w.soft = Bitmap.createScaledBitmap(b, 32, Math.max(1, 32 * b.height / b.width), true)
            w.title = "Harbor at First Light"
            w.byline = "Aurora Collection  ·  oil on canvas"
            w.credit = "On view while the collection is offline"
            main.post {
                if (cur == null) {
                    w.shownAt = SystemClock.uptimeMillis()
                    cur = w
                }
                invalidate()
            }
        }, "exhibit").start()
    }

    /** Put the caption in the top-right corner, `dpFromTop` down (the home screen's dock is at the bottom). */
    fun setCaptionTopRight(dpFromTop: Float) {
        captionTop = dpFromTop
        title.typeface = Typeface.create("serif", Typeface.ITALIC)
        title.textSize = dp(13f)
    }

    // Each start() begins a new generation; a download from an older one (still in
    // flight after a quick pause and resume) shows its painting but schedules nothing,
    // so pausing and resuming never stacks up extra download chains.
    private var generation = 0
    private val listLock = Any()

    fun start() {
        running = true
        generation++
        main.removeCallbacks(next)
        loadNext()
        invalidate()
    }

    fun stop() {
        running = false
        generation++
        main.removeCallbacks(next)
    }

    private fun loadNext() {
        if (!running) return
        val gen = generation
        Thread({
            try {
                val list: JSONArray
                synchronized(listLock) {
                    if (works == null) works = list()
                    list = works!!
                }
                val w = list.getJSONObject(pick(list))
                val out = Work()
                out.title = w.optString("title")
                val artist = w.optString("artist_title", "")
                val date = w.optString("date_display", "")
                out.byline = (if (artist.isEmpty() || artist == "null") "" else artist) + (if (date.isEmpty()) "" else "  ·  $date")
                // Ask for enough pixels to fill a 1080p screen in whichever direction it'll be scaled.
                val th = w.optJSONObject("thumbnail")
                val aspect = if (th == null) 1.4f else th.optDouble("width", 1.4).toFloat() / th.optDouble("height", 1.0).toFloat()
                val size = if (aspect >= 16f / 9f * 0.7f) "1920," else ",1080"
                out.bmp = download("https://www.artic.edu/iiif/2/" + w.getString("image_id") + "/full/" + size + "/0/default.jpg")
                out.soft = Bitmap.createScaledBitmap(out.bmp, 32, Math.max(1, 32 * out.bmp.height / out.bmp.width), true)
                main.post {
                    prev = cur
                    out.shownAt = SystemClock.uptimeMillis()
                    cur = out
                    invalidate()
                    if (running && gen == generation) main.postDelayed(next, CHANGE_MS)
                }
            } catch (e: Exception) {
                main.post(Runnable {
                    if (gen != generation) return@Runnable
                    if (gallery) { if (running) main.postDelayed(next, CHANGE_MS) }   // keep the exhibit up, try again later
                    else if (cur == null && listener != null) listener!!.onUnavailable()
                    else if (running) main.postDelayed(next, CHANGE_MS)
                })
            }
        }, "art").start()
    }

    /** Shuffled walk through the list without repeats until it's used up. */
    @Throws(Exception::class)
    private fun pick(works: JSONArray): Int {
        var order = prefs.getString("art_order", "")!!
        var pos = prefs.getInt("art_pos", 0)
        var idx: Array<String> = if (order.isEmpty()) arrayOf() else order.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (idx.size != works.length() || pos >= idx.size) {
            val all = Array(works.length()) { it }
            java.util.Collections.shuffle(all.asList(), rnd)
            val b = StringBuilder()
            for (i in all) b.append(if (b.length > 0) "," else "").append(i)
            order = b.toString()
            idx = order.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            pos = 0
        }
        prefs.edit().putString("art_order", order).putInt("art_pos", pos + 1).apply()
        return Integer.parseInt(idx[pos])
    }

    @Throws(Exception::class)
    private fun list(): JSONArray {
        val cached = prefs.getString("art_list2", null)
        if (cached != null && System.currentTimeMillis() - prefs.getLong("art_list2_time", 0) < LIST_TTL)
            return JSONArray(cached)
        try {
            val all = JSONObject(Weather.get(LIST_URL)).getJSONArray("data")
            val keep = JSONArray()
            for (i in 0 until all.length()) {
                val w = all.getJSONObject(i)
                if (!w.isNull("image_id")) keep.put(w)
            }
            prefs.edit().putString("art_list2", keep.toString()).putLong("art_list2_time", System.currentTimeMillis()).apply()
            return keep
        } catch (e: Exception) {
            if (cached != null) return JSONArray(cached)   // stale beats nothing
            throw e
        }
    }

    override fun onDraw(c: Canvas) {
        if (gallery) { drawGallery(c); return }
        c.drawColor(0xFF000000.toInt())
        val now = SystemClock.uptimeMillis()
        val cur = cur
        val prev = prev
        val fading = cur != null && now - cur.shownAt < FADE_MS
        if (prev != null && fading) drawWork(c, prev, 255, now)
        if (cur != null) drawWork(c, cur, if (fading) (255 * (now - cur.shownAt) / FADE_MS).toInt() else 255, now)
        if (!fading) this.prev = null
        if (running) postInvalidateDelayed(if (fading) 16L else 66L)   // keep the slow zoom moving
    }

    private fun drawGallery(c: Canvas) {
        val hg = hanger
        if (hg != null) {
            val now = SystemClock.uptimeMillis()
            val cur = cur
            val prev = prev
            val fading = cur != null && now - cur.shownAt < FADE_MS
            val a = if (cur != null && fading) (255 * (now - cur.shownAt) / FADE_MS).toInt() else 255
            if (prev != null && fading) hg.hang(c, prev.bmp, prev.title, prev.byline, prev.credit, 255 - a)
            if (cur != null) hg.hang(c, cur.bmp, cur.title, cur.byline, cur.credit, a)
            if (!fading) this.prev = null
            if (fading) postInvalidateOnAnimation()   // the 2 s crossfade only
            return
        }
        val W = width
        val H = height
        if (wall == null || wall!!.width != W) wall = paintWall(W, H)
        c.drawBitmap(wall!!, 0f, 0f, null)
        val now = SystemClock.uptimeMillis()
        val cur = cur
        val prev = prev
        val fading = cur != null && now - cur.shownAt < FADE_MS
        if (prev != null && fading) framed(c, prev, 255 - (255 * (now - cur!!.shownAt) / FADE_MS).toInt())
        if (cur != null) framed(c, cur, if (fading) (255 * (now - cur.shownAt) / FADE_MS).toInt() else 255)
        else fallbackExhibit(c)   // offline TVs still open into a real gallery, not an empty wall
        if (!fading) this.prev = null
        // Only the crossfade animates; a new painting arriving calls invalidate() itself.
        if (running && fading) postInvalidateOnAnimation()
    }

    /** An original, locally drawn exhibition work used until the public-domain collection loads. */
    private fun fallbackExhibit(c: Canvas) {
        val W = width.toFloat()
        val H = height.toFloat()
        val fw = Math.min(W * .56f, H * .62f)
        val fh = fw * .66f
        val l = (W - fw) / 2
        val t = H * .105f
        val border = dp(24f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x99000000.toInt(); c.drawRect(l - border - dp(10f), t + dp(12f), l + fw + border + dp(10f), t + fh + border * 2 + dp(16f), p)
        p.shader = android.graphics.LinearGradient(l, t, l + fw, t + fh,
                intArrayOf(0xFFF3D98B.toInt(), 0xFF9A7130.toInt(), 0xFFE8C46A.toInt(), 0xFF6F4D1D.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(l - border, t, l + fw + border, t + fh + border * 2, p); p.shader = null
        p.color = 0xFFF2ECDF.toInt(); c.drawRect(l - dp(9f), t + border - dp(9f), l + fw + dp(9f), t + fh + border + dp(9f), p)
        p.shader = android.graphics.LinearGradient(l, t + border, l, t + fh + border,
                intArrayOf(0xFF355872.toInt(), 0xFFE2A56B.toInt(), 0xFF4A2D26.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(l, t + border, l + fw, t + fh + border, p); p.shader = null
        p.color = 0xCCF5D58B.toInt(); c.drawCircle(l + fw * .7f, t + border + fh * .28f, fh * .12f, p)
        p.color = 0xFF213E48.toInt(); val ridge = android.graphics.Path(); ridge.moveTo(l, t + border + fh * .72f)
        ridge.lineTo(l + fw * .28f, t + border + fh * .38f); ridge.lineTo(l + fw * .5f, t + border + fh * .68f); ridge.lineTo(l + fw * .76f, t + border + fh * .32f); ridge.lineTo(l + fw, t + border + fh * .7f); ridge.lineTo(l + fw, t + border + fh); ridge.lineTo(l, t + border + fh); ridge.close(); c.drawPath(ridge, p)
        p.color = 0xFFF6F1E7.toInt(); p.textSize = dp(12f); p.typeface = Typeface.create("serif", Typeface.ITALIC); c.drawText("Evening Study, Aurora Collection", l + fw + dp(40f), t + fh + border - dp(8f), p)
    }

    /** Deep green museum wall, fine vertical texture, a warm spotlight from above, a dark baseboard. */
    private fun paintWall(W: Int, H: Int): Bitmap {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFF1E302C.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = Random(5)
        run {
            var x = 0
            while (x < W) {   // linen-like wall texture
                p.color = if (r.nextBoolean()) 0x08FFFFFF else 0x0A000000
                c.drawRect(x.toFloat(), 0f, (x + 1 + r.nextInt(2)).toFloat(), H.toFloat(), p)
                x += 3
            }
        }
        p.shader = android.graphics.RadialGradient(W / 2f, -H * 0.15f, H * 1.05f,
                intArrayOf(0x66FFE3B0, 0x22FFD9A0, 0x00000000), floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        // architecture: crown molding, a dado rail, a doorway to the next room, oak floor
        val d = resources.displayMetrics.density
        val floor = H * 0.8f
        p.shader = android.graphics.LinearGradient(0f, 0f, 0f, 16 * d, intArrayOf(0xFF2C3F3A.toInt(), 0xFF4A5D57.toInt(), 0xFF16241F.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), 16 * d, p)
        p.shader = null
        p.color = 0x33000000
        c.drawRect(0f, 16 * d, W.toFloat(), 19 * d, p)
        p.color = 0x22FFFFFF
        c.drawRect(0f, floor - 46 * d, W.toFloat(), floor - 45 * d, p)
        p.color = 0x18000000
        c.drawRect(0f, floor - 45 * d, W.toFloat(), floor, p)
        // doorway, left
        val dl = 24 * d
        val dr = 104 * d
        val dt = H * 0.3f
        p.color = 0xFF16211E.toInt()
        c.drawRect(dl - 8 * d, dt - 8 * d, dr + 8 * d, floor, p)
        p.shader = android.graphics.LinearGradient(0f, dt, 0f, floor, 0xFF0B110F.toInt(), 0xFF22302B.toInt(), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(dl, dt, dr, floor, p)
        p.shader = null
        val sign = Paint(Paint.ANTI_ALIAS_FLAG)
        sign.color = 0xCCE7D5A8.toInt()
        sign.textSize = 7.5f * d
        sign.letterSpacing = 0.3f
        sign.typeface = Typeface.create("serif", Typeface.NORMAL)
        sign.textAlign = Paint.Align.CENTER
        c.drawText("GALLERY  7", (dl + dr) / 2, dt - 16 * d, sign)
        // oak floor with a soft reflection of the wall
        p.shader = android.graphics.LinearGradient(0f, floor, 0f, H.toFloat(), 0xFF5A3E26.toInt(), 0xFF2E1E12.toInt(), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, floor, W.toFloat(), H.toFloat(), p)
        p.shader = null
        p.color = 0x22000000
        run {
            var x = -H.toFloat()
            while (x < W) {
                c.drawLine(x, H.toFloat(), x + (H - floor) * 0.9f + 80 * d, floor, p)
                x += 58 * d
            }
        }
        p.color = 0xFF101814.toInt()
        c.drawRect(0f, floor - 6 * d, W.toFloat(), floor, p)
        p.shader = android.graphics.RadialGradient(W / 2f, H / 2f, W * 0.75f, 0x00000000, 0x99000000.toInt(), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null
        return b
    }

    private val fr = RectF()
    private val gold = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mat = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun framed(c: Canvas, w: Work, alpha: Int) {
        val W = width.toFloat()
        val H = height.toFloat()
        val iw = w.bmp.width.toFloat()
        val ih = w.bmp.height.toFloat()
        // The work is the destination, not a background accent.  A wider, taller
        // canvas gives the gallery one unmistakable focal point from the sofa.
        val maxW = W * 0.5f
        val maxH = H * 0.56f
        val s = Math.min(maxW / iw, maxH / ih)
        val pw = iw * s
        val ph = ih * s
        val matW = Math.min(pw, ph) * 0.07f
        val frameW = H * 0.024f
        val cx = W * 0.45f
        val top = H * 0.085f
        val left = cx - pw / 2
        // frame shadow on the wall
        fr.set(left - matW - frameW, top, left + pw + matW + frameW, top + ph + 2 * (matW + frameW))
        pictureLight(c, fr, alpha)
        mat.color = 0xFF000000.toInt()
        mat.alpha = (alpha * 0.5f).toInt()
        mat.setShadowLayer(dp(18f), 0f, dp(10f), 0xCC000000.toInt())
        c.drawRect(fr, mat)
        mat.clearShadowLayer()
        // gold frame
        gold.shader = android.graphics.LinearGradient(fr.left, fr.top, fr.right, fr.bottom,
                intArrayOf(0xFFF3D98B.toInt(), 0xFFB08A3E.toInt(), 0xFFE8C46A.toInt(), 0xFF7A5A22.toInt(), 0xFFD9B25C.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        gold.alpha = alpha
        c.drawRect(fr, gold)
        gold.shader = null
        gold.style = Paint.Style.STROKE
        gold.strokeWidth = dp(1.5f)
        gold.color = 0xFF5A4015.toInt()
        gold.alpha = alpha
        c.drawRect(fr.left + frameW * 0.35f, fr.top + frameW * 0.35f, fr.right - frameW * 0.35f, fr.bottom - frameW * 0.35f, gold)
        gold.style = Paint.Style.FILL
        // cream mat, then the painting
        fr.inset(frameW, frameW)
        mat.color = 0xFFF2ECDF.toInt()
        mat.alpha = alpha
        c.drawRect(fr, mat)
        mat.color = 0x33000000
        mat.alpha = (alpha * 0.2f).toInt()
        c.drawRect(fr.left, fr.top, fr.right, fr.top + dp(2f), mat)
        r.set(left, top + frameW + matW, left + pw, top + frameW + matW + ph)
        img.alpha = alpha
        c.drawBitmap(w.bmp, null, r, img)
        // the wall label, to the right of the frame, level with its bottom
        val lx = fr.right + frameW + dp(28f)
        val maxText = W - lx - dp(48f)
        val t = fit(w.title, title, maxText)
        val by = fit(w.byline.uppercase(java.util.Locale.getDefault()), byline, maxText)
        val cr = fit(w.credit, byline, maxText)
        val tw = Math.max(Math.max(title.measureText(t), byline.measureText(by)), byline.measureText(cr))
        val pad = dp(12f)
        val lb = fr.bottom + frameW
        r.set(lx, lb - dp(62f), lx + tw + pad * 2, lb)
        label.color = 0xFFF6F1E7.toInt()
        label.alpha = alpha
        label.setShadowLayer(dp(6f), 0f, dp(3f), 0x88000000.toInt())
        c.drawRect(r, label)
        title.textAlign = Paint.Align.LEFT
        byline.textAlign = Paint.Align.LEFT
        title.color = 0xFF2A2622.toInt()
        byline.color = 0xFF6B6257.toInt()
        title.clearShadowLayer()
        byline.clearShadowLayer()
        title.alpha = alpha
        byline.alpha = alpha
        c.drawText(t, r.left + pad, r.top + dp(20f), title)
        c.drawText(by, r.left + pad, r.top + dp(35f), byline)
        byline.color = 0xFF968B7C.toInt()
        byline.alpha = alpha
        c.drawText(cr, r.left + pad, r.top + dp(50f), byline)
    }

    /** A brass picture light over the frame, washing its top edge. */
    private fun pictureLight(c: Canvas, f: RectF, alpha: Int) {
        val cx = f.centerX()
        val bw = Math.min(f.width() * 0.42f, dp(170f))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.RadialGradient(cx, f.top, f.width() * 0.6f,
                intArrayOf(0x40FFE6B0, 0x00FFE6B0), null, android.graphics.Shader.TileMode.CLAMP)
        p.alpha = alpha
        c.drawRect(f.left - dp(40f), f.top - dp(30f), f.right + dp(40f), f.top + f.height() * 0.6f, p)
        p.shader = null
        p.color = 0xFF6B5222.toInt()
        p.alpha = alpha
        c.drawRect(cx - dp(2f), f.top - dp(16f), cx + dp(2f), f.top + dp(2f), p)
        p.shader = android.graphics.LinearGradient(0f, f.top - dp(24f), 0f, f.top - dp(12f),
                intArrayOf(0xFFF3D98B.toInt(), 0xFFB08A3E.toInt(), 0xFF6B5222.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        p.alpha = alpha
        c.drawRoundRect(cx - bw / 2, f.top - dp(24f), cx + bw / 2, f.top - dp(13f), dp(5f), dp(5f), p)
        p.shader = null
    }

    private fun drawWork(c: Canvas, w: Work, alpha: Int, now: Long) {
        val W = width.toFloat()
        val H = height.toFloat()
        val iw = w.bmp.width.toFloat()
        val ih = w.bmp.height.toFloat()
        val zoom = 1f + 0.05f * Math.min(1f, (now - w.shownAt) / CHANGE_MS.toFloat())   // slow push-in
        val cover = (iw / ih) >= (W / H) * 0.7f   // close enough to widescreen: fill it
        img.alpha = alpha
        if (cover) {
            val s = Math.max(W / iw, H / ih) * zoom
            r.set((W - iw * s) / 2, (H - ih * s) / 2, (W + iw * s) / 2, (H + ih * s) / 2)
            c.drawBitmap(w.bmp, null, r, img)
        } else {
            // Blurred, darkened copy fills the screen; the painting sits on top at full height.
            val bs = Math.max(W / iw, H / ih) * 1.1f
            r.set((W - iw * bs) / 2, (H - ih * bs) / 2, (W + iw * bs) / 2, (H + ih * bs) / 2)
            c.drawBitmap(w.soft, null, r, img)
            shadow.alpha = (alpha * 0.55f).toInt()
            c.drawRect(0f, 0f, W, H, shadow)
            val s = H / ih * zoom
            r.set((W - iw * s) / 2, (H - ih * s) / 2, (W + iw * s) / 2, (H + ih * s) / 2)
            c.drawBitmap(w.bmp, null, r, img)
        }

        // tiny caption, bottom-left corner (or top-right on the home screen)
        val tx = dp(22f)
        val ty = H - dp(30f)
        val align = Paint.Align.LEFT
        val t = if (w.title.length > 60) w.title.substring(0, 58) + "…" else w.title
        if (captionTop >= 0) {
            // a museum wall label: cream card, title in italics, artist and date underneath
            val by = w.byline.uppercase(java.util.Locale.getDefault())
            val tw = Math.max(title.measureText(t), byline.measureText(by))
            val pad = dp(14f)
            val right = W - dp(56f)
            val top = dp(captionTop)
            r.set(right - tw - pad * 2, top, right, top + dp(46f))
            label.color = 0xF2F6F1E7.toInt()
            label.alpha = (alpha * 0.95f).toInt()
            label.setShadowLayer(dp(8f), 0f, dp(2f), 0x66000000)
            c.drawRoundRect(r, dp(2f), dp(2f), label)
            title.textAlign = Paint.Align.LEFT
            byline.textAlign = Paint.Align.LEFT
            title.color = 0xFF2A2622.toInt()
            byline.color = 0xFF6B6257.toInt()
            title.clearShadowLayer()
            byline.clearShadowLayer()
            title.alpha = alpha
            byline.alpha = alpha
            c.drawText(t, r.left + pad, top + dp(20f), title)
            c.drawText(by, r.left + pad, top + dp(35f), byline)
            return
        }
        title.textAlign = align
        byline.textAlign = align
        title.alpha = (alpha * 0.9f).toInt()
        byline.alpha = (alpha * 0.7f).toInt()
        c.drawText(t, tx, ty, title)
        c.drawText(w.byline.uppercase(java.util.Locale.getDefault()), tx, ty + dp(13f), byline)
    }

    private fun dp(v: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    }

    companion object {
        private const val CHANGE_MS = 90_000L
        private const val FADE_MS = 2_000L
        private const val LIST_TTL = 7L * 24 * 3600_000
        private const val LIST_URL = ("https://api.artic.edu/api/v1/artworks/search"
                + "?query%5Bbool%5D%5Bmust%5D%5B0%5D%5Bterm%5D%5Bis_public_domain%5D=true"
                + "&query%5Bbool%5D%5Bmust%5D%5B1%5D%5Bterm%5D%5Bis_boosted%5D=true"
                + "&query%5Bbool%5D%5Bmust%5D%5B2%5D%5Bterm%5D%5Bartwork_type_title.keyword%5D=Painting"
                + "&fields=title,artist_title,date_display,image_id,thumbnail&limit=100")

        @Throws(Exception::class)
        private fun download(url: String): Bitmap {
            val h = URL(url).openConnection() as HttpURLConnection
            h.connectTimeout = 10_000
            h.readTimeout = 20_000
            h.setRequestProperty("User-Agent", "AuroraHome/3 (personal TV screensaver)")
            try {
                h.inputStream.use { inp ->
                    val b = BitmapFactory.decodeStream(inp) ?: throw Exception("not an image")
                    return b
                }
            } finally {
                h.disconnect()
            }
        }

        private fun fit(s0: String, p: Paint, max: Float): String {
            var s = s0
            if (p.measureText(s) <= max) return s
            while (s.length > 4 && p.measureText(s + "…") > max) s = s.substring(0, s.length - 1)
            return s.trim { it <= ' ' } + "…"
        }
    }
}
