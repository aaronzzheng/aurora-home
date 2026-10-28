package home.aurora

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlin.math.roundToInt

/**
 * Small everyday conveniences driven by which app is on screen:
 *  - volume per app: remembers the volume you leave each app at and restores it
 *  - safe startup volume: the TV never wakes up louder than a set level
 *  - resume on startup: waking the TV reopens the app you were last using
 *  - last app: remembered for the "Back to" tile on the home screen
 * Each can be switched off.
 */
class Habits(private val ctx: Context) {

    companion object {
        const val HOME = "home.aurora"
        const val LIVE_TV = "com.tcl.tv"
    }

    private val prefs = Prefs.of(ctx)
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main = Handler(Looper.getMainLooper())
    private var apps: Apps? = null
    private var appsLoadedAt = 0L
    private var current: String? = null

    // ------------------------------------------------------------------ settings

    fun perAppVolume(): Boolean = prefs.getBoolean("hab_pervol", true)
    fun safeStart(): Boolean = prefs.getBoolean("hab_safe", true)
    fun safeLevel(): Int = prefs.getInt("hab_safe_pct", 25)        // percent of max
    fun resume(): Boolean = prefs.getBoolean("hab_resume", true)
    fun lastApp(): String? = prefs.getString("last_app", null)
    fun current(): String? = current

    fun set(perVol: Boolean?, safe: Boolean?, safePct: Int?, resume: Boolean?) {
        prefs.edit().apply {
            perVol?.let { putBoolean("hab_pervol", it) }
            safe?.let { putBoolean("hab_safe", it) }
            safePct?.let { putInt("hab_safe_pct", it.coerceIn(5, 80)) }
            resume?.let { putBoolean("hab_resume", it) }
        }.apply()
    }

    // ------------------------------------------------------------------ app switches

    /** Called for every window change; only real apps (and the home screen) count. */
    fun onWindow(pkgName: CharSequence?) {
        val pkg = pkgName?.toString() ?: return
        if (pkg == current || !isApp(pkg)) return
        val from = current
        current = pkg

        if (perAppVolume() && !audio.isVolumeFixed) {
            if (from != null) prefs.edit().putInt("vol:$from", music()).apply()
            val saved = prefs.getInt("vol:$pkg", -1)
            if (saved >= 0 && saved != music()) audio.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0)
        }
        if (pkg != HOME) prefs.edit().putString("last_app", pkg).putLong("last_app_at", System.currentTimeMillis()).apply()
    }

    private fun loadedApps(): Apps {
        val a = apps
        if (a != null && SystemClock.uptimeMillis() - appsLoadedAt <= 60_000) return a
        return Apps(ctx).also { it.reload(); apps = it; appsLoadedAt = SystemClock.uptimeMillis() }
    }

    private fun isApp(pkg: String): Boolean = pkg == HOME || pkg == LIVE_TV || loadedApps().get(pkg) != null

    private fun music(): Int = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

    // ------------------------------------------------------------------ waking up

    /** Screen just turned on (or the TV booted). */
    fun onWake() {
        capVolume()
        main.postDelayed({ Sounds.setTheme(ctx, HomeTheme.current(ctx).sounds); Sounds.chime(ctx) }, 600)
        // Give the TV a moment: if a remote app button already opened something, leave it alone.
        main.postDelayed({
            // Cap again only if we reopened an app (it may restore its own louder volume);
            // otherwise a volume change made right after turning on would be undone.
            if (resume() && reopenLast()) main.postDelayed(::capVolume, 2500)
        }, 1800)
    }

    private fun capVolume() {
        if (!safeStart() || audio.isVolumeFixed) return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cap = maxOf(1, (max * safeLevel() / 100f).roundToInt())
        if (music() > cap) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0)
            Log.i("AuroraHabits", "startup volume capped at $cap/$max")
        }
    }

    /** Reopen the last app; true if it was. */
    private fun reopenLast(): Boolean {
        val last = lastApp() ?: return false
        if (current != null && current != HOME) return false
        // The morning screen woke the TV: that's what it should show, not yesterday's app.
        if (MorningActivity.visible || SystemClock.elapsedRealtime() - Morning.wokeAt < 60_000) return false
        // Don't resume something from days ago.
        if (System.currentTimeMillis() - prefs.getLong("last_app_at", 0) > 3L * 24 * 3600_000) return false
        val i = if (last == LIVE_TV) ctx.packageManager.getLeanbackLaunchIntentForPackage(LIVE_TV)
        else loadedApps().get(last)?.launchIntent()
        i ?: return false
        return try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Log.i("AuroraHabits", "resumed $last")
            true
        } catch (e: Exception) {
            Log.w("AuroraHabits", "resume failed: $e")
            false
        }
    }
}
