package home.aurora

import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Date
import java.util.Random

/**
 * Screensaver: either the wallpapers (changing every minute, with a clock that
 * wanders to avoid burn-in) or art mode (famous paintings, small fixed clock).
 */
class AuroraDream : DreamService() {

    private val h = Handler(Looper.getMainLooper())
    private val rnd = Random()
    private var wall: WallpaperView? = null
    private var art: ArtView? = null
    private lateinit var root: FrameLayout
    private var artMode = false
    private lateinit var block: LinearLayout
    private lateinit var clock: FlipClock
    private lateinit var date: TextView

    private val tick: Runnable = object : Runnable {
        override fun run() {
            val now = Date()
            val h24 = android.text.format.DateFormat.is24HourFormat(this@AuroraDream)
            clock.setTime(HomeActivity.flipTime(now, h24))
            date.text = Room.fmt("EEEE, MMMM d", now)   // kept formatter, rebuilt if the language changes
            h.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000)
        }
    }

    private val wander: Runnable = object : Runnable {
        override fun run() {
            val p = block.parent as FrameLayout
            val maxX = (p.width - block.width).toFloat()
            val maxY = (p.height - block.height).toFloat()
            if (maxX > 0 && maxY > 0) {
                block.animate().alpha(0f).setDuration(1500).withEndAction {
                    block.translationX = rnd.nextFloat() * maxX
                    block.translationY = rnd.nextFloat() * maxY
                    block.animate().alpha(1f).setDuration(1500).start()
                }.start()
            }
            h.postDelayed(this, 90_000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false

        root = FrameLayout(this)
        artMode = Prefs.of(this).getBoolean("dream_art", false)
        if (artMode) {
            val art = ArtView(this)
            this.art = art
            art.setFrameMs(DRIFT_MS)
            art.setListener { fallBackToWallpapers() }   // offline: show wallpapers instead
            root.addView(art, FrameLayout.LayoutParams(-1, -1))
        } else {
            addWallpapers()
        }

        block = LinearLayout(this)
        block.orientation = LinearLayout.VERTICAL
        block.gravity = Gravity.START
        val pad = dp(48f)
        block.setPadding(pad, pad, pad, pad)
        clock = FlipClock(this, if (artMode) 34f else 72f)
        date = TextView(this)
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (artMode) 12f else 16f)
        date.setTextColor(0xB3FFFFFF.toInt())
        block.addView(clock)
        date.setPadding(dp(4f), dp(10f), 0, 0)
        block.addView(date)
        if (artMode) {
            // Art fills the screen on its own; no clock.
        } else {
            root.addView(block, FrameLayout.LayoutParams(-2, -2))
        }
        setContentView(root)
    }

    private fun addWallpapers() {
        val wall = WallpaperView(this)
        this.wall = wall
        wall.setIntervalMs(60_000)
        wall.setSpeed(0.7f)
        wall.setFrameMs(DRIFT_MS)
        root.addView(wall, 0, FrameLayout.LayoutParams(-1, -1))
        val shade = View(this)
        shade.setBackgroundColor(0x40000000)
        root.addView(shade, 1, FrameLayout.LayoutParams(-1, -1))
    }

    private fun fallBackToWallpapers() {
        art?.let { it.stop(); root.removeView(it); art = null }
        addWallpapers()
        wall!!.start()
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val art = art
        if (art != null) art.start() else wall!!.start()
        h.post(tick)
        if (!artMode) h.postDelayed(wander, 5_000)
    }

    override fun onDreamingStopped() {
        h.removeCallbacksAndMessages(null)
        art?.stop()
        wall?.stop()
        super.onDreamingStopped()
    }

    private fun dp(v: Float): Int {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics))
    }

    companion object {
        // The drift and zoom are so slow that ten frames a second looks the same, for a third of the work.
        private const val DRIFT_MS = 100L
    }
}
