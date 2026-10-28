package home.aurora

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Calendar
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Long-lived helper that keeps working while other apps are on screen:
 *  - night mode: a warm dimming overlay during set hours
 *  - sleep timer: puts the TV to sleep, with a 60 s "press any button" warning
 *  - timer and stopwatch: a small pill in the corner over any app
 *  - phone remote: hosts [RemoteServer] and carries out its commands
 *  - [Habits]: per-app volume, safe startup volume, reopening the last app
 *  - bedtime: turns the TV off at a set time each night
 *  - wind-down: the last minutes of a sleep timer fade the volume and dim the screen
 *  - morning screen: wakes the TV to the weather and the day ([Morning])
 *  - kid lock: locked apps ask for a PIN, however they're opened
 */
class AuroraService : AccessibilityService() {

    companion object {
        private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        private const val EXTRA_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
        private const val EXTRA_PREV = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"
        /** Fastest a held volume button may move: one step per this many ms. */
        private const val VOL_STEP_MS = 170L
        @JvmField @Volatile var instance: AuroraService? = null
        const val PORT = 8765
        private const val WIND_MS = 5 * 60_000L   // wind-down: the last minutes of a sleep timer
        private const val UNLOCK_MS = 2 * 3_600_000L   // an unlocked app asks for the PIN again after this

        /** "4:05" or "1:02:03" for a number of seconds. */
        @JvmStatic
        fun clock(s: Long): String {
            val h = s / 3600
            val m = s / 60 % 60
            val sec = s % 60
            return (if (h > 0) "$h:" + (if (m < 10) "0" else "") else "") + "$m:" + (if (sec < 10) "0" else "") + sec
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private var server: RemoteServer? = null
    @JvmField @Volatile var habits: Habits? = null   // read by the phone remote's worker threads

    private val audio get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val unlocked = HashMap<String, Long>()   // pkg → when (elapsedRealtime); until sleep or UNLOCK_MS
    private var night: View? = null
    private var banner: View? = null
    private var timerView: TextView? = null
    private var timerBox: FrameLayout? = null   // gives the pulsing pill room inside its window
    // Read by the phone remote's worker threads too, so volatile.
    @Volatile private var timerEnd = 0L   // elapsedRealtime; 0 = not running
    @Volatile private var swStart = 0L
    @Volatile private var ringing = false
    @Volatile private var sleepAt = 0L    // elapsedRealtime, 0 = off
    @Volatile private var warning = false
    private var frontClass: String? = null   // screen in front, from window events
    private var tone: ToneGenerator? = null

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF) { unlocked.clear(); return }
            habits?.onWake()
        }
    }

    /** The morning alarm is set in wall-clock time: re-arm it when the clock or time zone changes. */
    private val clockChanged = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = Morning.schedule(c)
    }

    private val packages = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = Apps.invalidate()
    }

    private val timerTick: Runnable = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            when {
                timerEnd > 0 -> {
                    val left = timerEnd - now
                    if (left <= 0) { ring(); return }
                    showTimer("Timer   " + clock((left + 999) / 1000), false)
                }
                swStart > 0 -> showTimer("Stopwatch   " + clock((now - swStart) / 1000), false)
                else -> return
            }
            main.postDelayed(this, 250)
        }
    }

    private val beep: Runnable = object : Runnable {
        override fun run() {
            if (!ringing) return
            try {
                (tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 90).also { tone = it }).startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            } catch (ignored: RuntimeException) { }
            main.postDelayed(this, 1200)
        }
    }

    private val stopRinging = Runnable { dismissTimer() }

    private val nightTick: Runnable = object : Runnable {
        override fun run() {
            updateNight()
            checkBedtime()
            main.postDelayed(this, 30_000)
        }
    }

    private val sleepWarn = Runnable {
        warning = true
        updateKeyFilter()
        showBanner("Sleeping in 1 minute  ·  press any button to cancel", 60_000)
    }

    private val sleepNow = Runnable {
        warning = false
        sleepAt = 0
        updateKeyFilter()
        hideBanner()
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        // Off now: put the volume back so the next wake isn't oddly quiet (safe start still caps it).
        main.postDelayed({ endWindDown(true) }, 1500)
    }

    // wind-down state
    private var windFrom = 0L          // elapsedRealtime the fade began, 0 = not winding down
    private var windVol = -1
    private var windSet = -1
    private var windUserVolume = false // the viewer turned it back up: leave the volume alone
    private var dim: View? = null

    private val windStart = Runnable {
        windFrom = SystemClock.elapsedRealtime()
        windVol = music()
        windSet = windVol
        windUserVolume = false
        dim = View(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            alpha = 0f
            addOverlay(this, full())
        }
        main.post(::windTick)
    }

    override fun onServiceConnected() {
        instance = this
        updateKeyFilter()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = Prefs.of(this)
        if (remoteEnabled()) setRemote(true)
        main.post(nightTick)
        val h = Habits(this).also { habits = it }
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) })
        registerReceiver(clockChanged, IntentFilter(Intent.ACTION_TIME_CHANGED).apply { addAction(Intent.ACTION_TIMEZONE_CHANGED) })
        registerReceiver(volumeChanged, IntentFilter(VOLUME_CHANGED))
        registerReceiver(packages, IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        // Also after a full power-on (but not when Aurora itself was just updated).
        if (SystemClock.elapsedRealtime() < 3 * 60_000) main.postDelayed({ h.onWake() }, 4000)
        Morning.schedule(this)
        Log.i("Aurora", "service up, remote on :$PORT")
    }

    override fun onDestroy() {
        instance = null
        for (r in listOf(screenOn, clockChanged, packages, volumeChanged)) {
            try { unregisterReceiver(r) } catch (ignored: IllegalArgumentException) { }
        }
        server?.stop()
        main.removeCallbacksAndMessages(null)
        removeOverlay(night)
        removeOverlay(dim)
        removeOverlay(banner)
        dismissTimer()
        tone?.release()
        adbLocal?.shutdown()
        flagThread?.quitSafely()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        // Window, focus and scroll events also come from autoplay and moving carousels, so they
        // don't count. Key presses do (see onKeyEvent); a click (OK pressed) is a safe backup.
        val t = e.eventType
        if (t == AccessibilityEvent.TYPE_VIEW_CLICKED && (warning || ringing)) userActive()
        if (t != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val cls = e.className?.toString()
        val pkg = e.packageName
        val ours = pkg != null && packageName.contentEquals(pkg)
        // Our own banners, timer pill and menus also report window changes under our
        // package; they aren't the home screen coming forward, so they must not switch
        // the per-app volume or count as an app opening.
        val activity = cls != null && "Activity" in cls
        if (ours && !activity) return
        habits?.onWindow(pkg)
        checkLock(pkg)
        // Only full screens count (dialogs and our own overlays report other classes).
        if (activity) frontClass = cls else if (pkg != null && !ours) frontClass = null
    }

    override fun onInterrupt() {}

    // ------------------------------------------------------------------ key filtering

    private var flagThread: HandlerThread? = null
    private var flags: Handler? = null

    /**
     * Key filtering is only switched on while the sleep warning or the alarm is up.
     * While a service filters keys, Android stops repeating held buttons for every
     * app (holding volume or the D-pad moves just one step), so it stays off otherwise.
     */
    private fun updateKeyFilter() {
        val on = warning || ringing
        // Off the main thread: the system may be waiting on our main thread to answer a key
        // event while this call waits on the system, which froze the TV in testing.
        val f = flags ?: HandlerThread("aurora-keys").let { t ->
            t.start()
            flagThread = t
            Handler(t.looper).also { flags = it }
        }
        f.post { applyKeyFilter(on) }
    }

    private fun applyKeyFilter(on: Boolean) {
        val info = serviceInfo ?: return
        val flag = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        val want = if (on) info.flags or flag else info.flags and flag.inv()
        if (want == info.flags) return
        info.flags = want
        serviceInfo = info
    }

    /** Something was pressed on the phone remote: counts like a button on the TV remote. */
    fun userActive() {
        main.post {
            if (ringing) dismissTimer()
            cancelSleepByUser()
        }
    }

    /** Any button during the sleep warning cancels the timer and is swallowed. */
    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (ringing) {
            if (e.action == KeyEvent.ACTION_UP) main.post(::dismissTimer)
            return true
        }
        if (!warning) return false
        if (e.action == KeyEvent.ACTION_UP) main.post(::cancelSleepByUser)
        return true
    }

    // ------------------------------------------------------------------ gentle volume

    /**
     * Holding volume on the TV remote repeats very fast, so the volume races. Android
     * doesn't let an app slow one button's repeat (filtering keys stops every button
     * repeating), so instead a one-step change that comes sooner than [VOL_STEP_MS]
     * after the last is put back: a held button climbs steadily, about six steps a
     * second. Single presses and bigger jumps (per-app volume, wind down) are untouched.
     */
    private var volKept = -1
    private var volAt = 0L

    private val volumeChanged = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.getIntExtra(EXTRA_STREAM, -1) != AudioManager.STREAM_MUSIC) return
            val v = i.getIntExtra(EXTRA_VALUE, -1)
            val prev = i.getIntExtra(EXTRA_PREV, -1)
            if (v < 0 || v == volKept) return
            val now = SystemClock.uptimeMillis()
            // A jump of more than one step in one go was set on purpose (per-app volume, wind down).
            if (volKept < 0 || prev < 0 || Math.abs(v - prev) > 1 || !gentleVolume() || audio.isVolumeFixed) {
                volKept = v
                volAt = now
                return
            }
            // A button step: at most one step per VOL_STEP_MS, measured from the last one let through,
            // so even broadcasts that arrive late can't let a held button race ahead.
            val want = if (now - volAt >= VOL_STEP_MS) volKept + Integer.signum(v - volKept) else volKept
            if (want != volKept) { volKept = want; volAt = now }
            if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) != volKept) audio.setStreamVolume(AudioManager.STREAM_MUSIC, volKept, 0)
        }
    }

    fun gentleVolume(): Boolean = prefs.getBoolean("gentle_volume", true)

    fun setGentleVolume(on: Boolean) = prefs.edit().putBoolean("gentle_volume", on).apply()

    /** Run on the main thread and wait for the result (for the server's worker threads). */
    fun <T> onMain(c: Callable<T>): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return c.call()
        val t = FutureTask(c)
        main.post(t)
        // A busy main thread mustn't tie up the remote's few workers forever.
        return t.get(5, TimeUnit.SECONDS)
    }

    // ------------------------------------------------------------------ night mode

    fun nightEnabled(): Boolean = prefs.getBoolean("night_on", true)
    fun nightStart(): Int = prefs.getInt("night_start", 23)
    fun nightEnd(): Int = prefs.getInt("night_end", 6)
    fun nightLevel(): Int = prefs.getInt("night_level", 35)   // percent

    fun setNight(on: Boolean, start: Int, end: Int, level: Int) {
        prefs.edit().putBoolean("night_on", on).putInt("night_start", start)
            .putInt("night_end", end).putInt("night_level", level.coerceIn(5, 70)).apply()
        removeOverlay(night)
        night = null
        updateNight()
    }

    fun nightActive(): Boolean {
        if (!nightEnabled()) return false
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val s = nightStart()
        val e = nightEnd()
        return if (s <= e) h in s until e else h >= s || h < e
    }

    private fun updateNight() {
        val want = nightActive()
        val current = night
        if (want && current == null) {
            night = View(this).apply {
                // Dark amber: dims and warms at the same time.
                val a = (nightLevel() / 100f * 255).roundToInt()
                setBackgroundColor((a shl 24) or 0x2A1200)
                alpha = 0f
                addOverlay(this, full())
                animate().alpha(1f).setDuration(4000).start()
            }
        } else if (!want && current != null) {
            night = null
            current.animate().alpha(0f).setDuration(2000).withEndAction { removeOverlay(current) }.start()
        }
    }

    // ------------------------------------------------------------------ bedtime

    fun bedtimeOn(): Boolean = prefs.getBoolean("bed_on", false)
    /** Minutes after midnight. */
    fun bedtime(): Int = prefs.getInt("bed_at", 23 * 60 + 30)

    fun setBedtime(on: Boolean, minutes: Int) {
        prefs.edit().putBoolean("bed_on", on).putInt("bed_at", minutes).remove("bed_done").apply()
    }

    /** At bedtime, start a 1-minute sleep timer (with its "press any button" warning). */
    private fun checkBedtime() {
        if (!bedtimeOn() || sleepAt != 0L) return
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val at = bedtime()
        if (now < at || now > at + 2) return
        val day = c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
        if (prefs.getInt("bed_done", 0) == day) return
        prefs.edit().putInt("bed_done", day).apply()
        if (!(getSystemService(POWER_SERVICE) as PowerManager).isInteractive) return
        setSleep(1)
    }

    // ------------------------------------------------------------------ kid lock

    fun kidLockOn(): Boolean = prefs.getBoolean("lock_on", false) && Pin.isSet(prefs)
    fun lockedApps(): MutableSet<String> = HashSet(prefs.getStringSet("locked", emptySet()) ?: emptySet())
    fun isLocked(pkg: String?): Boolean = pkg != null && kidLockOn() && pkg in lockedApps() && !stillUnlocked(pkg)
    fun unlock(pkg: String) { unlocked[pkg] = SystemClock.elapsedRealtime() }

    private fun stillUnlocked(pkg: String): Boolean {
        val at = unlocked[pkg] ?: return false
        if (SystemClock.elapsedRealtime() - at < UNLOCK_MS) return true
        unlocked.remove(pkg)
        return false
    }
    fun relock() = unlocked.clear()

    /** A locked app came to the front some other way (phone remote, "open last app"…): send it home for the PIN. */
    private fun checkLock(pkg: CharSequence?) {
        if (pkg == null || !isLocked(pkg.toString())) return
        launch(Intent(this, HomeActivity::class.java).putExtra("unlock", pkg.toString())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    // ------------------------------------------------------------------ phone remote

    fun remoteEnabled(): Boolean = prefs.getBoolean("remote_on", true)
    fun remote(): RemoteServer? = server

    fun setRemote(on: Boolean) {
        prefs.edit().putBoolean("remote_on", on).apply()
        server?.stop()
        server = if (on) RemoteServer(this, PORT).also { it.start() } else null
    }

    // ------------------------------------------------------------------ sleep timer and wind-down

    fun setSleep(minutes: Int) {
        main.removeCallbacks(sleepWarn)
        main.removeCallbacks(sleepNow)
        warning = false
        updateKeyFilter()
        hideBanner()
        endWindDown(true)
        if (minutes <= 0) { sleepAt = 0; return }
        val ms = minutes * 60_000L
        sleepAt = SystemClock.elapsedRealtime() + ms
        main.postDelayed(sleepWarn, maxOf(0, ms - 60_000))
        main.postDelayed(sleepNow, ms)
        if (windDownOn()) main.postDelayed(windStart, maxOf(0, ms - WIND_MS))
        showBanner("Sleep timer  ·  $minutes min", 3000)
    }

    fun windDownOn(): Boolean = prefs.getBoolean("wind_down", true)

    fun setWindDown(on: Boolean) = prefs.edit().putBoolean("wind_down", on).apply()

    private fun music(): Int = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

    /** Every 10 seconds: lower the volume toward 40% of where it started and darken the screen. */
    private fun windTick() {
        if (windFrom == 0L || sleepAt == 0L) return
        val now = SystemClock.elapsedRealtime()
        val f = ((now - windFrom) / maxOf(1L, sleepAt - windFrom).toFloat()).coerceIn(0f, 1f)
        val am = audio
        if (!windUserVolume && !am.isVolumeFixed && windVol > 0) {
            if (music() > windSet) windUserVolume = true   // turned up by hand: respect it
            else {
                val v = maxOf(1, (windVol * (1f - 0.6f * f)).roundToInt())
                if (v != music()) am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                windSet = v
            }
        }
        dim?.animate()?.alpha(0.55f * f)?.setDuration(9_000)?.start()
        if (f < 1f) main.postDelayed(::windTick, 10_000)
    }

    /** Stop winding down; with [restore], put the volume back where it was. */
    private fun endWindDown(restore: Boolean) {
        main.removeCallbacks(windStart)
        if (windFrom == 0L) return
        windFrom = 0
        if (restore && !windUserVolume && windVol >= 0 && !audio.isVolumeFixed) audio.setStreamVolume(AudioManager.STREAM_MUSIC, windVol, 0)
        windVol = -1
        dim?.let { d ->
            dim = null
            d.animate().alpha(0f).setDuration(400).withEndAction { removeOverlay(d) }.start()
        }
    }

    private fun cancelSleepByUser() {
        if (!warning) return
        setSleep(0)
        showBanner("Sleep timer cancelled", 2500)
    }

    /** Minutes left (rounded up), or 0 when off. */
    fun sleepLeft(): Int = if (sleepAt == 0L) 0 else maxOf(1L, (sleepAt - SystemClock.elapsedRealtime() + 59_999) / 60_000).toInt()

    // ------------------------------------------------------------------ timer

    /** Count down from [seconds]; 0 cancels. Replaces a running stopwatch. */
    fun setTimer(seconds: Int) {
        dismissTimer()
        if (seconds <= 0) return
        timerEnd = SystemClock.elapsedRealtime() + seconds * 1000L
        main.post(timerTick)
    }

    fun startStopwatch() {
        dismissTimer()
        swStart = SystemClock.elapsedRealtime()
        main.post(timerTick)
    }

    /** Seconds left on the timer, or -1. */
    fun timerLeft(): Int = if (timerEnd == 0L) -1 else maxOf(0L, (timerEnd - SystemClock.elapsedRealtime() + 999) / 1000).toInt()

    /** Seconds on the stopwatch, or -1. */
    fun stopwatchSecs(): Int = if (swStart == 0L) -1 else ((SystemClock.elapsedRealtime() - swStart) / 1000).toInt()

    fun timerRinging(): Boolean = ringing

    /** Stop the timer/stopwatch and silence it. */
    fun dismissTimer() {
        main.removeCallbacks(timerTick)
        main.removeCallbacks(beep)
        main.removeCallbacks(stopRinging)
        timerEnd = 0
        swStart = 0
        ringing = false
        updateKeyFilter()
        timerView?.let {
            it.animate().cancel()
            removeOverlay(timerBox)
            timerView = null
            timerBox = null
        }
    }

    private fun ring() {
        timerEnd = 0
        ringing = true
        updateKeyFilter()
        showTimer("Time's up!   ·   press any button", true)
        timerView?.animate()?.scaleX(1.06f)?.scaleY(1.06f)?.setDuration(600)?.withEndAction(::pulse)?.start()
        main.post(beep)
        main.postDelayed(stopRinging, 60_000)
    }

    private fun pulse() {
        val v = timerView
        if (!ringing || v == null) return
        val to = if (v.scaleX > 1.03f) 1f else 1.06f
        v.animate().scaleX(to).scaleY(to).setDuration(600).withEndAction(::pulse).start()
    }

    private fun showTimer(text: String, big: Boolean) {
        val view = timerView ?: TextView(this).also { t ->
            t.setTextColor(0xFFFFFFFF.toInt())
            t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            t.letterSpacing = 0.04f
            t.background = GradientDrawable().apply {
                cornerRadius = dp(100f).toFloat()
                setColor(0xE60B0D14.toInt())
                setStroke(dp(1f), 0x40FFFFFF)
            }
            val box = FrameLayout(this).apply {
                clipChildren = false
                setPadding(dp(20f), dp(12f), dp(20f), dp(12f))
                addView(t)
            }
            addOverlay(box, base().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.END
                x = dp(12f)
                y = dp(16f)
            })
            timerView = t
            timerBox = box
        }
        // On Aurora's home screen the Timer button shows the countdown instead.
        val home = HomeActivity::class.java.name == frontClass
        timerBox?.visibility = if (home && !big) View.GONE else View.VISIBLE
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (big) 22f else 16f)
        val p = dp(if (big) 18f else 12f)
        view.setPadding(p * 2, p, p * 2, p)
        (view.background as GradientDrawable).setColor(if (big) 0xF2C8781E.toInt() else 0xE60B0D14.toInt())   // warm amber when it rings
        view.text = text
    }

    // ------------------------------------------------------------------ remote actions

    fun back() { performGlobalAction(GLOBAL_ACTION_BACK) }

    /** Put the TV to sleep (standby), same as the sleep timer does. */
    fun powerOff() {
        setSleep(0)
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    fun home() { performGlobalAction(GLOBAL_ACTION_HOME) }

    /**
     * Wake the TV from standby (the phone's power button when the TV is off). Works while the
     * TV keeps its network up in standby; with "Arrows in every app" on, a real wake-up key
     * press backs it up for TVs that ignore a wake lock.
     */
    fun wake(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isInteractive) return true
        @Suppress("DEPRECATION")
        pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "aurora:phone").acquire(10_000)
        if (fullRemote()) adb().press(KeyEvent.KEYCODE_WAKEUP)
        return true
    }

    fun media(keycode: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keycode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keycode))
    }

    fun volume(direction: Int) = audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)

    // ------------------------------------------------------------------ the phone remote's arrows

    private var adbLocal: AdbLocal? = null

    /** "Arrows in every app": press keys through the TV's debugging connection (asked for once on the TV). */
    fun fullRemote(): Boolean = prefs.getBoolean("remote_full", false)

    fun adb(): AdbLocal = adbLocal ?: AdbLocal(this).also { adbLocal = it }

    fun setFullRemote(on: Boolean) {
        prefs.edit().putBoolean("remote_full", on).apply()
        if (!on) adbLocal?.close()
    }

    /**
     * An arrow or OK from the phone. With "arrows in every app" it's a real key press;
     * otherwise the selection is moved through accessibility, which works on the home
     * screen, settings and most apps, but not apps that draw their own screens.
     * Returns false when nothing could be moved.
     */
    fun arrow(key: String): Boolean {
        val code = when (key) {
            "up" -> KeyEvent.KEYCODE_DPAD_UP
            "down" -> KeyEvent.KEYCODE_DPAD_DOWN
            "left" -> KeyEvent.KEYCODE_DPAD_LEFT
            "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "ok" -> KeyEvent.KEYCODE_DPAD_CENTER
            "menu" -> KeyEvent.KEYCODE_MENU
            else -> return false
        }
        if (fullRemote()) { adb().press(code); return true }
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (key == "ok") return focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (key == "menu") return focused.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        val dir = when (key) { "up" -> View.FOCUS_UP; "down" -> View.FOCUS_DOWN; "left" -> View.FOCUS_LEFT; else -> View.FOCUS_RIGHT }
        val next = focused.focusSearch(dir) ?: return false
        return next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    /** Put text into whatever text field has focus. Returns false if nothing editable is focused. */
    fun type(text: String, submit: Boolean): Boolean {
        val f = focusedEditable() ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        val ok = f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok && submit) f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        return ok
    }

    /** The focused editable field in any window (the keyboard can own the active window). */
    private fun focusedEditable(): AccessibilityNodeInfo? {
        val roots = listOfNotNull(rootInActiveWindow) + windows.mapNotNull { it.root }
        return roots.firstNotNullOfOrNull { r -> r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable } }
    }

    /** Search inside YouTube. */
    fun searchYouTube(q: String): Boolean =
        // YouTube's TV app ignores ACTION_SEARCH but opens results from a web link. (Google TV's
        // cross-app search shows its results in the stock launcher, which is disabled.)
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results").buildUpon()
            .appendQueryParameter("search_query", q).build()).setPackage("com.google.android.youtube.tv"))

    fun launch(i: Intent?): Boolean {
        if (i == null) return false
        return try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            Log.w("Aurora", "launch failed: $e")
            false
        }
    }

    // ------------------------------------------------------------------ overlays

    fun showBanner(text: String, ms: Long) {
        hideBanner()
        val t = TextView(this).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            letterSpacing = 0.04f
            val p = dp(14f)
            setPadding(p * 2, p, p * 2, p)
            background = GradientDrawable().apply {
                cornerRadius = dp(100f).toFloat()
                setColor(0xE60B0D14.toInt())
                setStroke(dp(1f), 0x40FFFFFF)
            }
            alpha = 0f
        }
        banner = t
        addOverlay(t, base().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(28f)
        })
        t.animate().alpha(1f).setDuration(250).start()
        main.postDelayed({ if (banner === t) hideBanner() }, ms)
    }

    fun hideBanner() {
        removeOverlay(banner)
        banner = null
    }

    private fun base() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT)

    private fun full() = base().apply {
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
    }

    private fun addOverlay(v: View, lp: WindowManager.LayoutParams) {
        try { wm.addView(v, lp) } catch (e: Exception) { Log.w("Aurora", "overlay: $e") }
    }

    private fun removeOverlay(v: View?) {
        if (v == null) return
        try { wm.removeViewImmediate(v) } catch (ignored: Exception) { }
    }

    private fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).roundToInt()
}
