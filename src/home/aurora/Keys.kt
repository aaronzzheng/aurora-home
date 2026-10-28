package home.aurora

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent

/** Pacing for held buttons on Aurora's own screens. */
object Keys {

    /** Held arrows repeat at most this often (the remote repeats every ~50 ms). */
    private const val REPEAT_MS = 130L
    private var last = 0L

    /** True for a held arrow's repeat that comes too soon; the caller drops it. */
    @JvmStatic
    fun tooFast(e: KeyEvent): Boolean {
        if (e.action != KeyEvent.ACTION_DOWN) return false
        val arrow = e.keyCode == KeyEvent.KEYCODE_DPAD_UP || e.keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        if (!arrow) return false
        val now = SystemClock.uptimeMillis()
        if (e.repeatCount > 0 && now - last < REPEAT_MS) return true
        last = now
        return false
    }

    /**
     * The remote's Settings (gear) button sends KEYCODE_NOTIFICATION, which Google TV hands to
     * its own home screen's quick-settings panel. With that home screen switched off nothing
     * opens, so on Aurora's screens the button opens the TV's Settings instead.
     */
    @JvmStatic
    fun settingsButton(a: Activity, e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_NOTIFICATION) return false
        if (e.action == KeyEvent.ACTION_UP && !e.isCanceled) {
            try {
                a.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (ignored: Exception) { }
        }
        return true
    }
}
