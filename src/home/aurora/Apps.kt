package home.aurora

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.text.TextUtils
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.HashSet
import java.util.LinkedHashMap

/** Launchable apps, the user's dock order, hidden apps and launch counts. All stored on-device. */
open class Apps(c: Context) {

    open class App(ri: ResolveInfo, pm: PackageManager, leanback: Boolean) {
        @JvmField val pkg: String
        @JvmField val label: String
        @JvmField val component: ComponentName
        @JvmField val info: ActivityInfo
        @JvmField val leanback: Boolean
        @JvmField var launches = 0

        init {
            info = ri.activityInfo
            pkg = info.packageName
            label = ri.loadLabel(pm).toString()
            component = ComponentName(pkg, info.name)
            this.leanback = leanback
        }

        /** 16:9 TV banner if the app has one, else null. */
        fun banner(pm: PackageManager): Drawable? {
            var d: Drawable? = info.loadBanner(pm)
            if (d == null) d = info.applicationInfo.loadBanner(pm)
            return d
        }

        fun icon(pm: PackageManager): Drawable = info.loadIcon(pm)

        fun launchIntent(): Intent {
            return Intent(Intent.ACTION_MAIN)
                    .addCategory(if (leanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
    }

    private val pm: PackageManager = c.packageManager
    private val prefs: SharedPreferences = Prefs.of(c)
    private val all: MutableMap<String, App> = LinkedHashMap()

    fun pm(): PackageManager = pm

    fun reload() {
        val found = scan(pm)
        all.clear()
        all.putAll(found)
        for (a in all.values) a.launches = prefs.getInt("n:" + a.pkg, 0)
    }

    fun get(pkg: String?): App? = if (pkg == null) null else all[pkg]

    // ---- dock ----

    fun dockOrder(): MutableList<String> {
        val s = prefs.getString("dock", null)
        return if (s == null) ArrayList(DEFAULT_DOCK) else ArrayList(Arrays.asList(*TextUtils.split(s, ",")))
    }

    private fun saveDock(d: List<String>) { prefs.edit().putString("dock", TextUtils.join(",", d)).apply() }

    fun dock(): MutableList<App> {
        val out: MutableList<App> = ArrayList()
        for (p in dockOrder()) if (all.containsKey(p)) out.add(all[p]!!)
        return out
    }

    fun inDock(pkg: String): Boolean = dockOrder().contains(pkg)

    fun toggleDock(pkg: String) {
        val d = dockOrder()
        if (!d.remove(pkg)) d.add(pkg)
        saveDock(d)
    }

    /** Move within the dock by delta, skipping entries for apps that aren't installed. */
    fun move(pkg: String, delta: Int) {
        val d = dockOrder()
        d.retainAll(all.keys)
        val i = d.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j < 0 || j >= d.size) return
        Collections.swap(d, i, j)
        saveDock(d)
    }

    // ---- hidden ----

    fun hidden(): MutableSet<String> = HashSet(prefs.getStringSet("hidden", Collections.emptySet())!!)

    fun setHidden(pkg: String, hide: Boolean) {
        val h = hidden()
        if (hide) h.add(pkg) else h.remove(pkg)
        prefs.edit().putStringSet("hidden", h).apply()
    }

    fun hiddenApps(): MutableList<App> {
        val out: MutableList<App> = ArrayList()
        for (p in hidden()) if (all.containsKey(p)) out.add(all[p]!!)
        return out
    }

    /** Everything not in the dock and not hidden, most-launched first. */
    fun rest(): MutableList<App> {
        val skip = hidden()
        skip.addAll(dockOrder())
        val out: MutableList<App> = ArrayList()
        for (a in all.values) if (!skip.contains(a.pkg)) out.add(a)
        Collections.sort(out) { a, b ->
            if (a.launches != b.launches) Integer.compare(b.launches, a.launches)
            else a.label.compareTo(b.label, ignoreCase = true)
        }
        return out
    }

    fun countLaunch(a: App) {
        a.launches++
        prefs.edit().putInt("n:" + a.pkg, a.launches).apply()
    }

    companion object {
        @JvmField
        val DEFAULT_DOCK: List<String> = Arrays.asList(
                "com.netflix.ninja",
                "com.google.android.youtube.tv",
                "com.amazon.amazonvideo.livingroom",
                "com.plexapp.android",
                "com.apple.atve.androidtv.appletv")

        /** System pieces that show up as "apps" but belong in the quick row or nowhere. */
        @JvmField
        val NOT_APPS: Set<String> = HashSet(Arrays.asList(
                "home.aurora",
                "com.android.tv.settings",
                "com.tcl.suspension",
                Habits.LIVE_TV,
                "com.spocky.projengmenu",
                "com.google.android.apps.tv.launcherx",
                "com.google.android.tungsten.setupwraith"))

        // The installed-app scan (labels and all) is shared by the home screen, the service
        // and the phone remote, and only redone when apps change, or every 10 minutes at most.
        private var scanned: Map<String, App>? = null
        private var scannedAt = 0L
        @Volatile private var dirty = true
        private const val RESCAN_MS = 10 * 60_000L

        /** Apps were installed, removed or updated: the next reload scans again. */
        @JvmStatic
        fun invalidate() { dirty = true }

        @Synchronized
        private fun scan(pm: PackageManager): Map<String, App> {
            val now = android.os.SystemClock.elapsedRealtime()
            val s = scanned
            if (s != null && !dirty && now - scannedAt < RESCAN_MS) return s
            dirty = false
            val m: MutableMap<String, App> = LinkedHashMap()
            add(pm, m, Intent.CATEGORY_LEANBACK_LAUNCHER, true)
            add(pm, m, Intent.CATEGORY_LAUNCHER, false)
            val result = java.util.Collections.unmodifiableMap(m)
            scanned = result
            scannedAt = now
            return result
        }

        private fun add(pm: PackageManager, into: MutableMap<String, App>, category: String, leanback: Boolean) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val p = ri.activityInfo.packageName
                if (NOT_APPS.contains(p) || into.containsKey(p)) continue
                into[p] = App(ri, pm, leanback)
            }
        }
    }
}
