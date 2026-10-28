package home.aurora

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * The morning screen: dawn sky, the time, the weather and today's calendar.
 * Any button keeps it on; OK or Back goes to the home screen. If it turned the
 * TV on and nobody touches anything, it puts the TV back to sleep.
 */
class MorningActivity : Activity() {

    companion object {
        private const val STAY_MS = 15 * 60_000L

        /** Showing right now (Habits won't reopen the last app over it). */
        @JvmField @Volatile var visible = false
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var face: Face
    private var woke = false
    private var touched = false
    private var weather = ""
    private var weatherSub = ""
    private var events: List<Morning.Event> = emptyList()
    private var calendarNote = ""

    private val tick = object : Runnable {
        override fun run() {
            face.invalidate()
            main.postDelayed(this, 20_000)
        }
    }

    private val backToSleep = Runnable {
        if (touched) return@Runnable
        AuroraService.instance?.powerOff()
        finish()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        // Going back to sleep needs the background service; without it, just stay up.
        woke = intent.getBooleanExtra("woke", false) && AuroraService.instance != null
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        face = Face()
        setContentView(face)
        Weather.load(this) { i ->
            if (i == null) {
                weather = ""
                weatherSub = "Weather unavailable"
            } else {
                weather = "${i.temp}  ${i.desc}"
                weatherSub = i.hilo + if (i.place.isNullOrEmpty()) "" else "  ·  ${i.place}"
            }
            face.invalidate()
        }
        val hasCalendar = Morning.prefs(this).getString("cal_url", "")!!.isNotBlank()
        calendarNote = if (hasCalendar) "Loading your calendar…" else "Add a calendar in Settings → Morning screen"
        if (hasCalendar) Thread({
            val (got, note) = try {
                Morning.today(this).let { it to if (it.isEmpty()) "Nothing on the calendar today" else "" }
            } catch (e: Exception) {
                emptyList<Morning.Event>() to "Couldn't load the calendar"
            }
            main.post { events = got; calendarNote = note; face.invalidate() }
        }, "calendar").start()
        if (woke) main.postDelayed(backToSleep, STAY_MS)
    }

    override fun onResume() {
        super.onResume()
        visible = true
        main.post(tick)
    }

    override fun onPause() {
        visible = false
        main.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        if (!touched) {
            touched = true
            main.removeCallbacks(backToSleep)
            face.invalidate()
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_BACK) {
            startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return true
        }
        return code in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT || super.onKeyDown(code, e)
    }

    /** The whole screen, drawn on a 960 × 540 design stage. */
    private inner class Face : View(this@MorningActivity) {
        private var sky: Bitmap? = null
        private var k = 1f

        private fun text(size: Float, color: Int, family: String, spacing: Float = 0f) =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textSize = size
                this.color = color
                typeface = Typeface.create(family, Typeface.NORMAL)
                letterSpacing = spacing
            }

        // Made once; the screen only redraws every 20 seconds or when something arrives.
        private val small = text(11f, 0xE6FFF4E0.toInt(), "sans-serif-medium", 0.35f)
        private val big = text(150f, 0xFFFFFFFF.toInt(), "sans-serif-thin").apply { setShadowLayer(12f, 0f, 4f, 0x55000000) }
        private val dateText = text(26f, 0xFFFFF4E0.toInt(), "serif")
        private val weatherText = text(22f, 0xFFFFFFFF.toInt(), "sans-serif-light")
        private val weatherSmall = text(14f, 0xCCFFF4E0.toInt(), "sans-serif")
        private val head = text(12f, 0xE6FFF4E0.toInt(), "sans-serif-medium", 0.3f)
        private val row = text(17f, 0xFFFFFFFF.toInt(), "sans-serif")
        private val timeText = text(15f, 0xCCFFF4E0.toInt(), "sans-serif-medium")
        private val note = text(15f, 0xCCFFF4E0.toInt(), "sans-serif-light")
        private val hint = text(13f, 0xCCFFF4E0.toInt(), "sans-serif", 0.08f)
        private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
        private val card = RectF(560f, 70f, 900f, 360f)

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            k = minOf(w / 960f, h / 540f)
            sky?.recycle()
            sky = Bitmap.createBitmap(maxOf(1, w), maxOf(1, h), Bitmap.Config.ARGB_8888).also {
                val c = Canvas(it)
                c.scale(w / 960f, h / 540f)
                dawn(c)
            }
        }

        private fun dawn(c: Canvas) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, 0f, 430f, intArrayOf(0xFF22305A.toInt(), 0xFF5A5A8C.toInt(), 0xFFD98E7A.toInt(), 0xFFF5C98A.toInt()),
                floatArrayOf(0f, 0.45f, 0.8f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, 960f, 540f, p)
            p.shader = RadialGradient(720f, 420f, 360f, intArrayOf(0xAAFFE6B0.toInt(), 0x33FFC890, 0x00FFC890), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, 960f, 540f, p)
            p.shader = null
            p.color = 0xFFFFF0CC.toInt()
            c.drawCircle(720f, 420f, 30f, p)
            val hills = intArrayOf(0xFF7A6A8C.toInt(), 0xFF4E4A6A.toInt(), 0xFF2E2E48.toInt())
            val tops = floatArrayOf(400f, 440f, 480f)
            for (i in 0 until 3) {
                val h = Path()
                h.moveTo(0f, 540f)
                for (x in 0..960 step 10) h.lineTo(x.toFloat(), tops[i] + (sin(x / 960.0 * PI * (2 + i) + i * 1.7) * 18).toFloat())
                h.lineTo(960f, 540f)
                h.close()
                p.color = hills[i]
                c.drawPath(h, p)
            }
            p.shader = LinearGradient(0f, 0f, 520f, 0f, 0x55000000, 0x00000000, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, 960f, 540f, p)
        }

        override fun onDraw(c: Canvas) {
            sky?.let { c.drawBitmap(it, 0f, 0f, null) }
            c.save()
            c.translate((width - 960 * k) / 2, (height - 540 * k) / 2)
            c.scale(k, k)
            val now = Date()
            val greeting = HomeTheme.current(this@MorningActivity).greeting
            c.drawText(if (greeting == null) "GOOD MORNING" else "GOOD MORNING  ·  " + greeting.uppercase(Locale.getDefault()), 60f, 90f, small)
            val h24 = DateFormat.is24HourFormat(this@MorningActivity)
            c.drawText(SimpleDateFormat(if (h24) "H:mm" else "h:mm", Locale.getDefault()).format(now), 50f, 240f, big)
            c.drawText(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now), 62f, 290f, dateText)
            c.drawText(weather, 62f, 340f, weatherText)
            c.drawText(weatherSub, 62f, 364f, weatherSmall)

            // today's calendar
            c.drawRoundRect(card, 22f, 22f, cardPaint)
            c.drawText("TODAY", card.left + 26, card.top + 38, head)
            var y = card.top + 76
            if (events.isEmpty()) c.drawText(calendarNote, card.left + 26, y, note)
            val tf = SimpleDateFormat(if (h24) "H:mm" else "h:mm a", Locale.getDefault())
            for (e in events.take(6)) {
                c.drawText(if (e.allDay) "All day" else tf.format(Date(e.start)).lowercase(Locale.getDefault()), card.left + 26, y, timeText)
                c.drawText(Room.fit(e.title, row, card.width() - 150), card.left + 120, y, row)
                y += 40
            }
            if (events.size > 6) c.drawText("+ ${events.size - 6} more", card.left + 26, y, timeText)

            val h = if (woke && !touched) "Press OK to start your day  ·  the TV goes back to sleep in 15 minutes if untouched"
            else "Press OK for the home screen"
            c.drawText(h, 62f, 510f, hint)
            c.restore()
        }
    }
}
