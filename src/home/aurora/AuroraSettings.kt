package home.aurora

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import java.util.Collections

/** The Settings panel and its pages. Lives with the home screen it changes. */
class AuroraSettings(private val home: HomeActivity) {

    companion object {
        private fun svc(): AuroraService? = AuroraService.instance

        private val CLOCKS = arrayOf("auto", "12", "24")
        private val EVERY = intArrayOf(1, 5, 15, 60, 0)   // minutes, 0 = never

        private val NIGHT_STARTS = intArrayOf(19, 20, 21, 22, 23, 0, 1)
        private val NIGHT_ENDS = intArrayOf(5, 6, 7, 8, 9)
        private val LEVELS = intArrayOf(20, 35, 50)

        private fun cycle(values: IntArray, cur: Int): Int {
            for (i in values.indices) if (values[i] == cur) return values[(i + 1) % values.size]
            return values[0]
        }

        @JvmStatic
        fun hour(h: Int): String = "" + (if (h % 12 == 0) 12 else h % 12) + (if (h < 12) " am" else " pm")

        /** "11:30 pm" from minutes after midnight. */
        @JvmStatic
        fun time(minutes: Int): String {
            val h = minutes / 60
            val m = minutes % 60
            return "" + (if (h % 12 == 0) 12 else h % 12) + ":" + (if (m < 10) "0" else "") + m + (if (h < 12) " am" else " pm")
        }

        /** Accessibility settings, where the background service ("Aurora Remote & Night Mode") is switched on. */
        @JvmStatic
        fun openServiceSettings(home: HomeActivity) {
            try {
                home.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Toast.makeText(home, "Turn on \"Aurora Remote & Night Mode\"", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(home, "Open Settings → Accessibility on the TV", Toast.LENGTH_LONG).show()
            }
        }

        private fun plural(n: Int, word: String): String = "$n " + word + (if (n == 1) "" else "s")

        private fun tcl(cls: String): ComponentName = ComponentName("com.android.tv.settings", cls)
    }

    private val prefs: SharedPreferences = Prefs.of(home)

    fun show() {
        val svc = svc()
        val h: Habits? = svc?.habits
        val items = ArrayList<GlassMenu.Item>()
        items.add(GlassMenu.Item.action("Theme  ·  " + home.theme!!.name) { themes() })
        items.add(GlassMenu.Item.action("Home screen") { homeScreen() })
        items.add(GlassMenu.Item.action("Weather") { weather() })
        if (svc != null) {
            items.add(GlassMenu.Item.action("Bedtime and night mode") { bedtime() })
            items.add(GlassMenu.Item.action("Morning screen" + (if (Morning.on(home)) "  ·  " + time(Morning.at(home)) else "")) { morning() })
            items.add(GlassMenu.Item.action("Kid lock") { home.withPin { kidLock() } })
            items.add(GlassMenu.Item.action("Phone remote") { home.withPin { phoneRemote() } })
        }
        items.add(GlassMenu.Item.action("Security") { security() })
        items.add(GlassMenu.Item.action("Back up and restore") {
            GlassMenu.message(home, "Back up and restore",
                "Your settings are saved to your phone, not the TV, so they survive a reset.\n\n" +
                    "On your phone, open the phone remote and go to Backup:\n" +
                    "·  Download backup saves a file with your theme, home-screen apps, Kid lock, bedtime, morning screen and everything else.\n" +
                    "·  Restore from file sends one back. The TV shows what's in it and asks before changing anything.\n\n" +
                    "Paired phones aren't in the backup: phones already paired with this TV stay paired, and on a new or reset TV they pair again.", null)
        })
        items.add(GlassMenu.Item.choice("Screensaver",
            { if (prefs.getBoolean("dream_art", false)) "Paintings" else "Wallpapers" },
            { prefs.edit().putBoolean("dream_art", !prefs.getBoolean("dream_art", false)).apply() }))
        if (h != null) {
            items.add(GlassMenu.Item.toggle("Open the last app when the TV turns on", h::resume) { on -> h.set(null, null, null, on) })
            items.add(GlassMenu.Item.toggle("Remember the volume for each app", h::perAppVolume) { on -> h.set(on, null, null, null) })
            items.add(GlassMenu.Item.toggle("Turn on at a safe volume (" + h.safeLevel() + "%)", h::safeStart) { on -> h.set(null, on, null, null) })
            AuroraService.instance?.let { sv ->
                items.add(GlassMenu.Item.toggle("Gentle volume: holding the volume button changes it steadily", sv::gentleVolume, sv::setGentleVolume))
            }
        }
        items.add(GlassMenu.Item.toggle("Interface sounds", { Sounds.enabled(home) }) { on -> Sounds.setEnabled(home, on) })
        items.add(GlassMenu.Item.choice("Interface sound volume", { Sounds.levelName(home) }) { Sounds.nextLevel(home) })
        items.add(GlassMenu.Item.action("All apps") { home.showAllApps() })
        val hidden = home.apps!!.hiddenApps().size
        if (hidden > 0) items.add(GlassMenu.Item.action("Hidden apps ($hidden)") { home.showHidden() })
        items.add(GlassMenu.Item.action("TV settings") { tvSettings() })
        items.add(GlassMenu.Item.action("TV info") { tvInfo() })
        GlassMenu.panel(home, "Settings", items)
    }

    // ------------------------------------------------------------------ theme

    /** Pick a look for the home screen and menus. The games keep theirs. */
    private fun themes() {
        val cur = prefs.getString("theme", "aurora")
        val items = ArrayList<GlassMenu.Item>()
        for (i in HomeTheme.IDS.indices) {
            val id = HomeTheme.IDS[i]
            items.add(GlassMenu.Item.action(HomeTheme.NAMES[i] + (if (id == cur) "  ✓" else "")) {
                prefs.edit().putString("theme", id).apply()
                home.applyTheme()
            })
        }
        items.add(GlassMenu.Item.toggle("Holiday looks (Halloween, Christmas, New Year's, 4th of July)",
            { prefs.getBoolean("holidays", true) },
            { on -> prefs.edit().putBoolean("holidays", on).apply(); home.applyTheme() }))
        GlassMenu.panel(home, "Theme", items)
    }

    // ------------------------------------------------------------------ home screen

    private fun homeScreen() {
        val items = ArrayList<GlassMenu.Item>()
        items.add(GlassMenu.Item.action("Apps on the home screen") { chooseApps() })
        items.add(GlassMenu.Item.action("Change their order") { reorder() })
        items.add(GlassMenu.Item.choice("Clock", {
            val c = prefs.getString("clock", "auto")!!
            if (c == "12") "12-hour" else if (c == "24") "24-hour" else "Automatic"
        }, {
            prefs.edit().putString("clock", CLOCKS[(java.util.Arrays.asList(*CLOCKS).indexOf(prefs.getString("clock", "auto")) + 1) % 3]).apply()
            home.applyHomePrefs()
        }))
        items.add(GlassMenu.Item.toggle("Show the date", { prefs.getBoolean("show_date", true) },
            { on -> prefs.edit().putBoolean("show_date", on).apply(); home.applyHomePrefs() }))
        items.add(GlassMenu.Item.toggle("Show the weather", { prefs.getBoolean("show_weather", true) },
            { on -> prefs.edit().putBoolean("show_weather", on).apply(); home.applyHomePrefs() }))
        items.add(GlassMenu.Item.action("Wallpapers") { wallpapers() })
        items.add(GlassMenu.Item.choice("Change wallpaper", {
            val m = prefs.getInt("wp_every", 5)
            if (m == 0) "Never" else if (m == 60) "Every hour" else "Every $m min"
        }, {
            var i = 0
            val m = prefs.getInt("wp_every", 5)
            while (i < EVERY.size && EVERY[i] != m) i++
            prefs.edit().putInt("wp_every", EVERY[(i + 1) % EVERY.size]).apply()
            home.applyHomePrefs()
        }))
        GlassMenu.panel(home, "Home screen", items)
    }

    private fun everyApp(): MutableList<Apps.App> {
        val list = ArrayList<Apps.App>(home.apps!!.dock())
        list.addAll(home.apps!!.rest())
        Collections.sort(list) { a, b -> a.label.compareTo(b.label, ignoreCase = true) }
        return list
    }

    private fun chooseApps() {
        val list = everyApp()
        val names = Array(list.size) { "" }
        val on = BooleanArray(list.size)
        for (i in names.indices) { names[i] = list[i].label; on[i] = home.apps!!.inDock(list[i].pkg) }
        GlassMenu.toggles(home, "Apps on the home screen", names, on) { which, now ->
            home.apps!!.toggleDock(list[which].pkg)
            home.rebuild()
        }
    }

    private fun reorder() {
        val dock = home.apps!!.dock()
        val names = Array(dock.size) { "" }
        for (i in names.indices) names[i] = "${i + 1}.  " + dock[i].label
        GlassMenu.list(home, "Pick an app to move", names) { which -> move(dock[which].pkg) }
    }

    /** Keep nudging one app left or right until Done. */
    private fun move(pkg: String) {
        val dock = home.apps!!.dock()
        var at = 0
        while (at < dock.size && dock[at].pkg != pkg) at++
        val a = home.apps!!.get(pkg)!!
        GlassMenu.list(home, a.label + "  ·  place " + (at + 1) + " of " + dock.size,
            arrayOf("Move left", "Move right", "Done")) { which ->
            if (which == 2) { reorder(); return@list }
            home.apps!!.move(pkg, if (which == 0) -1 else 1)
            home.rebuild()
            move(pkg)
        }
    }

    private fun wallpapers() {
        val titles = home.wall!!.titles()
        val off = WallpaperView.off(home)
        val names = titles.toTypedArray()
        val on = BooleanArray(names.size)
        for (i in names.indices) on[i] = !off.contains(names[i])
        GlassMenu.toggles(home, "Wallpapers", names, on) { which, now ->
            val o = WallpaperView.off(home)
            if (now) o.remove(names[which]) else o.add(names[which])
            prefs.edit().putStringSet("wp_off", o).apply()
        }
    }

    // ------------------------------------------------------------------ weather

    private fun weather() {
        val items = ArrayList<GlassMenu.Item>()
        items.add(GlassMenu.Item.choice("Temperature", { if (Weather.fahrenheit(home)) "°F" else "°C" }, {
            Weather.setFahrenheit(home, !Weather.fahrenheit(home))
            HomeActivity.refreshWeather()
        }))
        val place = prefs.getString("w_place", "")!!
        items.add(GlassMenu.Item.action("City" + (if (place.isEmpty()) "" else "  ·  $place")) {
            GlassMenu.input(home, "City or ZIP code", "e.g. Columbus or 43215") { q ->
                Thread({
                    var msg: String?
                    try { msg = "Weather for " + Weather.setPlace(home, q) }
                    catch (e: Exception) { msg = if (e.message != null && e.message!!.startsWith("Couldn't")) e.message else "Couldn't look that up" }
                    val m = msg
                    home.runOnUiThread { toast(m); HomeActivity.refreshWeather() }
                }, "place").start()
            }
        })
        items.add(GlassMenu.Item.action("Use the TV's location") {
            Weather.useMyLocation(home)
            HomeActivity.refreshWeather()
            toast("Finding your location…")
        })
        GlassMenu.panel(home, "Weather", items)
    }

    // ------------------------------------------------------------------ bedtime

    private fun bedtime() {
        val s = svc() ?: return
        val items = ArrayList<GlassMenu.Item>()
        items.add(GlassMenu.Item.toggle("Night mode (dims and warms the screen)", s::nightEnabled) { on ->
            s.setNight(on, s.nightStart(), s.nightEnd(), s.nightLevel()) })
        items.add(GlassMenu.Item.choice("Night mode starts", { hour(s.nightStart()) },
            { s.setNight(s.nightEnabled(), cycle(NIGHT_STARTS, s.nightStart()), s.nightEnd(), s.nightLevel()) }))
        items.add(GlassMenu.Item.choice("Night mode ends", { hour(s.nightEnd()) },
            { s.setNight(s.nightEnabled(), s.nightStart(), cycle(NIGHT_ENDS, s.nightEnd()), s.nightLevel()) }))
        items.add(GlassMenu.Item.choice("How dim", { if (s.nightLevel() <= 20) "Light" else if (s.nightLevel() <= 35) "Medium" else "Strong" },
            { s.setNight(s.nightEnabled(), s.nightStart(), s.nightEnd(), cycle(LEVELS, s.nightLevel())) }))
        items.add(GlassMenu.Item.toggle("Wind down: the last 5 minutes of a sleep timer fade the volume and dim the screen", s::windDownOn, s::setWindDown))
        items.add(GlassMenu.Item.toggle("Turn the TV off at bedtime", s::bedtimeOn) { on -> s.setBedtime(on, s.bedtime()) })
        items.add(GlassMenu.Item.choice("Bedtime", { time(s.bedtime()) }, {
            var m = s.bedtime() + 30   // 9:00 pm to 1:00 am, in half hours
            if (m >= 25 * 60) m = 21 * 60 else if (m >= 24 * 60) m -= 24 * 60 else if (m > 60 && m < 21 * 60) m = 21 * 60
            s.setBedtime(s.bedtimeOn(), m)
        }))
        GlassMenu.panel(home, "Bedtime", items)
    }

    // ------------------------------------------------------------------ morning screen

    private fun morning() {
        val items = ArrayList<GlassMenu.Item>()
        items.add(GlassMenu.Item.toggle("Wake the TV to the morning screen", { Morning.on(home) },
            { on -> Morning.set(home, on, Morning.at(home), Morning.days(home)) }))
        items.add(GlassMenu.Item.choice("Time", { time(Morning.at(home)) }, {
            var m = Morning.at(home) + 15   // 5:00 to 10:00 am, in quarter hours
            if (m > 10 * 60) m = 5 * 60
            Morning.set(home, Morning.on(home), m, Morning.days(home))
        }))
        items.add(GlassMenu.Item.choice("Days", { Morning.DAYS[Morning.days(home)] },
            { Morning.set(home, Morning.on(home), Morning.at(home), (Morning.days(home) + 1) % Morning.DAYS.size) }))
        val cal = prefs.getString("cal_url", "")!!
        items.add(GlassMenu.Item.action(if (cal.isEmpty()) "Add a calendar (iCal link)" else "Calendar  ·  linked") {
            GlassMenu.input(home, "Calendar link (iCal / .ics)", "Google Calendar: Settings → your calendar → Secret address in iCal format") { url ->
                prefs.edit().putString("cal_url", url.trim { it <= ' ' }).apply()
                toast(if (url.trim { it <= ' ' }.isEmpty()) "Calendar removed" else "Calendar saved")
            }
        })
        if (cal.isNotEmpty()) items.add(GlassMenu.Item.action("Remove the calendar") { prefs.edit().remove("cal_url").apply(); toast("Calendar removed") })
        items.add(GlassMenu.Item.action("Show it now") { Morning.show(home, false) })
        GlassMenu.panel(home, "Morning screen", items)
    }

    // ------------------------------------------------------------------ kid lock

    private fun kidLock() {
        val s = svc() ?: return
        if (!Pin.isSet(prefs)) { newPin { kidLock() }; return }
        val items = ArrayList<GlassMenu.Item>()
        items.add(GlassMenu.Item.toggle("Kid lock", s::kidLockOn) { on -> prefs.edit().putBoolean("lock_on", on).apply(); s.relock() })
        items.add(GlassMenu.Item.action("Locked apps (" + s.lockedApps().size + ")") { lockedApps() })
        items.add(GlassMenu.Item.action("Change PIN") { newPin { kidLock() } })
        items.add(GlassMenu.Item.action("Lock everything again now") { s.relock(); toast("Locked") })
        GlassMenu.panel(home, "Kid lock", items)
    }

    private fun lockedApps() {
        val s = svc()!!
        val list = everyApp()
        val locked = s.lockedApps()
        val names = Array(list.size) { "" }
        val on = BooleanArray(list.size)
        for (i in names.indices) { names[i] = list[i].label; on[i] = locked.contains(list[i].pkg) }
        GlassMenu.toggles(home, "Ask for the PIN to open", names, on) { which, now ->
            val l = s.lockedApps()
            if (now) l.add(list[which].pkg) else l.remove(list[which].pkg)
            prefs.edit().putStringSet("locked", l).putBoolean("lock_on", true).apply()
        }
    }

    private fun newPin(then: Runnable) {
        GlassMenu.pin(home, "Choose a 4-digit PIN") { first ->
            GlassMenu.pin(home, "Enter it again") { second ->
                if (first != second) { toast("The PINs didn't match"); newPin(then); return@pin }
                Pin.set(prefs, first)
                toast("PIN saved")
                then.run()
            }
        }
    }

    // ------------------------------------------------------------------ phone remote

    private fun phoneRemote() {
        val s = svc() ?: return
        val items = ArrayList<GlassMenu.Item>()
        items.add(GlassMenu.Item.toggle("Phone remote", s::remoteEnabled) { on -> s.setRemote(on); home.applyHomePrefs() })
        items.add(GlassMenu.Item.toggle("Arrows in every app (YouTube, Netflix…)", s::fullRemote) { on -> fullRemote(on) })
        items.add(GlassMenu.Item.action("Connect a phone") { connectPhone() })
        items.add(GlassMenu.Item.action("Paired phones (" + Security.phones(prefs, false).size + ")") { pairedPhones() })
        items.add(GlassMenu.Item.action("Forget all phones") {
            Security.forgetAll(prefs)
            toast("Phones will need to connect again")
        })
        GlassMenu.panel(home, "Phone remote", items)
    }

    private fun connectPhone() {
        val s = svc()
        val ip = HomeActivity.lanAddress()
        if (s == null || !s.remoteEnabled() || s.remote() == null) { toast("Turn on the phone remote first"); return }
        if (ip == null) { toast("The TV isn't on Wi-Fi"); return }
        val code = s.remote()!!.newPairCode()
        val addr = ip + ":" + AuroraService.PORT
        val pin = Pin.isSet(prefs)
        GlassMenu.qr(home, "Connect a phone", "http://" + addr + "/#code=" + code + (if (pin) "&pin=1" else ""),
            "Scan with your phone's camera.\nOr open " + addr + " on your phone and enter " + code + "." +
                (if (pin) "\nYou'll also need the family PIN." else "") + "\nYour phone needs to be on the same Wi-Fi.")
    }

    // ------------------------------------------------------------------ security

    private fun allApps(): MutableList<Apps.App> {
        val list = everyApp()
        list.addAll(home.apps!!.hiddenApps())
        return list
    }

    /** Everything that's open to other people, each with its fix. */
    private fun security() {
        val apps = allApps()
        Thread({
            val r = Security.check(home, apps)
            home.runOnUiThread { if (!home.isFinishing) security(r) }
        }, "security").start()
    }

    private fun security(r: Security.Report) {
        val items = ArrayList<GlassMenu.Item>()
        if (r.serviceOff) items.add(GlassMenu.Item.action("⚠  Aurora's background service is off: bedtime, the sleep timer and the phone remote aren't working, and Kid lock only covers the home screen") { openServiceSettings(home) })
        if (r.networkDebugging && r.forFullRemote) {
            items.add(GlassMenu.Item.action("•  Network debugging is on for Arrows in every app: only choose Allow on the TV for Aurora and your own computer") { networkDebugging() })
        } else if (r.networkDebugging) {
            items.add(GlassMenu.Item.action("⚠  Network debugging is ON: anyone on your Wi-Fi can install apps on this TV and control it") { networkDebugging() })
        } else {
            items.add(GlassMenu.Item.action("✓  Network debugging is off" + (if (r.usbDebugging) "  ·  USB debugging is on" else "")) { networkDebugging() })
        }
        if (r.sideloaded.isEmpty()) items.add(GlassMenu.Item.action("✓  Every app came from the Play Store") { security() })
        else items.add(GlassMenu.Item.action("⚠  " + plural(r.sideloaded.size, "app") + " from outside the Play Store") { sideloaded(r) })
        if (r.installers.isEmpty()) items.add(GlassMenu.Item.action("✓  No app can install other apps") { security() })
        else items.add(GlassMenu.Item.action("⚠  " + plural(r.installers.size, "app") + " can install other apps") { installers(r) })
        val phones = Security.phones(prefs, true).size
        items.add(GlassMenu.Item.action("Paired phones ($phones)") { pairedPhones() })
        items.add(GlassMenu.Item.toggle("Ask for the PIN to open Settings", { prefs.getBoolean("settings_pin", false) && Pin.isSet(prefs) }) { on ->
            if (!on) { prefs.edit().putBoolean("settings_pin", false).apply(); return@toggle }
            if (Pin.isSet(prefs)) { prefs.edit().putBoolean("settings_pin", true).apply(); toast("Settings now ask for the PIN"); return@toggle }
            GlassMenu.dismissAll()
            newPin { prefs.edit().putBoolean("settings_pin", true).apply(); toast("Settings now ask for the PIN") }
        })
        if (Pin.isSet(prefs)) items.add(GlassMenu.Item.action("Change PIN (also used by Kid lock)") { newPin { security() } })
        val w = r.warnings()
        GlassMenu.panel(home, if (w == 0) "Security  ·  all clear" else "Security  ·  " + plural(w, "warning"), items)
    }

    private fun networkDebugging() {
        val items = ArrayList<GlassMenu.Item>()
        items.add(GlassMenu.Item.action("Open Developer options to turn it off") { open(null, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) })
        items.add(GlassMenu.Item.action("What this means") {
            GlassMenu.message(home, "Network debugging",
                "With network debugging on, any computer on your Wi-Fi can connect to this TV on port " + Security.ADB_PORT +
                    " without a password. It can install apps, read the screen and press buttons.\n\n" +
                    "Turn it off in Developer options (USB debugging / Network debugging) when you aren't developing. " +
                    "Updating Aurora Home from a computer needs it on again.") { security() }
        })
        items.add(GlassMenu.Item.action("Back") { security() })
        GlassMenu.panel(home, "Network debugging", items)
    }

    private fun sideloaded(r: Security.Report) {
        val names = Array(r.sideloaded.size) { "" }
        for (i in names.indices) names[i] = r.sideloaded[i].label + "  ·  " + r.sideloadedBy[i]
        GlassMenu.list(home, "From outside the Play Store  ·  OK for app info", names) { which -> appInfo(r.sideloaded[which].pkg) }
    }

    private fun installers(r: Security.Report) {
        val names = Array(r.installers.size) { "" }
        for (i in names.indices) names[i] = r.installers[i].label
        GlassMenu.list(home, "Allowed to install apps  ·  OK to change", names) { which ->
            val pkg = r.installers[which].pkg
            try {
                home.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                appInfo(pkg)
            }
        }
    }

    private fun appInfo(pkg: String) {
        try {
            home.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("Not available on this TV")
        }
    }

    /**
     * The phone's arrows reach the home screen and most apps through accessibility. Apps that draw
     * their own screens need real key presses, which go through the TV's network debugging: the TV
     * asks once whether to allow Aurora.
     */
    private fun fullRemote(on: Boolean) {
        val s = svc() ?: return
        if (!on) { s.setFullRemote(false); return }
        GlassMenu.message(home, "Arrows in every app",
            "The phone's arrows already work on this home screen and in most apps. To also reach apps that draw their own " +
            "screens, like YouTube and Netflix, Aurora presses the buttons through the TV's network debugging.\n\n" +
            "The TV will ask \"Allow debugging?\" — choose Always allow, then Allow.\n\n" +
            "Network debugging has to stay on for this. Other devices on your Wi-Fi can then ask to connect too: " +
            "if the TV ever asks \"Allow debugging?\" when you didn't expect it, choose Cancel.") {
            s.setFullRemote(true)
            s.adb().connect { ok ->
                home.runOnUiThread {
                    if (ok) toast("The phone's arrows now work in every app")
                    else { s.setFullRemote(false); toast(s.adb().problem ?: "Couldn't connect") }
                }
            }
        }
    }

    /** Every phone that can use the phone remote; pick one to forget it. */
    private fun pairedPhones() {
        val phones = Security.phones(prefs, true)
        if (phones.isEmpty()) {
            GlassMenu.message(home, "Paired phones", "No phones are paired. Use Settings → Phone remote → Connect a phone.", null)
            return
        }
        val names = Array(phones.size) { "" }
        for (i in names.indices) {
            val p = phones[i]
            names[i] = p.name + (if (p.ip.isEmpty()) "" else "  ·  " + p.ip) + "  ·  last used " + Security.ago(p.lastUsed)
        }
        GlassMenu.list(home, "Paired phones  ·  OK to forget one", names) { which ->
            val p = phones[which]
            val items = ArrayList<GlassMenu.Item>()
            items.add(GlassMenu.Item.action("Forget " + p.name + "  (it will need the code again)") {
                Security.forget(prefs, p.token)
                toast(p.name + " forgotten")
                pairedPhones()
            })
            items.add(GlassMenu.Item.action("Keep it") { pairedPhones() })
            GlassMenu.panel(home, p.name + (if (p.paired > 0) "  ·  paired " + Security.ago(p.paired) else ""), items)
        }
    }

    // ------------------------------------------------------------------ TV settings shortcuts

    private fun tvSettings() {
        val items = ArrayList<GlassMenu.Item>()
        items.add(GlassMenu.Item.action("Wi-Fi") { open(null, Settings.ACTION_WIFI_SETTINGS) })
        items.add(GlassMenu.Item.action("Bluetooth (headphones, speakers)") {
            open(tcl("com.tcl.settings.accessories.AccessoriesActivity"), Settings.ACTION_BLUETOOTH_SETTINGS)
        })
        items.add(GlassMenu.Item.action("Picture") { open(tcl("com.tcl.settings.PictureActivity"), Settings.ACTION_DISPLAY_SETTINGS) })
        items.add(GlassMenu.Item.action("Sound") { open(tcl("com.tcl.settings.SoundActivity"), Settings.ACTION_SOUND_SETTINGS) })
        items.add(GlassMenu.Item.action("Power and sleep") { open(null, "com.android.tv.settings.action.ENERGY_SAVING") })
        items.add(GlassMenu.Item.action("All TV settings") { open(null, Settings.ACTION_SETTINGS) })
        GlassMenu.panel(home, "TV settings", items)
    }

    /** Try the TCL screen first, then the standard Android one. */
    private fun open(exact: ComponentName?, action: String) {
        if (exact != null) {
            try {
                home.startActivity(Intent().setComponent(exact).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (ignored: Exception) { }
        }
        try {
            home.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try { home.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (e2: Exception) { toast("Not available on this TV") }
        }
    }

    // ------------------------------------------------------------------ TV info

    @Suppress("DEPRECATION")
    fun tvInfo() {
        val b = StringBuilder()
        val ip = HomeActivity.lanAddress()
        try {
            val wm = home.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val w = wm.connectionInfo
            if (w != null && w.networkId != -1) {
                val bars = WifiManager.calculateSignalLevel(w.rssi, 5)
                val words = arrayOf("Very weak", "Weak", "OK", "Good", "Excellent")
                b.append("Wi-Fi signal:  ").append(words[Math.max(0, Math.min(4, bars))]).append("\n")
            } else b.append("Wi-Fi:  not connected (or using a cable)\n")
        } catch (e: Exception) {
            b.append("Wi-Fi signal:  unknown\n")
        }
        b.append("IP address:  ").append(ip ?: "none").append("\n")
        val fs = StatFs(Environment.getDataDirectory().path)
        b.append("Free storage:  ").append(Formatter.formatShortFileSize(home, fs.availableBytes))
            .append(" of ").append(Formatter.formatShortFileSize(home, fs.totalBytes)).append("\n")
        b.append("TV:  ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
            .append("  ·  Android ").append(Build.VERSION.RELEASE).append("\n")
        var ver: String? = ""
        try { ver = home.packageManager.getPackageInfo(home.packageName, 0).versionName } catch (ignored: Exception) { }
        b.append("Aurora:  version ").append(ver)
        GlassMenu.message(home, "TV info", b.toString(), null)
    }

    private fun toast(s: String?) { Toast.makeText(home, s, Toast.LENGTH_SHORT).show() }
}
