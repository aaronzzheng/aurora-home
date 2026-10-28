package home.aurora

import android.app.AppOpsManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What on this TV is open to other people: network debugging, apps from outside
 * the Play Store, apps allowed to install other apps, and the phones paired with
 * the phone remote. Checks only; the fixes live in TV settings or the panel.
 */
object Security {

    const val ADB_PORT = 5555
    private const val PLAY = "com.android.vending"
    private val lock = Any()   // guards the paired-phone records

    class Report {
        @JvmField var networkDebugging = false
        @JvmField var usbDebugging = false
        @JvmField var serviceOff = false
        @JvmField val sideloaded = mutableListOf<Apps.App>()
        @JvmField val installers = mutableListOf<Apps.App>()
        @JvmField val sideloadedBy = mutableListOf<String>()

        fun warnings(): Int = listOf(serviceOff, networkDebugging, sideloaded.isNotEmpty(), installers.isNotEmpty()).count { it }
    }

    /** Run the checks. Opens a socket, so call it off the main thread. */
    @JvmStatic
    fun check(c: Context, apps: List<Apps.App>): Report {
        val r = Report()
        val cr = c.contentResolver
        r.serviceOff = !serviceEnabled(c)
        r.usbDebugging = Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1
        // "adb tcpip 5555" listens on every interface; Android 11+ wireless debugging has its own flag.
        r.networkDebugging = r.usbDebugging && (Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1 || listening(ADB_PORT))
        val pm = c.packageManager
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        for (a in apps) {
            if (a.pkg == c.packageName) continue   // this launcher is sideloaded on purpose
            val info = a.info.applicationInfo
            if (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) continue
            val installer = installer(pm, a.pkg)
            if (installer != PLAY) {
                r.sideloaded += a
                r.sideloadedBy += if (installer == null) "installed by a computer (ADB) or a file" else "installed by " + labelOf(pm, installer)
            }
            if (canInstall(pm, ops, a.pkg, info.uid)) r.installers += a
        }
        return r
    }

    /**
     * Is Aurora's background service (an accessibility service) switched on? Kid lock,
     * bedtime, the sleep timer, night mode and the phone remote all live in it.
     */
    @JvmStatic
    fun serviceEnabled(c: Context): Boolean {
        if (AuroraService.instance != null) return true
        val on = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return on != null && on.contains(c.packageName + "/")
    }

    private fun listening(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300) }
        true
    } catch (e: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun installer(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
        else pm.getInstallerPackageName(pkg)
    } catch (e: Exception) {
        null
    }

    private fun labelOf(pm: PackageManager, pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    /** Has this app been allowed to "install unknown apps"? */
    @Suppress("DEPRECATION")
    private fun canInstall(pm: PackageManager, ops: AppOpsManager, pkg: String, uid: Int): Boolean = try {
        val requested = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS).requestedPermissions
        if (requested == null || "android.permission.REQUEST_INSTALL_PACKAGES" !in requested) false
        else {
            val op = "android:request_install_packages"
            val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(op, uid, pkg) else ops.checkOpNoThrow(op, uid, pkg)
            mode == AppOpsManager.MODE_ALLOWED
        }
    } catch (e: Exception) {
        false
    }

    // ------------------------------------------------------------------ paired phones
    // The phone remote's tokens live in "remote_tokens" (what the server trusts);
    // "remote_phones" remembers what each one is, so a single phone can be forgotten.

    class Phone(@JvmField val token: String, o: JSONObject) {
        @JvmField val name: String = o.optString("name", "Phone")
        @JvmField val ip: String = o.optString("ip", "")
        @JvmField val paired: Long = o.optLong("paired", 0)
        @JvmField val lastUsed: Long = o.optLong("last", 0)
    }

    private fun records(p: SharedPreferences): JSONObject =
        try { JSONObject(p.getString("remote_phones", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    private fun tokens(p: SharedPreferences): Set<String> = p.getStringSet("remote_tokens", emptySet()) ?: emptySet()

    /** A new phone paired: trust its token and remember what it is. */
    @JvmStatic
    fun paired(p: SharedPreferences, token: String, userAgent: String?, ip: String) = synchronized(lock) {
        val now = System.currentTimeMillis()
        val all = records(p)
        all.put(token, JSONObject().put("name", deviceName(userAgent)).put("ip", ip).put("paired", now).put("last", now))
        p.edit().putStringSet("remote_tokens", tokens(p) + token).putString("remote_phones", all.toString()).apply()
    }

    /** Note that a phone was used; written at most every 10 minutes per phone. */
    @JvmStatic
    fun used(p: SharedPreferences, token: String, ip: String) = synchronized(lock) {
        val all = records(p)
        val now = System.currentTimeMillis()
        val o = all.optJSONObject(token)
        if (o != null && now - o.optLong("last", 0) < 10 * 60_000 && ip == o.optString("ip")) return@synchronized
        // A phone paired before phones were remembered gets a record the first time it's used.
        val rec = (o ?: JSONObject().put("name", "Phone").put("paired", 0)).put("last", now).put("ip", ip)
        all.put(token, rec)
        p.edit().putString("remote_phones", all.toString()).apply()
    }

    /** Paired phones, most recently used first. */
    @JvmStatic
    fun phones(p: SharedPreferences, prune: Boolean): MutableList<Phone> = synchronized(lock) {
        val tokens = tokens(p)
        val all = records(p)
        val out = mutableListOf<Phone>()
        var changed = false
        val it = all.keys()
        while (it.hasNext()) {
            val t = it.next()
            if (t !in tokens) { it.remove(); changed = true; continue }
            out += Phone(t, all.optJSONObject(t) ?: JSONObject())
        }
        for (t in tokens) if (!all.has(t)) out += Phone(t, JSONObject())   // never seen since phones were remembered
        if (prune && changed) p.edit().putString("remote_phones", all.toString()).apply()
        out.sortByDescending { it.lastUsed }
        out
    }

    @JvmStatic
    fun forget(p: SharedPreferences, token: String) = synchronized(lock) {
        val all = records(p)
        all.remove(token)
        p.edit().putStringSet("remote_tokens", tokens(p) - token).putString("remote_phones", all.toString()).apply()
    }

    @JvmStatic
    fun forgetAll(p: SharedPreferences) = synchronized(lock) {
        p.edit().remove("remote_tokens").remove("remote_phones").apply()
    }

    /** "iPhone", "Pixel 8", "Mac"… from the browser's User-Agent. */
    @JvmStatic
    fun deviceName(ua: String?): String {
        if (ua == null) return "Phone"
        if ("iPhone" in ua) return "iPhone"
        if ("iPad" in ua) return "iPad"
        val a = ua.indexOf("Android")
        if (a >= 0) {
            val semi = ua.indexOf(';', a)
            val end = ua.indexOf(')', a)
            if (semi > 0 && end > semi) {
                var model = ua.substring(semi + 1, end).trim()
                val b = model.indexOf("Build/")   // "Pixel 8 Build/AP1A" → "Pixel 8"
                if (b >= 0) model = model.substring(0, b).trim()
                if (model.length > 1 && !model.equals("wv", ignoreCase = true)) return model
            }
            return "Android phone"
        }
        if ("Macintosh" in ua) return "Mac"
        if ("Windows" in ua) return "Windows PC"
        if ("linux" in ua.lowercase(Locale.ROOT)) return "Linux computer"
        return "Phone"
    }

    /** "just now", "3 hours ago", "Oct 2". */
    @JvmStatic
    fun ago(t: Long): String {
        if (t <= 0) return "not since this update"
        val m = (System.currentTimeMillis() - t) / 60_000
        return when {
            m < 2 -> "just now"
            m < 60 -> "$m minutes ago"
            m < 48 * 60 -> "${m / 60} hours ago"
            m < 14 * 24 * 60 -> "${m / 1440} days ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(t))
        }
    }
}
