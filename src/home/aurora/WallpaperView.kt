package home.aurora

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import java.lang.reflect.Field
import java.util.ArrayList

/**
 * Cycles the bundled wallpapers with a slow Ken Burns drift and a crossfade.
 * Decoding happens off the main thread. Also hands out its current transform and
 * a pre-blurred copy of the image so [GlassView] can fake a backdrop blur.
 */
open class WallpaperView
/** @param prefix which bundled set: "wp_" Apple wallpapers, "sp_" NASA space photos. */
constructor(c: Context, prefix: String) : View(c) {

    open class Frame(full: Bitmap, blur: Bitmap, title: String, seed: Int) {
        @JvmField val full: Bitmap
        @JvmField val blur: Bitmap
        @JvmField val title: String
        @JvmField val born = SystemClock.uptimeMillis()
        @JvmField val driftX: Float
        @JvmField val driftY: Float
        @JvmField var still = false

        init {
            this.full = full
            this.blur = blur
            this.title = title
            driftX = (if (seed % 2 == 0) 1 else -1) * 0.5f
            driftY = (if (seed % 3 == 0) 1 else -1) * 0.5f
        }
    }

    fun interface Listener { fun onWallpaper(title: String) }

    private val full: MutableList<Int> = ArrayList()
    private val blur: MutableList<Int> = ArrayList()
    private val titles: MutableList<String> = ArrayList()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val m = Matrix()
    private val main = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences
    private val followers: MutableList<View> = ArrayList()
    private var cur: Frame? = null
    private var prev: Frame? = null
    private val indexKey: String
    private var painted: android.graphics.drawable.Drawable? = null
    private var index: Int
    private var intervalMs = 5 * 60_000L
    private var speed = 1f
    private var running = false
    private var wanted = false   // start() was asked for; it runs while we're attached
    private var loading = false
    private var frameMs = FRAME_MS
    private var listener: Listener? = null
    private var parallax = 0f
    private var parallaxTarget = 0f   // px

    private val advance = Runnable { next() }

    constructor(c: Context) : this(c, "wp_")

    init {
        prefs = Prefs.of(c)
        indexKey = if (prefix == "wp_") "wp" else "wp_$prefix"
        load(prefix)
        // The Apple wallpapers aren't in the public source; a copy built without them shows the NASA photos.
        if (full.isEmpty() && prefix == "wp_") load("sp_")
        index = prefs.getInt(indexKey, 0) % Math.max(full.size, 1)
    }

    /** Every bundled photo whose name starts with [prefix], with its pre-blurred copy. */
    private fun load(prefix: String) {
        for (f: Field in R.drawable::class.java.fields) {
            val n = f.name
            if (!n.startsWith(prefix) || n.endsWith("_blur")) continue
            try {
                full.add(f.getInt(null))
                blur.add(R.drawable::class.java.getField(n + "_blur").getInt(null))
                titles.add(title(n.substring(prefix.length)))
            } catch (ignored: ReflectiveOperationException) { }
        }
    }

    /**
     * Show a painted background instead of photos. It stays still (no drift),
     * and glass panels still get a blurred copy of it.
     */
    fun setPainted(d: android.graphics.drawable.Drawable?) { painted = d }

    private fun paint() {
        val w = width
        val h = height
        if (w == 0 || h == 0) return
        val f = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        painted!!.setBounds(0, 0, w, h)
        painted!!.draw(Canvas(f))
        val small = Bitmap.createScaledBitmap(f, Math.max(1, w / 4), Math.max(1, h / 4), true)
        val b = Bitmap.createScaledBitmap(small, Math.max(1, w / 16), Math.max(1, h / 16), true)
        val fr = Frame(f, b, "", 0)
        cur = fr
        fr.still = true
        prev = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (painted != null) paint()
    }

    fun setListener(l: Listener?) { listener = l }
    fun setIntervalMs(ms: Long) { intervalMs = ms; if (running) scheduleAdvance() }
    fun setSpeed(s: Float) { speed = s }

    /** Time between frames of the drift (the crossfade stays smooth); the screensaver asks for less. */
    fun setFrameMs(ms: Long) { frameMs = Math.max(FRAME_MS, ms) }

    /** Shift the wallpaper slightly against focus movement; -1..1. */
    fun setParallax(amount: Float) {
        parallaxTarget = amount * resources.displayMetrics.density * 14
    }

    /** Views whose drawing depends on our transform (glass panels) get redrawn with us. */
    fun addFollower(v: View) { if (!followers.contains(v)) followers.add(v) }
    fun removeFollower(v: View) { followers.remove(v) }

    /**
     * Start drifting and changing. Until we're attached to a window this only notes
     * the request (and onAttachedToWindow starts it), so a wallpaper nobody shows
     * never decodes or draws.
     */
    fun start() {
        wanted = true
        if (!isAttachedToWindow) return
        running = true
        if (painted != null) { invalidate(); return }
        if (cur == null && off(context).contains(if (titles.isEmpty()) "" else titles[index])) next()
        else if (cur == null) load(index)
        else scheduleAdvance()
        invalidate()
    }

    fun stop() {
        wanted = false
        halt()
    }

    private fun halt() {
        running = false
        main.removeCallbacks(advance)
        prev = null   // no crossfade is coming; let GC have it
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (wanted && !running) start()
    }

    override fun onDetachedFromWindow() {
        halt()   // still wanted: attaching again picks up where it left off
        super.onDetachedFromWindow()
    }

    fun titles(): MutableList<String> = ArrayList(titles)

    fun next() {
        val off = off(context)
        for (k in 0 until full.size) {   // skip switched-off ones (all off = show them all)
            index = (index + 1) % full.size
            if (!off.contains(titles[index])) break
        }
        if (painted != null) return
        prefs.edit().putInt(indexKey, index).apply()
        load(index)
    }

    private fun scheduleAdvance() {
        main.removeCallbacks(advance)
        if (running && intervalMs > 0) main.postDelayed(advance, intervalMs)   // 0 = never change
    }

    private fun load(i: Int) {
        if (loading || full.isEmpty()) return
        loading = true
        val resFull = full[i]
        val resBlur = blur[i]
        val t = titles[i]
        // just enough pixels to cover the screen at the drift's deepest zoom
        val dm = resources.displayMetrics
        val tw = (Math.max(width, dm.widthPixels) * 1.12f).toInt()
        val th = (Math.max(height, dm.heightPixels) * 1.12f).toInt()
        Thread({
            var f: Bitmap? = null
            var b: Bitmap? = null
            try {
                f = Bitmaps.res(resources, resFull, tw, th)
                if (f != null) b = Bitmaps.res(resources, resBlur, 0, 0)
            } catch (e: Throwable) {
                // out of memory and the like: keep what's showing
            } finally {
                val full = f
                val soft = b
                main.post { loaded(full, soft, t, i) }
            }
        }, "wallpaper").start()
    }

    private fun loaded(f: Bitmap?, b: Bitmap?, t: String, i: Int) {
        loading = false
        if (f == null || b == null) { scheduleAdvance(); return }   // try the next one later
        prev = if (running) cur else null
        cur = Frame(f, b, t, i)
        listener?.onWallpaper(t)
        scheduleAdvance()
        invalidate()
    }

    /** Matrix mapping a frame's full-size bitmap onto this view right now. */
    private fun transform(fr: Frame, out: Matrix) {
        val w = width
        val h = height
        val base = Math.max(w / fr.full.width.toFloat(), h / fr.full.height.toFloat())
        if (fr.still) { out.setScale(base, base); return }
        var t = ((SystemClock.uptimeMillis() - fr.born) * speed % (2 * KEN_BURNS_MS)) / KEN_BURNS_MS
        t = if (t > 1) 2 - t else t                  // ping-pong 0..1..0
        t = t * t * (3 - 2 * t)                      // ease
        val zoom = base * (1.04f + 0.08f * t)
        val bw = fr.full.width * zoom
        val bh = fr.full.height * zoom
        val slackX = bw - w
        val slackY = bh - h
        out.setScale(zoom, zoom)
        val tx = -slackX * (0.5f + fr.driftX * (t - 0.5f)) + parallax
        out.postTranslate(Math.max(-slackX, Math.min(0f, tx)), -slackY * (0.5f + fr.driftY * (t - 0.5f)))
    }

    /** Alpha (0..255) of the current frame during a crossfade. */
    private fun fade(): Int {
        val cur = cur ?: return 0
        val age = SystemClock.uptimeMillis() - cur.born
        return if (prev == null) 255 else (255 * Math.min(1f, age / FADE_MS.toFloat())).toInt()
    }

    /**
     * Draw the blurred backdrop for a glass panel located at (left, top) in our
     * coordinate space, matching exactly what's behind it.
     */
    fun drawBackdrop(c: Canvas, left: Float, top: Float) {
        val a = fade()
        val prev = prev
        val cur = cur
        if (prev != null && a < 255) drawBlur(c, prev, left, top, 255)
        if (cur != null) drawBlur(c, cur, left, top, a)
    }

    private val bm = Matrix()
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun drawBlur(c: Canvas, fr: Frame, left: Float, top: Float, alpha: Int) {
        transform(fr, bm)
        val sx = fr.full.width / fr.blur.width.toFloat()
        bm.preScale(sx, fr.full.height / fr.blur.height.toFloat())
        bm.postTranslate(-left, -top)
        bp.alpha = alpha
        c.drawBitmap(fr.blur, bm, bp)
    }

    override fun onDraw(c: Canvas) {
        parallax += (parallaxTarget - parallax) * 0.08f   // ease toward the target each frame
        c.drawColor(0xFF000000.toInt())
        val a = fade()
        val p = prev
        if (p != null && a < 255) {
            transform(p, m)
            paint.alpha = 255
            c.drawBitmap(p.full, m, paint)
        } else if (p != null && a == 255) {
            prev = null // let GC reclaim it; recycling could race the render thread
        }
        val cu = cur
        if (cu != null) {
            transform(cu, m)
            paint.alpha = a
            c.drawBitmap(cu.full, m, paint)
        }
        for (f in followers) f.invalidate()
        // After a few minutes without a button press nobody's watching closely: half the frame rate.
        // The crossfade always gets full speed.
        val ms = if (p != null) FRAME_MS else Math.max(frameMs, if (idle()) FRAME_MS * 2 else FRAME_MS)
        if (running && (cur == null || !cur!!.still)) postInvalidateDelayed(ms)
    }

    companion object {
        private const val KEN_BURNS_MS = 90_000L
        private const val FADE_MS = 1_800L
        private const val FRAME_MS = 33L
        private const val IDLE_MS = 5 * 60_000L
        @Volatile private var lastInput = SystemClock.uptimeMillis()

        /** A button was pressed: back to the full frame rate. */
        @JvmStatic
        fun input() { lastInput = SystemClock.uptimeMillis() }

        /** No button pressed for a few minutes: animations can drop to a lower frame rate. */
        @JvmStatic
        fun idle(): Boolean = SystemClock.uptimeMillis() - lastInput > IDLE_MS

        private fun title(slug: String): String {
            val b = StringBuilder()
            for (w in slug.split("_".toRegex()).dropLastWhile { it.isEmpty() }) b.append(if (b.length > 0) " " else "").append(Character.toUpperCase(w[0])).append(w.substring(1))
            return b.toString()
        }

        /** Wallpapers switched off in Settings (by title). */
        @JvmStatic
        fun off(c: Context): MutableSet<String> {
            return java.util.HashSet(Prefs.of(c)
                    .getStringSet("wp_off", java.util.Collections.emptySet())!!)
        }
    }
}
