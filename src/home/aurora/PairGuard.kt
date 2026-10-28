package home.aurora

import java.util.ArrayDeque
import java.util.Locale
import java.util.Random

/**
 * Pairing codes and the limits on guessing them. Six-digit codes, good for two
 * minutes and five guesses each; and no more than [MISSES_PER_HOUR] wrong guesses
 * an hour across all codes, so asking for fresh codes doesn't reset the count.
 * Plain Kotlin (no Android) so it can be tested on its own.
 */
class PairGuard(private val rnd: Random) {

    companion object {
        const val TRIES_PER_CODE = 5
        const val MISSES_PER_HOUR = 15
        const val CODE_MS = 120_000L
        const val HOUR = 3_600_000L
    }

    private val misses = ArrayDeque<Long>()
    private var code: String? = null
    private var until = 0L
    private var pausedUntil = 0L
    private var codeTries = 0

    /** A fresh code. Callers decide who may ask (the TV itself always may). */
    @Synchronized
    fun newCode(now: Long): String {
        val c = String.format(Locale.ROOT, "%06d", rnd.nextInt(1_000_000))
        code = c
        until = now + CODE_MS
        codeTries = 0
        return c
    }

    /** The current code if it's still good, else null. */
    @Synchronized
    fun current(now: Long): String? = code?.takeIf { now <= until }

    @Synchronized
    fun paused(now: Long): Boolean = now < pausedUntil

    /** Whole minutes until pairing reopens (at least 1 while paused). */
    @Synchronized
    fun minutesLeft(now: Long): Long = maxOf(1, (pausedUntil - now + 59_999) / 60_000)

    /** Someone at the TV asked to pair: lift any pause. */
    @Synchronized
    fun unpause() {
        pausedUntil = 0
        misses.clear()
    }

    /**
     * Check a guess (and, when the TV has a PIN, whether that PIN was right).
     * Returns null when pairing may go ahead, else a message for the phone.
     */
    @Synchronized
    fun check(guess: String?, pinOk: Boolean, now: Long): String? {
        while (misses.isNotEmpty() && now - misses.peekFirst() > HOUR) misses.pollFirst()
        if (now < pausedUntil) return "Too many wrong tries. Try again in ${minutesLeft(now)} minutes."
        val c = code
        if (c == null || now > until) return "Code expired. Request a new one."
        val right = guess != null && c == guess.trim()
        if (right && pinOk) {
            code = null
            return null
        }
        misses.addLast(now)
        codeTries++
        if (misses.size >= MISSES_PER_HOUR) {
            pausedUntil = now + HOUR
            misses.clear()
            code = null
            return "Too many wrong tries. Pairing is paused for an hour."
        }
        if (codeTries >= TRIES_PER_CODE) {
            code = null
            return "Too many tries. Request a new code."
        }
        return if (right) "Wrong PIN" else "Wrong code"
    }
}
