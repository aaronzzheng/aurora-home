package home.aurora

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Collections

/** Watch time per app over the last 7 days, from Android's own usage stats. Never leaves the TV. */
object Stats {

    class Entry(@JvmField val pkg: String, @JvmField val label: String, @JvmField val ms: Long)

    @JvmStatic
    fun allowed(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    /** Apps the home screen lists, most-watched first, ignoring anything under a minute. */
    @JvmStatic
    fun week(c: Context, apps: Apps): MutableList<Entry> {
        val out = ArrayList<Entry>()
        if (!allowed(c)) return out
        val um = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val m = um.queryAndAggregateUsageStats(now - 7L * 24 * 3600_000, now)
        for (u in m.values) {
            val a = apps.get(u.packageName)
            val t = u.totalTimeInForeground
            if (a != null && t >= 60_000) out.add(Entry(a.pkg, a.label, t))
        }
        Collections.sort(out) { x, y -> java.lang.Long.compare(y.ms, x.ms) }
        return out
    }

    @JvmStatic
    fun total(es: List<Entry>): Long {
        var t = 0L
        for (e in es) t += e.ms
        return t
    }

    @JvmStatic
    fun format(ms: Long): String {
        val m = ms / 60_000
        val h = m / 60
        return if (h > 0) "${h}h ${m % 60}m" else "${m}m"
    }
}
