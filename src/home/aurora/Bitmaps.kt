package home.aurora

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors

/**
 * Decoding for photos and plates. Images come in no bigger than the screen needs
 * (a 720p TV never decodes a 1080p plate), and running out of memory gives null
 * instead of taking the launcher down. Room plates also stay in a small cache, so
 * rebuilding a room (a theme change, coming back from an app) doesn't decode them again.
 */
object Bitmaps {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "plates").also { it.isDaemon = true } }
    private val pending = HashMap<String, MutableList<(Bitmap) -> Unit>>()   // main thread only

    // An eighth of the heap, at most 32 MB: a room's plates and the window's Earth.
    private val cache = object : LruCache<String, Bitmap>(Math.min(Runtime.getRuntime().maxMemory() / 8, 32L shl 20).toInt() / 1024) {
        override fun sizeOf(key: String, b: Bitmap): Int = Math.max(1, b.allocationByteCount / 1024)
    }

    /**
     * Decode options for a `srcW` × `srcH` image that only has to cover `w` × `h`
     * (0 = don't care): a power-of-two sample size first, then the decoder scales
     * the rest of the way. `opaque` decodes to RGB_565 (half the memory, no alpha).
     */
    private fun options(srcW: Int, srcH: Int, w: Int, h: Int, opaque: Boolean): BitmapFactory.Options {
        val o = BitmapFactory.Options()
        o.inScaled = false
        if (opaque) o.inPreferredConfig = Bitmap.Config.RGB_565
        if (srcW <= 0 || srcH <= 0 || (w <= 0 && h <= 0)) return o
        var s = 1
        while ((w <= 0 || srcW / (s * 2) >= w) && (h <= 0 || srcH / (s * 2) >= h)) s *= 2
        o.inSampleSize = s
        val sw = srcW / s
        val sh = srcH / s
        val scale = Math.max(if (w > 0) w / sw.toFloat() else 0f, if (h > 0) h / sh.toFloat() else 0f)
        if (scale < 0.95f) {
            o.inScaled = true
            o.inDensity = sw
            o.inTargetDensity = Math.ceil((sw * scale).toDouble()).toInt()
        }
        return o
    }

    private inline fun safe(f: () -> Bitmap?): Bitmap? {
        return try {
            val b = f()
            b?.density = Bitmap.DENSITY_NONE   // decode-time scaling stamps a density; we draw by pixels
            b
        } catch (e: Throwable) {   // out of memory included: no picture beats no launcher
            null
        }
    }

    /** A bundled image just big enough to cover `w` × `h` (0 = don't care); null if it can't be decoded. */
    @JvmStatic
    @JvmOverloads
    fun res(r: Resources, id: Int, w: Int, h: Int, opaque: Boolean = false): Bitmap? = safe {
        val b = BitmapFactory.Options()
        b.inJustDecodeBounds = true
        b.inScaled = false
        BitmapFactory.decodeResource(r, id, b)
        BitmapFactory.decodeResource(r, id, options(b.outWidth, b.outHeight, w, h, opaque))
    }

    /** Same, from a file. */
    @JvmStatic
    fun file(path: String, w: Int, h: Int): Bitmap? = safe {
        val b = BitmapFactory.Options()
        b.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, b)
        BitmapFactory.decodeFile(path, options(b.outWidth, b.outHeight, w, h, false))
    }

    /** Same, from downloaded bytes. */
    @JvmStatic
    fun bytes(data: ByteArray, w: Int, h: Int): Bitmap? = safe {
        val b = BitmapFactory.Options()
        b.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(data, 0, data.size, b)
        BitmapFactory.decodeByteArray(data, 0, data.size, options(b.outWidth, b.outHeight, w, h, false))
    }

    /**
     * A room's bundled plate, sized to cover `w` × `h` and cached across rebuilds.
     * Cached: returned at once. Otherwise null now; it's decoded off the main thread and
     * handed to `ready` on the main thread (never, if it can't be decoded). Main thread
     * only. The bitmap is shared: never recycle it.
     */
    @JvmStatic
    fun plate(r: Resources, id: Int, w: Int, h: Int, opaque: Boolean, ready: (Bitmap) -> Unit): Bitmap? {
        val key = "$id/$w/$h/$opaque"
        cache.get(key)?.let { return it }
        val waiting = pending[key]
        if (waiting != null) { waiting.add(ready); return null }
        pending[key] = mutableListOf(ready)
        worker.execute {
            val b = res(r, id, w, h, opaque)
            main.post {
                val all = pending.remove(key) ?: return@post
                if (b == null) return@post
                cache.put(key, b)
                for (f in all) f(b)
            }
        }
        return null
    }
}
