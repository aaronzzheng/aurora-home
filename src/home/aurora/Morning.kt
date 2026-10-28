package home.aurora

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Morning screen: at a set time the TV wakes to the time, the weather and the
 * day's calendar, then goes back to sleep on its own if nobody touches it.
 * Off by default. If the TV is already on at that time, it's left alone.
 */
class Morning : BroadcastReceiver() {

    override fun onReceive(c: Context, i: Intent) {
        schedule(c)
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) return   // someone's already watching: don't interrupt
        // Only the background service can put the TV back to sleep; without it the TV would stay on all day.
        if (AuroraService.instance == null) { Log.w("AuroraMorning", "service off, not waking the TV"); return }
        @Suppress("DEPRECATION")
        pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "aurora:morning").acquire(15_000)
        wokeAt = SystemClock.elapsedRealtime()
        show(c, true)
    }

    /**
     * Alarms don't survive a reboot, and the service that also re-arms this may be off:
     * re-arm after a boot, an update, a clock change, or the exact-alarm permission changing.
     */
    class Rearm : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = schedule(c)
    }

    class Event(@JvmField val title: String, @JvmField val start: Long, @JvmField val allDay: Boolean)

    companion object {

        @JvmField val DAYS = arrayOf("Every day", "Weekdays", "Weekends")

        /** When the morning screen last woke the TV (elapsedRealtime), so "open the last app" leaves it alone. */
        @JvmField @Volatile var wokeAt = 0L

        @JvmStatic fun prefs(c: Context): SharedPreferences = Prefs.of(c)
        @JvmStatic fun on(c: Context): Boolean = prefs(c).getBoolean("morning_on", false)
        /** Minutes after midnight. */
        @JvmStatic fun at(c: Context): Int = prefs(c).getInt("morning_at", 7 * 60)
        @JvmStatic fun days(c: Context): Int = prefs(c).getInt("morning_days", 1)

        @JvmStatic
        fun set(c: Context, on: Boolean, at: Int, days: Int) {
            prefs(c).edit().putBoolean("morning_on", on).putInt("morning_at", at).putInt("morning_days", days).apply()
            schedule(c)
        }

        private fun intent(c: Context): PendingIntent =
            PendingIntent.getBroadcast(c, 7, Intent(c, Morning::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        /** (Re)arm the alarm for the next morning that counts; cancels it when off. */
        @JvmStatic
        fun schedule(c: Context) {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = intent(c)
            am.cancel(pi)
            if (!on(c)) return
            val time = next(Calendar.getInstance(), at(c), days(c))
            // An alarm clock fires on the minute even in Doze. USE_EXACT_ALARM (Android 13+) grants it
            // without asking; on 12 it needs "Alarms & reminders", else it's merely close.
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setAlarmClock(AlarmManager.AlarmClockInfo(time, null), pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            Log.i("AuroraMorning", "next morning screen ${Date(time)}")
        }

        @JvmStatic
        fun next(now: Calendar, at: Int, days: Int): Long {
            val t = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, at / 60)
                set(Calendar.MINUTE, at % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (!t.after(now)) t.add(Calendar.DAY_OF_YEAR, 1)
            repeat(7) { if (!counts(t, days)) t.add(Calendar.DAY_OF_YEAR, 1) }
            return t.timeInMillis
        }

        private fun counts(t: Calendar, days: Int): Boolean {
            val d = t.get(Calendar.DAY_OF_WEEK)
            val weekend = d == Calendar.SATURDAY || d == Calendar.SUNDAY
            return days == 0 || (days == 1 && !weekend) || (days == 2 && weekend)
        }

        /** Open the morning screen; [woke] means it turned the TV on and should turn it off again. */
        @JvmStatic
        fun show(c: Context, woke: Boolean) {
            try {
                c.startActivity(Intent(c, MorningActivity::class.java).putExtra("woke", woke).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Log.w("AuroraMorning", "couldn't show: $e")
            }
        }

        // ------------------------------------------------------------------ calendar

        /**
         * Today's events from a calendar's secret iCal link (Google Calendar: Settings →
         * your calendar → "Secret address in iCal format").
         */
        @JvmStatic
        fun today(c: Context): List<Event> {
            var url = prefs(c).getString("cal_url", "")!!.trim()
            if (url.isEmpty()) return emptyList()
            if (url.startsWith("webcal://")) url = "https://" + url.substring(9)
            return eventsOn(Weather.get(url), Calendar.getInstance())
        }

        /** One VEVENT, as read from the file. */
        private class Raw {
            var title: String? = null
            var uid: String? = null
            var start: String? = null
            var startParams = ""
            var rule: String? = null
            var recurrence: String? = null
            var recurrenceParams = ""
            val exdates = mutableListOf<Pair<String, String>>()   // params to value
        }

        /**
         * The events in [ics] that happen on [day]: one-off events, daily, weekly,
         * monthly (by date, or by weekday like "2nd Tuesday") and yearly repeats with INTERVAL, COUNT and UNTIL, skipped dates (EXDATE),
         * and single occurrences that were moved or edited (RECURRENCE-ID). Plain code (tested).
         */
        @JvmStatic
        fun eventsOn(ics: String, day: Calendar): List<Event> {
            val text = ics.replace("\r\n ", "").replace("\n ", "").replace("\r\n\t", "").replace("\n\t", "")
            val events = mutableListOf<Raw>()
            var cur: Raw? = null
            for (line in text.split(Regex("\r?\n"))) {
                if (line == "BEGIN:VEVENT") { cur = Raw(); continue }
                val e = cur ?: continue
                if (line == "END:VEVENT") { events += e; cur = null; continue }
                val colon = line.indexOf(':')
                if (colon < 0) continue
                val name = line.substring(0, colon)
                val value = line.substring(colon + 1)
                val key = name.substringBefore(';')
                val params = if (';' in name) name.substringAfter(';') else ""
                when (key) {
                    "SUMMARY" -> e.title = value
                    "UID" -> e.uid = value
                    "DTSTART" -> { e.start = value; e.startParams = params }
                    "RRULE" -> e.rule = value
                    "EXDATE" -> value.split(",").forEach { e.exdates += params to it }
                    "RECURRENCE-ID" -> { e.recurrence = value; e.recurrenceParams = params }
                }
            }
            val ymd = ymd(day)
            // Moved or edited occurrences replace the series' own occurrence on that date.
            val replaced = events.mapNotNullTo(HashSet()) { e ->
                val r = e.recurrence ?: return@mapNotNullTo null
                val uid = e.uid ?: return@mapNotNullTo null
                runCatching { uid + "@" + ymd(local(parse(r, e.recurrenceParams))) }.getOrNull()
            }
            val out = mutableListOf<Event>()
            for (e in events) {
                val startText = e.start ?: continue
                try {
                    val allDay = 'T' !in startText
                    val s = local(parse(startText, e.startParams))
                    if (e.recurrence == null && e.uid != null && (e.uid + "@" + ymd) in replaced) continue
                    if (e.exdates.any { (p, v) -> ymd(local(parse(v, p))) == ymd }) continue
                    if (!occursOn(s, if (e.recurrence == null) e.rule else null, day)) continue
                    val time = (day.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, if (allDay) 0 else s.get(Calendar.HOUR_OF_DAY))
                        set(Calendar.MINUTE, if (allDay) 0 else s.get(Calendar.MINUTE))
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    out += Event(e.title?.let(::unescape) ?: "(no title)", time.timeInMillis, allDay)
                } catch (ignored: Exception) {   // one odd event never hides the rest
                }
            }
            return out.sortedWith(compareBy<Event> { !it.allDay }.thenBy { it.start })
        }

        private fun ymd(c: Calendar): Int = c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)

        /** The same instant in this TV's time zone. */
        private fun local(c: Calendar): Calendar = Calendar.getInstance().apply { timeInMillis = c.timeInMillis }

        @JvmStatic
        fun parse(v: String, params: String): Calendar {
            val utc = v.endsWith("Z")
            val tz = params.split(";").firstOrNull { it.startsWith("TZID=") }?.substring(5)?.replace("\"", "")
            val dateOnly = 'T' !in v
            val f = SimpleDateFormat(if (dateOnly) "yyyyMMdd" else "yyyyMMdd'T'HHmmss", Locale.US)
            // All-day dates are the same date everywhere: read them in this TV's zone.
            f.timeZone = when {
                utc -> TimeZone.getTimeZone("UTC")
                dateOnly || tz == null -> TimeZone.getDefault()
                else -> zone(tz)
            }
            return Calendar.getInstance().apply { time = f.parse(if (utc) v.dropLast(1) else v)!! }
        }

        private val WINDOWS_ZONES = mapOf(
            "eastern standard time" to "America/New_York", "central standard time" to "America/Chicago",
            "mountain standard time" to "America/Denver", "us mountain standard time" to "America/Phoenix",
            "pacific standard time" to "America/Los_Angeles", "alaskan standard time" to "America/Anchorage",
            "hawaiian standard time" to "Pacific/Honolulu", "atlantic standard time" to "America/Halifax",
            "gmt standard time" to "Europe/London", "w. europe standard time" to "Europe/Berlin",
            "romance standard time" to "Europe/Paris", "central europe standard time" to "Europe/Budapest",
            "india standard time" to "Asia/Kolkata", "china standard time" to "Asia/Shanghai",
            "tokyo standard time" to "Asia/Tokyo", "aus eastern standard time" to "Australia/Sydney",
            "utc" to "UTC",
        )

        /** An iCal TZID: Olson names, plus the Windows names Outlook writes. Unknown ones use this TV's zone, not GMT. */
        @JvmStatic
        fun zone(tz: String): TimeZone {
            WINDOWS_ZONES[tz.lowercase(Locale.ROOT)]?.let { return TimeZone.getTimeZone(it) }
            val z = TimeZone.getTimeZone(tz)
            val unknown = z.id == "GMT" && !tz.equals("GMT", true) && !tz.equals("Etc/GMT", true)
            return if (unknown) TimeZone.getDefault() else z
        }

        /** Whole days from [a]'s date to [b]'s date (calendar days, not 24-hour spans). */
        private fun daysBetween(a: Calendar, b: Calendar): Int {
            fun midnightUtc(c: Calendar) = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis
            return ((midnightUtc(b) - midnightUtc(a)) / 86_400_000.0).roundToInt()
        }

        private val DAY_NAMES = arrayOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")

        /** Does an event starting at [start] (with this RRULE, or none) happen on [day]? */
        @JvmStatic
        fun occursOn(start: Calendar, rule: String?, day: Calendar): Boolean {
            val diff = daysBetween(start, day)
            if (diff == 0) return true
            if (rule == null || diff < 0) return false
            var freq: String? = null
            var byDay: String? = null
            var interval = 1
            var count = -1
            var until = Int.MAX_VALUE
            for (part in rule.split(";")) when {
                part.startsWith("FREQ=") -> freq = part.substring(5)
                part.startsWith("BYDAY=") -> byDay = part.substring(6)
                part.startsWith("INTERVAL=") -> interval = maxOf(1, part.substring(9).toInt())
                part.startsWith("COUNT=") -> count = part.substring(6).toInt()
                part.startsWith("UNTIL=") -> until = untilDay(part.substring(6))
            }
            if (ymd(day) > until) return false
            return when (freq) {
                "DAILY" -> diff % interval == 0 && (count < 0 || diff / interval < count)
                "WEEKLY" -> {
                    val days = byDay?.let { b -> (1..7).filter { DAY_NAMES[it - 1] in b } } ?: listOf(start.get(Calendar.DAY_OF_WEEK))
                    if (day.get(Calendar.DAY_OF_WEEK) !in days) return false
                    // Weeks run Monday to Sunday (iCal's default WKST); every INTERVAL-th week counts.
                    val startMon = (start.get(Calendar.DAY_OF_WEEK) + 5) % 7   // days since Monday
                    if ((diff + startMon) / 7 % interval != 0) return false
                    if (count < 0) return true
                    // Occurrences from the start up to and including this day.
                    val walk = start.clone() as Calendar
                    var n = 0
                    for (d in 0..diff) {
                        if (walk.get(Calendar.DAY_OF_WEEK) in days && (d + startMon) / 7 % interval == 0) n++
                        walk.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    n <= count
                }
                "MONTHLY" -> {
                    val months = (day.get(Calendar.YEAR) - start.get(Calendar.YEAR)) * 12 + day.get(Calendar.MONTH) - start.get(Calendar.MONTH)
                    if (months % interval != 0) return false
                    if (byDay == null) {
                        return day.get(Calendar.DAY_OF_MONTH) == start.get(Calendar.DAY_OF_MONTH) &&
                            (count < 0 || months / interval < count)
                    }
                    // "2nd Tuesday", "last Friday", or every Monday of the month.
                    if (!monthlyByDay(day, byDay)) return false
                    if (count < 0) return true
                    val walk = start.clone() as Calendar
                    var n = 0
                    for (d in 0..diff) {
                        val m = (walk.get(Calendar.YEAR) - start.get(Calendar.YEAR)) * 12 + walk.get(Calendar.MONTH) - start.get(Calendar.MONTH)
                        if (m % interval == 0 && monthlyByDay(walk, byDay)) n++
                        walk.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    n <= count
                }
                "YEARLY" -> {
                    val years = day.get(Calendar.YEAR) - start.get(Calendar.YEAR)
                    day.get(Calendar.MONTH) == start.get(Calendar.MONTH) && day.get(Calendar.DAY_OF_MONTH) == start.get(Calendar.DAY_OF_MONTH) &&
                        years % interval == 0 && (count < 0 || years / interval < count)
                }
                else -> false   // rules this TV doesn't understand are left out rather than shown wrong
            }
        }

        /** Does [day] match a monthly BYDAY list such as "2TU", "-1FR" or "MO,WE"? */
        private fun monthlyByDay(day: Calendar, byDay: String): Boolean {
            val dom = day.get(Calendar.DAY_OF_MONTH)
            val last = day.getActualMaximum(Calendar.DAY_OF_MONTH)
            val name = DAY_NAMES[day.get(Calendar.DAY_OF_WEEK) - 1]
            return byDay.split(",").any { b ->
                if (b.length < 2 || !b.endsWith(name)) return@any false
                val n = b.dropLast(2).toIntOrNull() ?: 0
                when {
                    n > 0 -> (dom - 1) / 7 + 1 == n
                    n < 0 -> (last - dom) / 7 + 1 == -n
                    else -> true
                }
            }
        }

        /** UNTIL as a local yyyymmdd: a UTC time ("...T030000Z") can fall on the day before here. */
        private fun untilDay(v: String): Int {
            if (v.length >= 16 && v.endsWith("Z")) {
                val f = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                runCatching { f.parse(v) }.getOrNull()?.let { d ->
                    return ymd(Calendar.getInstance().apply { time = d })
                }
            }
            return v.substring(0, 8).toInt()
        }

        private fun unescape(s: String) = s.replace("\\,", ",").replace("\\;", ";").replace("\\n", " ").replace("\\\\", "\\")
    }
}
