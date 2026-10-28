package home.aurora

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings backup: every setting as one JSON file the phone remote downloads,
 * and restores later (after a reset or on a new TV). Restoring always asks on the
 * TV first, with the PIN if there is one, so a paired phone can't quietly switch
 * off Kid lock.
 *
 * Left out: caches, today's state, phone-remote tokens, the family PIN and the
 * calendar's secret link. A restore keeps this TV's own PIN, calendar and paired
 * phones, so a backup file never grants access to anything. (Launch counts and
 * per-app volumes are kept: they shape the app order and the volume you're used to.)
 *
 * Every value is checked against the type the app reads it as before it's written,
 * so an edited or damaged file can't leave the home screen crashing on start.
 */
object Backup {

    const val KIND = "aurora-home-backup"
    const val VERSION = 1

    private val SKIP = setOf(
        "art_list2", "art_list2_time", "art_order", "art_pos",   // painting list cache
        "w_cache", "w_time", "weather_code",                    // weather cache
        "bed_done", "pin_misses", "pin_wait_until",             // today's state, lockouts
        "remote_tokens", "remote_phones",                       // pairing: phones pair again
        "lock_pin", "cal_url",                                  // secrets stay on the TV
        "last_app", "last_app_at",                              // what was on last
        "debug_month", "debug_day", "debug_hour",
    )

    private fun kept(key: String) = key !in SKIP

    /** What each setting is read as: b(oolean), i(nt), l(ong), f(loat), s(tring), set. */
    private val TYPES: Map<String, String> = buildMap {
        listOf("bed_on", "dream_art", "hab_pervol", "hab_resume", "hab_safe", "holidays", "lock_on", "morning_on", "night_on",
            "remote_on", "settings_pin", "show_date", "show_weather", "ui_sounds", "w_fahrenheit", "w_unit_set", "wind_down", "gentle_volume")
            .forEach { put(it, "b") }
        listOf("bed_at", "hab_safe_pct", "morning_at", "morning_days", "night_end", "night_level", "night_start", "wp_every", "ui_sounds_level")
            .forEach { put(it, "i") }
        listOf("clock", "dock", "theme", "w_place").forEach { put(it, "s") }
        listOf("hidden", "locked", "wp_off").forEach { put(it, "set") }
        put("w_lat", "f")
        put("w_lon", "f")
    }

    private fun counter(key: String) = key.startsWith("n:") || key.startsWith("vol:") || key.startsWith("best_")

    /** Allowed range for numbers that have one. */
    private fun range(key: String): IntRange? = when (key) {
        "night_start", "night_end" -> 0..23
        "ui_sounds_level" -> 0..100
        "bed_at", "morning_at" -> 0 until 24 * 60
        "morning_days" -> 0..2
        "hab_safe_pct" -> 5..80
        "night_level" -> 5..70
        "wp_every" -> 0..24 * 60
        else -> if (counter(key)) 0..Int.MAX_VALUE else null
    }

    /** The type a setting must have, or null for settings this version doesn't know. */
    @JvmStatic
    fun typeOf(key: String): String? = TYPES[key] ?: if (counter(key)) "i" else null   // launch counts, volumes, game scores

    /** Everything worth keeping, as a file. */
    @JvmStatic
    fun export(c: Context): JSONObject {
        val values = JSONObject()
        for ((k, v) in Prefs.of(c).all) {
            if (!kept(k) || v == null) continue
            val typed = when (v) {
                is Boolean -> JSONObject().put("b", v)
                is Int -> JSONObject().put("i", v)
                is Long -> JSONObject().put("l", v)
                is Float -> JSONObject().put("f", v.toDouble())
                is String -> JSONObject().put("s", v)
                is Set<*> -> JSONObject().put("set", JSONArray(v))
                else -> continue
            }
            values.put(k, typed)
        }
        return JSONObject()
            .put("kind", KIND)
            .put("version", VERSION)
            .put("made", System.currentTimeMillis())
            .put("tv", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("settings", values)
    }

    /** Check a file before anything is touched; returns it or throws with a plain message. */
    @JvmStatic
    fun read(text: String?): JSONObject {
        val o = try { JSONObject(text ?: "") } catch (e: Exception) { throw Exception("That isn't an Aurora Home backup file") }
        if (o.optString("kind") != KIND) throw Exception("That isn't an Aurora Home backup file")
        if (o.optInt("version", 0) > VERSION) throw Exception("This backup is from a newer Aurora Home. Update the TV first.")
        val s = o.optJSONObject("settings")
        if (s == null || s.length() == 0) throw Exception("The backup is empty")
        return o
    }

    /** A few lines so the person at the TV knows what they're agreeing to. */
    @JvmStatic
    fun summary(c: Context, backup: JSONObject): String {
        val tvHasPin = Pin.isSet(Prefs.of(c))
        val s = backup.optJSONObject("settings") ?: JSONObject()
        val lines = mutableListOf<String>()
        val made = backup.optLong("made", 0)
        if (made > 0) {
            val tv = backup.optString("tv")
            lines += "Saved " + SimpleDateFormat("EEEE, MMMM d 'at' h:mm a", Locale.getDefault()).format(Date(made)) +
                (if (tv.isEmpty()) "" else " from $tv")
        }
        lines += "Theme:  " + HomeTheme.nameOf(s.str("theme") ?: "aurora")
        s.str("dock")?.let { dock -> lines += "Apps on the home screen:  " + (if (dock.isEmpty()) 0 else dock.split(",").size) }
        val locked = s.setSize("locked")
        val apps = "$locked locked app" + if (locked == 1) "" else "s"
        lines += "Kid lock:  " + when {
            !s.bool("lock_on") -> "off"
            tvHasPin -> "on, $apps (this TV's PIN stays)"
            else -> "$apps, on once you set a PIN"
        }
        lines += "PIN for Settings:  " + if (s.bool("settings_pin")) "on" else "off"
        lines += "Bedtime:  " + (if (s.bool("bed_on")) AuroraSettings.time(s.int("bed_at", 23 * 60 + 30)) else "off") +
            "   ·   Morning screen:  " + (if (s.bool("morning_on")) AuroraSettings.time(s.int("morning_at", 7 * 60)) else "off")
        lines += "This TV's PIN, calendar link and paired phones stay as they are."
        return lines.joinToString("\n")
    }

    /** Replace this TV's settings with the backup's. */
    @JvmStatic
    fun apply(c: Context, backup: JSONObject) {
        val s = backup.getJSONObject("settings")
        val p = Prefs.of(c)
        val e = p.edit()
        // Settings the backup didn't have go back to their defaults, so the TV matches it exactly.
        p.all.keys.filter(::kept).forEach { e.remove(it) }
        for (k in s.keys()) {
            if (!kept(k)) continue
            val t = s.optJSONObject(k) ?: continue
            val want = typeOf(k) ?: continue                // a setting this version doesn't know
            if (!t.has(want)) continue                       // the wrong type: skip it
            try {
                when (val v = t.get(want)) {
                    is Boolean -> if (want == "b") e.putBoolean(k, v)
                    is Int -> when (want) {
                        "i" -> e.putInt(k, range(k)?.let { v.coerceIn(it) } ?: v)
                        "l" -> e.putLong(k, v.toLong())
                        "f" -> e.putFloat(k, v.toFloat())
                    }
                    is Number -> when (want) {
                        "l" -> e.putLong(k, v.toLong())
                        "f" -> e.putFloat(k, v.toFloat())
                    }
                    is String -> if (want == "s") e.putString(k, v)
                    is JSONArray -> if (want == "set") {
                        e.putStringSet(k, (0 until v.length()).mapNotNull { v.get(it) as? String }.toSet())
                    }
                }
            } catch (ignored: Exception) {   // one bad value never stops the rest
            }
        }
        e.remove("pin_misses").remove("pin_wait_until").remove("bed_done")
        if (!e.commit()) throw Exception("Couldn't save the settings")
    }

    // ------------------------------------------------------------------ a restore waiting for the TV

    private var pending: JSONObject? = null

    @JvmStatic @Synchronized
    fun offer(backup: JSONObject) { pending = backup }

    @JvmStatic @Synchronized
    fun take(): JSONObject? = pending.also { pending = null }

    @JvmStatic @Synchronized
    fun waiting(): Boolean = pending != null

    // ------------------------------------------------------------------ reading typed values from a backup

    private fun JSONObject.typed(k: String, type: String): JSONObject? = optJSONObject(k)?.takeIf { it.has(type) }
    private fun JSONObject.str(k: String): String? = typed(k, "s")?.optString("s")
    private fun JSONObject.bool(k: String): Boolean = typed(k, "b")?.optBoolean("b") ?: false
    private fun JSONObject.int(k: String, def: Int): Int = typed(k, "i")?.optInt("i") ?: def
    private fun JSONObject.setSize(k: String): Int = typed(k, "set")?.optJSONArray("set")?.length() ?: 0
}
