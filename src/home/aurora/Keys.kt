package home.aurora

import android.os.SystemClock
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
}
