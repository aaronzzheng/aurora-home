package home.aurora

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock

/**
 * Quiet interface sounds: a click when focus moves, a tock when something opens,
 * a chime when the TV turns on. They follow the TV volume and can be switched off.
 */
object Sounds {

    private var pool: SoundPool? = null
    private var click = 0
    private var select = 0
    private var chime = 0
    private lateinit var app: Context
    private val sets = HashMap<String, IntArray>()
    private var cur: IntArray? = null   // the theme's {click, select, chime}, or null for the Aurora sounds
    private lateinit var prefs: SharedPreferences
    private var lastClick = 0L
    private var quietUntil = 0L

    private fun init(c: Context) {
        if (pool != null) return
        app = c.applicationContext
        prefs = Prefs.of(app)
        val p = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        pool = p
        click = p.load(app, R.raw.ui_click, 1)
        select = p.load(app, R.raw.ui_select, 1)
        chime = p.load(app, R.raw.ui_chime, 1)
    }

    @JvmStatic
    fun enabled(c: Context): Boolean {
        init(c)
        return prefs.getBoolean("ui_sounds", true)
    }

    @JvmStatic
    fun setEnabled(c: Context, on: Boolean) {
        init(c)
        prefs.edit().putBoolean("ui_sounds", on).apply()
    }

    /**
     * How loud the sounds are, as a percent of their full level; set in Settings. Eight steps
     * from barely there to as loud as the files go (the old top step is about 35 here).
     */
    private val LEVELS = intArrayOf(5, 10, 20, 35, 50, 65, 80, 100)

    @JvmStatic
    fun level(c: Context): Int {
        init(c)
        return prefs.getInt("ui_sounds_level", 20)
    }

    /** "▮▮▮▯▯▯▯▯" for the third step of eight. */
    @JvmStatic
    fun levelName(c: Context): String {
        val l = level(c)
        val i = LEVELS.indexOfFirst { it >= l }.let { if (it < 0) LEVELS.size - 1 else it }
        return "▮".repeat(i + 1) + "▯".repeat(LEVELS.size - i - 1)
    }

    /** Step to the next level (wrapping round) and play a sample at it. */
    @JvmStatic
    fun nextLevel(c: Context) {
        // Step from the step shown, so a level saved between steps (older versions) still moves.
        val l = level(c)
        val shown = LEVELS.indexOfFirst { it >= l }.let { if (it < 0) LEVELS.size - 1 else it }
        prefs.edit().putInt("ui_sounds_level", LEVELS[(shown + 1) % LEVELS.size]).apply()
        select(c)
    }

    /** Use a theme's own sounds (ui_click_<id> etc.); themes without any keep the Aurora ones. */
    @JvmStatic
    fun setTheme(c: Context, id: String?) {
        init(c)
        if (id == null) { cur = null; return }
        var set = sets[id]
        if (set == null) {
            val r = app.resources
            val a = r.getIdentifier("ui_click_$id", "raw", app.packageName)
            val b = r.getIdentifier("ui_select_$id", "raw", app.packageName)
            val d = r.getIdentifier("ui_chime_$id", "raw", app.packageName)
            val p = pool!!
            set = if (a == 0 || b == 0 || d == 0) IntArray(0)
            else intArrayOf(p.load(app, a, 1), p.load(app, b, 1), p.load(app, d, 1))
            sets[id] = set
        }
        cur = if (set.isEmpty()) null else set
    }

    /** Skip sounds briefly, e.g. while a screen sets its initial focus. */
    @JvmStatic
    fun quietFor(ms: Long) { quietUntil = SystemClock.uptimeMillis() + ms }

    @JvmStatic
    fun click(c: Context) {
        val now = SystemClock.uptimeMillis()
        if (now - lastClick < 45) return   // holding an arrow down shouldn't buzz
        lastClick = now
        play(c, cur?.get(0) ?: click, 0.7f)
    }

    @JvmStatic
    fun select(c: Context) { play(c, cur?.get(1) ?: select, 0.9f) }

    @JvmStatic
    fun chime(c: Context) { play(c, cur?.get(2) ?: chime, 1f) }

    private fun play(c: Context, id: Int, vol: Float) {
        if (!enabled(c) || SystemClock.uptimeMillis() < quietUntil) return
        val v = vol * level(c) / 100f
        pool!!.play(id, v, v, 1, 0, 1f)
    }
}
