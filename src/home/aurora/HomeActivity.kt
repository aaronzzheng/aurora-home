package home.aurora

import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

class HomeActivity : Activity() {

    companion object {
        private var current = WeakReference<HomeActivity>(null)

        /** Called by the phone remote. */
        @JvmStatic
        fun nextWallpaper() {
            val h = current.get()
            if (h != null && h.wall!!.parent != null) h.wall!!.next()
        }

        @JvmStatic
        fun refreshWeather() {
            val h = current.get()
            if (h != null) { h.weatherAt = 0; h.updateWeather() }
        }

        private var serviceWarned = false   // once per run of the app, not on every return home

        @JvmStatic
        fun rememberDirection(code: Int) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { Tile.lastDx = -1; Tile.lastDy = 0 }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { Tile.lastDx = 1; Tile.lastDy = 0 }
                KeyEvent.KEYCODE_DPAD_UP -> { Tile.lastDx = 0; Tile.lastDy = -1 }
                KeyEvent.KEYCODE_DPAD_DOWN -> { Tile.lastDx = 0; Tile.lastDy = 1 }
            }
        }

        /** Apps unlocked from here while the background service was off; cleared when the TV wakes. */
        private val unlockedHere: MutableSet<String> = HashSet()

        /** Four characters for the flip clock: "0942" (24h) or " 942" (12h, blank leading card). */
        @JvmStatic
        fun flipTime(now: Date, h24: Boolean): String {
            val s = SimpleDateFormat(if (h24) "HHmm" else "hhmm", Locale.getDefault()).format(now)
            return if (!h24 && s[0] == '0') " " + s.substring(1) else s
        }

        private fun endAligned(): LinearLayout.LayoutParams {
            val lp = LinearLayout.LayoutParams(-2, -2)
            lp.gravity = Gravity.END
            return lp
        }

        private fun pillText(pill: View?): TextView = (pill as ViewGroup).getChildAt(0) as TextView

        @JvmStatic
        fun lanAddress(): String? {
            try {
                for (n in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (!n.isUp || n.isLoopback) continue
                    for (a in Collections.list(n.inetAddresses))
                        if (a is Inet4Address && a.isSiteLocalAddress) return a.hostAddress
                }
            } catch (ignored: Exception) { }
            return null
        }
    }

    private var THIN: Typeface? = null   // from the theme
    private var LIGHT: Typeface? = null
    private var MEDIUM: Typeface? = null

    @JvmField var apps: Apps? = null
    @JvmField var theme: HomeTheme? = null
    @JvmField var wall: WallpaperView? = null
    private var themeFx: ThemeFx? = null
    private var fx: WeatherFx? = null      // weather under the UI
    private var shine: WeatherFx? = null   // wake-up shimmer over everything
    private var wokeAt = 0L
    private var resumed = false

    /** TV just turned on: sweep the wake-up shimmer across the home screen. */
    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            unlockedHere.clear()
            wokeAt = android.os.SystemClock.uptimeMillis()
            if (resumed) { wokeAt = 0; val s = shine!!; s.postDelayed(s::shimmer, 350) }
        }
    }
    private var clock: ClockFace? = null
    private var date: TextView? = null
    private var focusTitle: TextView? = null
    private var temp: TextView? = null
    private var sky: TextView? = null
    private var weekLine: TextView? = null
    private var remoteLine: TextView? = null
    private var weatherBox: View? = null
    private var sleepPill: View? = null
    private var timerPill: View? = null
    private var gamesPill: View? = null
    private var weatherAt = 0L
    private var dockRow: LinearLayout? = null
    private var quickRow: LinearLayout? = null
    private var scroller: ScrollView? = null
    private var host: View? = null   // posts the timer tick: the scroller, or a room's stage
    private var room: Room? = null   // every look but Aurora and Classic has its own room
    private var lastFocused: String? = null

    private val timeTick = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { updateClock() }
    }

    private val pkgChange = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { Apps.invalidate(); rebuild() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        apps = Apps(this)
        theme = HomeTheme.current(this)
        val theme = theme!!
        THIN = theme.thin
        LIGHT = theme.light
        MEDIUM = theme.medium
        Sounds.setTheme(this, theme.sounds)

        val root = FrameLayout(this)
        room = Room.create(this, theme)
        if (room != null) { buildRoom(root); return }
        val wall = WallpaperView(this, theme.photos)
        this.wall = wall
        if (theme.bg == HomeTheme.BG_DRAWN) wall.setPainted(theme.background())
        root.addView(wall, FrameLayout.LayoutParams(-1, -1))
        fx = WeatherFx(this)
        root.addView(fx, FrameLayout.LayoutParams(-1, -1))
        if (theme.fx != ThemeFx.NONE || theme.wash != 0) {   // a holiday's touches
            val tfx = ThemeFx(this, theme.fx, theme.wash)
            themeFx = tfx
            theme.sparks?.let { tfx.setFireworkColors(*it) }
            root.addView(tfx, FrameLayout.LayoutParams(-1, -1))
        }
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON))
        if (theme.scrimTop != 0) root.addView(scrim(GradientDrawable.Orientation.TOP_BOTTOM, theme.scrimTop), FrameLayout.LayoutParams(-1, dp(200), Gravity.TOP))
        if (theme.scrimBottom != 0) root.addView(scrim(GradientDrawable.Orientation.BOTTOM_TOP, theme.scrimBottom), FrameLayout.LayoutParams(-1, dp(330), Gravity.BOTTOM))

        val scroller = ScrollView(this)
        this.scroller = scroller
        scroller.isVerticalScrollBarEnabled = false
        scroller.clipChildren = false
        scroller.isFillViewport = true
        host = scroller
        root.addView(scroller, FrameLayout.LayoutParams(-1, -1))

        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.clipChildren = false
        page.clipToPadding = false
        page.setPadding(dp(56), dp(26), dp(56), dp(18))
        // The home is a single TV viewport, never a document.  Measuring the page
        // to the viewport lets its deliberate spacers absorb spare room instead of
        // pushing Games and Settings below a vertical scroll.
        scroller.addView(page, FrameLayout.LayoutParams(-1, -1))   // ScrollView.LayoutParams is FrameLayout.LayoutParams

        // --- pieces both layouts use (every other look is a Room) ---
        val desktop = theme.layout == HomeTheme.L_DESKTOP
        val clockCol = LinearLayout(this)
        clockCol.orientation = LinearLayout.VERTICAL
        clockCol.gravity = Gravity.START
        val clockView: View
        if (desktop) {
            clockView = PlainClock(this, 15f, 0xFF000000.toInt(), theme.medium, false, false)
        } else if (theme.clock == HomeTheme.CLOCK_FLIP) {
            val fc = FlipClock(this, 62f)
            fc.setLook(theme.cards, theme.digits, theme.clockFont, theme.clockGlow)
            clockView = fc
        } else {
            val lcd = theme.clock == HomeTheme.CLOCK_LCD
            clockView = PlainClock(this, if (lcd) 44f else 84f, if (lcd) theme.digits else theme.text, theme.clockFont, theme.shadow, lcd)
        }
        clock = clockView as ClockFace
        val date = caps(11f, ink(0.8f))
        this.date = date
        date.setPadding(dp(4), dp(8), 0, 0)
        if (!desktop) clockCol.addView(clockView, LinearLayout.LayoutParams(-2, -2))
        val wrapLp = LinearLayout.LayoutParams(-2, -2)
        clockCol.addView(date, wrapLp)
        if (theme.greeting != null) {
            val hi = caps(11f, theme.accent, theme.greeting)
            hi.setPadding(dp(4), dp(6), 0, 0)
            clockCol.addView(hi, LinearLayout.LayoutParams(wrapLp))
        }
        val weekLine = caps(9f, ink(0.5f))
        this.weekLine = weekLine
        weekLine.setPadding(dp(4), dp(6), 0, 0)
        clockCol.addView(weekLine, LinearLayout.LayoutParams(wrapLp))

        val capCol = LinearLayout(this)
        capCol.orientation = LinearLayout.VERTICAL
        capCol.gravity = Gravity.END
        capCol.setPadding(0, dp(6), 0, 0)
        val wx = LinearLayout(this)
        weatherBox = wx
        wx.gravity = Gravity.CENTER_VERTICAL
        val sky = text(13f, ink(0.8f), LIGHT)
        this.sky = sky
        sky.gravity = Gravity.END
        sky.setPadding(0, 0, dp(12), 0)
        val temp = text(40f, theme.text, THIN)
        this.temp = temp
        temp.includeFontPadding = false
        if (theme.shadow) temp.setShadowLayer(dp(12).toFloat(), 0f, 0f, 0x55000000)
        wx.addView(sky)
        wx.addView(temp)
        capCol.addView(wx, endAligned())
        val remoteLine = caps(9f, ink(0.5f))
        this.remoteLine = remoteLine
        remoteLine.setPadding(0, dp(14), 0, 0)
        capCol.addView(remoteLine, endAligned())

        val focusTitle = text(26f, theme.text, LIGHT)
        this.focusTitle = focusTitle
        if (theme.shadow) focusTitle.setShadowLayer(dp(12).toFloat(), 0f, 0f, 0x66000000)
        focusTitle.setPadding(dp(6), 0, 0, dp(6))

        val glass = GlassView(this, wall, dp(theme.dockRadius).toFloat(), theme.dockTint)
        glass.setStyle(if (desktop) HomeTheme.S_NONE else theme.dock, theme.dockEdge)
        glass.setPadding(dp(if (theme.shelf) 40 else 12), dp(10), dp(if (theme.shelf) 40 else 12), dp(if (theme.shelf) 16 else 10))
        val dockScroll = hscroll()
        val dockRow = row()
        this.dockRow = dockRow
        dockScroll.addView(dockRow)
        glass.addView(dockScroll, FrameLayout.LayoutParams(-2, -2))

        val quickScroll = hscroll()
        val quickRow = row()
        this.quickRow = quickRow
        quickScroll.addView(quickRow)

        if (desktop) {
            // --- Windows 95: a weather window, a Programs window, and the taskbar ---
            for (t in arrayOf(date, weekLine, sky, temp, remoteLine)) { t.setTextColor(0xFF000000.toInt()); t.setShadowLayer(0f, 0f, 0f, 0) }
            sky.setTextColor(0xFF000000.toInt())
            val wbody = LinearLayout(this)
            wbody.orientation = LinearLayout.VERTICAL
            wbody.setPadding(dp(14), dp(10), dp(14), dp(12))
            wbody.addView(capCol)
            wbody.addView(clockCol)
            clockCol.gravity = Gravity.END
            val header = LinearLayout(this)
            val icons = LinearLayout(this)
            icons.orientation = LinearLayout.VERTICAL
            icons.addView(deskIcon(0, "My Computer") { AuroraSettings(this).tvInfo() })
            icons.addView(deskIcon(1, "Control Panel") { showSettings() })
            icons.addView(deskIcon(2, "Recycle Bin") { showHidden() })
            header.addView(icons)
            header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            header.addView(win95Window(text(13f, 0xFFFFFFFF.toInt(), MEDIUM), "Weather", wbody))
            page.addView(header)
            page.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

            focusTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            focusTitle.setTextColor(0xFFFFFFFF.toInt())
            focusTitle.typeface = MEDIUM
            focusTitle.setShadowLayer(0f, 0f, 0f, 0)
            focusTitle.setPadding(0, 0, 0, 0)
            val well = FrameLayout(this)   // the white, pressed-in area icons sit in
            well.background = HomeTheme.Bevel(this, 0xFFFFFFFF.toInt(), true)
            well.setPadding(dp(4), dp(4), dp(4), dp(4))
            well.addView(glass)
            val wl = LinearLayout.LayoutParams(-2, -2)
            wl.setMargins(dp(4), 0, dp(4), dp(4))
            val wrap = LinearLayout(this)
            wrap.addView(well, wl)
            page.addView(win95Window(focusTitle, "Programs", wrap), LinearLayout.LayoutParams(-2, -2))
            page.setPadding(page.paddingLeft, page.paddingTop, page.paddingRight, dp(70))

            val bar = LinearLayout(this)
            bar.gravity = Gravity.CENTER_VERTICAL
            bar.background = HomeTheme.Bevel(this, 0xFFC0C0C0.toInt(), false)
            bar.setPadding(dp(4), dp(3), dp(4), dp(3))
            bar.addView(startButton())
            quickRow.setPadding(dp(2), dp(2), dp(2), dp(2))
            bar.addView(quickScroll, LinearLayout.LayoutParams(0, -2, 1f))
            val tray = LinearLayout(this)
            tray.gravity = Gravity.CENTER_VERTICAL
            tray.background = HomeTheme.Bevel(this, 0xFFC0C0C0.toInt(), true)
            tray.setPadding(dp(12), dp(4), dp(12), dp(4))
            tray.addView(clockView)
            bar.addView(tray)
            root.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        } else {
            // --- clock top-left, weather top-right, apps along the bottom ---
            val header = LinearLayout(this)
            header.addView(clockCol, LinearLayout.LayoutParams(0, -2, 1f))
            header.addView(capCol)
            page.addView(header)
            page.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
            page.addView(focusTitle)
            // Keep everyday destinations in the first reachable row, even on a dense scene.
            val qlp = LinearLayout.LayoutParams(-1, -2)
            qlp.topMargin = dp(10)
            page.addView(quickScroll, qlp)
            page.addView(glass, LinearLayout.LayoutParams(-2, -2))
        }

        shine = WeatherFx(this)
        root.addView(shine, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        current = WeakReference(this)
    }

    /** A dedicated room: its own layout, controls, focus and clock on a fixed 16:9 stage. */
    private fun buildRoom(root: FrameLayout) {
        val theme = theme!!
        val room = room!!
        wall = WallpaperView(this, theme.photos)
        if (theme.bg == HomeTheme.BG_DRAWN) wall!!.setPainted(theme.background())
        fx = WeatherFx(this)
        dockRow = row()
        room.build()
        clock = room.clock
        date = room.date
        weekLine = room.week
        temp = room.temp
        sky = room.sky
        remoteLine = room.remote
        weatherBox = room.weatherBox
        focusTitle = room.focusTitle
        host = room.stage
        root.setBackgroundColor(0xFF000000.toInt())
        root.addView(room.stage, FrameLayout.LayoutParams(Math.round(Room.W * room.k), Math.round(Room.H * room.k), Gravity.CENTER))
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON))
        shine = WeatherFx(this)
        root.addView(shine, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        current = WeakReference(this)
        debugGame(intent)
    }

    /** Preview builds only: `--ei debug_game N` opens that game in the current look. */
    private fun debugGame(i: Intent?) {
        if (i == null || !i.hasExtra("debug_game") || (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        startActivity(Intent(this, GamesActivity::class.java).putExtras(i))
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onStart() {
        super.onStart()
        val tf = IntentFilter(Intent.ACTION_TIME_TICK)
        tf.addAction(Intent.ACTION_TIME_CHANGED)
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        registerReceiver(timeTick, tf)
        val pf = IntentFilter()
        pf.addAction(Intent.ACTION_PACKAGE_ADDED)
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED)
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED)
        pf.addDataScheme("package")
        registerReceiver(pkgChange, pf)
        updateClock()
        rebuild()
        // Coming home: land on the "Back to" tile.
        val first: View? = if (room != null) room!!.firstApp() else if (dockRow!!.childCount > 0) dockRow!!.getChildAt(0) else null
        if ((first is Tile || first is Room.Widget) && first.tag != null &&
            AuroraService.instance != null && AuroraService.instance!!.habits != null &&
            first.tag.equals(AuroraService.instance!!.habits!!.lastApp())
        ) {
            first.requestFocus()
        }
        updateWeather()
        updateWeek()
    }

    override fun onResume() {
        super.onResume()
        Sounds.quietFor(500)   // no click while the screen sets its starting focus
        if (themeChanged()) return
        Sounds.setTheme(this, theme!!.sounds)
        applyHomePrefs()
        // Only the looks that show the wallpaper run it; the rest would decode photos for nothing.
        if (wall!!.parent != null) wall!!.start()
        fx!!.start()
        if (themeFx != null) themeFx!!.start()
        if (room != null) room!!.start()
        resumed = true
        if (Backup.waiting()) shine!!.post { askRestore() }
        shine!!.postDelayed(checkService, 8000)   // give the service time to connect after a boot
        if (android.os.SystemClock.uptimeMillis() - wokeAt < 8000) { wokeAt = 0; val s = shine!!; s.postDelayed(s::shimmer, 350) }
    }

    /** A phone sent a backup: show what's in it and only replace the settings on a yes (and the PIN). */
    private fun askRestore() {
        if (!Backup.waiting()) return
        // Taken only once the PIN is in: a PIN lockout leaves the backup waiting for the next try.
        withPin {
            val b = Backup.take() ?: return@withPin
            val items = ArrayList<GlassMenu.Item>()
            items.add(GlassMenu.Item.action("Restore  ·  replace this TV's settings") {
                try {
                    Backup.apply(this, b)
                    Morning.schedule(this)
                    val s = AuroraService.instance
                    if (s != null) s.setNight(s.nightEnabled(), s.nightStart(), s.nightEnd(), s.nightLevel())
                    Toast.makeText(this, "Settings restored", Toast.LENGTH_LONG).show()
                    applyTheme()
                } catch (e: Exception) {
                    Toast.makeText(this, if (e.message == null) "Couldn't restore" else e.message, Toast.LENGTH_LONG).show()
                }
            })
            items.add(GlassMenu.Item.action("Cancel") { Toast.makeText(this, "Nothing was changed", Toast.LENGTH_SHORT).show() })
            GlassMenu.panel(this, "Restore settings from your phone?\n" + Backup.summary(this, b), items)
        }
    }

    /** Kid lock, bedtime and the sleep timer silently stop if the background service is turned off: say so. */
    private val checkService: Runnable = object : Runnable {
        override fun run() {
            if (!resumed || serviceWarned || Security.serviceEnabled(this@HomeActivity)) return
            if (GlassMenu.anyShowing()) { shine!!.postDelayed(checkService, 5000); return }   // never on top of a PIN pad or menu
            serviceWarned = true
            val items = ArrayList<GlassMenu.Item>()
            items.add(GlassMenu.Item.action("Turn it on  (Accessibility → Aurora Remote & Night Mode)") { AuroraSettings.openServiceSettings(this@HomeActivity) })
            items.add(GlassMenu.Item.action("Not now") { })
            GlassMenu.panel(this@HomeActivity, "Aurora's background service is off  ·  bedtime, the sleep timer, night mode and the phone remote aren't working, and Kid lock only covers apps opened from this screen", items)
        }
    }

    /** Back from an app: the home screen eases in instead of snapping. */
    override fun onRestart() {
        super.onRestart()
        if (room != null) { room!!.onReturn(); return }
        val page = scroller!!.getChildAt(0)
        page.alpha = 0f
        page.scaleX = 1.04f
        page.scaleY = 1.04f
        page.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.8f)).start()
    }

    override fun onPause() {
        resumed = false
        shine!!.removeCallbacks(checkService)
        wall!!.stop()
        fx!!.stop()
        if (themeFx != null) themeFx!!.stop()
        if (room != null) room!!.stop()
        super.onPause()
    }

    override fun onStop() {
        unregisterReceiver(timeTick)
        unregisterReceiver(pkgChange)
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(screenOn)
        super.onDestroy()
    }

    /** Pressing Home while already home jumps back to the first dock tile. */
    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        GlassMenu.dismissAll()
        val locked = i.getStringExtra("unlock")
        if (locked != null) {   // a locked app was opened some other way; ask here
            val a = apps!!.get(locked)
            if (a != null) launch(a, null)
            return
        }
        if (room != null) {
            val first = room!!.firstApp()
            first?.requestFocus()
            return
        }
        scroller!!.smoothScrollTo(0, 0)
        if (dockRow!!.childCount > 0) dockRow!!.getChildAt(0).requestFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* we are home */ }

    /** Remember which way the remote moved, so tiles tilt in from that side. */
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (Keys.tooFast(e) || Keys.settingsButton(this, e) || Keys.liveTvButton(this, e)) return true
        if (e.action == KeyEvent.ACTION_DOWN) { rememberDirection(e.keyCode); WallpaperView.input() }
        return super.dispatchKeyEvent(e)
    }

    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        if (code == KeyEvent.KEYCODE_MENU) {
            val f = currentFocus
            if (f is Tile) { showOptions(f.tag as String); return true }
            if (f is Room.Widget && f.tag is String && apps!!.get(f.tag as String) != null) {
                showOptions(f.tag as String)
                return true
            }
        }
        return super.onKeyDown(code, e)
    }

    // ---------------------------------------------------------------- content

    fun rebuild() {
        val apps = apps!!
        val theme = theme!!
        apps.reload()
        val f = currentFocus
        if (f != null && f.tag is String) lastFocused = f.tag as String

        if (room != null) { rebuildRoom(); return }
        val dockRow = dockRow!!
        val quickRow = quickRow!!
        dockRow.removeAllViews()
        // The app you just left goes first, tagged, so OK on coming home takes you back.
        val svc = AuroraService.instance
        val back: Apps.App? = if (svc == null || svc.habits == null || svc.habits!!.lastApp() == null) null else apps.get(svc.habits!!.lastApp())
        val docked = apps.dock()
        val extra = back != null && !docked.contains(back)
        var tw = if (docked.size + (if (extra) 1 else 0) > 5) 126 else 144   // six tiles still fit
        tw = Math.round(tw * theme.tileScale)
        val th = tw * 9 / 16
        if (back != null) {
            val t = tile(back, tw, th, 8)
            t.setBadge("Back to")
            dockRow.addView(t)
        }
        for (a in docked) if (back == null || a.pkg != back.pkg) dockRow.addView(tile(a, tw, th, 8))


        quickRow.removeAllViews()
        gamesPill = pill("Games") { start(Intent(this, GamesActivity::class.java), gamesPill) }
        quickRow.addView(gamesPill)
        quickRow.addView(pill("Inputs") { start(Intent("com.android.tv.action.VIEW_INPUTS")) })
        quickRow.addView(pill("Live TV") { start(packageManager.getLeanbackLaunchIntentForPackage(Habits.LIVE_TV)) })
        timerPill = pill("Timer") { showTimer() }
        quickRow.addView(timerPill)
        sleepPill = pill("Sleep") { showSleep() }
        quickRow.addView(sleepPill)
        quickRow.addView(pill("Settings") { showSettings() })
        updatePills()

        var target: View? = if (lastFocused == null) null else dockRow.findViewWithTag<View>(lastFocused)
        if (target == null && dockRow.childCount > 0) target = dockRow.getChildAt(0)
        target?.requestFocus()
    }

    private fun rebuildRoom() {
        val apps = apps!!
        val room = room!!
        val svc = AuroraService.instance
        val back: Apps.App? = if (svc == null || svc.habits == null || svc.habits!!.lastApp() == null) null else apps.get(svc.habits!!.lastApp())
        val list = ArrayList<Apps.App>()
        if (back != null) list.add(back)
        for (a in apps.dock()) if (back == null || a.pkg != back.pkg) list.add(a)
        room.setApps(list, back)
        updatePills()
        var target: View? = if (lastFocused == null) null else room.stage.findViewWithTag<View>(lastFocused)
        if (target == null) target = room.firstApp()
        target?.requestFocus()
    }

    // ---------------------------------------------------------------- what a room asks for

    /** One of [Room.ACTIONS]: the everyday controls every room offers. */
    fun act(action: Int, from: View?) {
        Sounds.select(this)
        when (action) {
            Room.GAMES -> start(Intent(this, GamesActivity::class.java), from)
            Room.INPUTS -> start(Intent("com.android.tv.action.VIEW_INPUTS"))
            Room.LIVE -> start(packageManager.getLeanbackLaunchIntentForPackage(Habits.LIVE_TV))
            Room.TIMER -> showTimer()
            Room.SLEEP -> showSleep()
            Room.SETTINGS -> showSettings()
            else -> showAllApps()
        }
    }

    fun launchApp(a: Apps.App, from: View?) { launch(a, from) }

    fun appOptions(pkg: String) { showOptions(pkg) }

    fun appFocused(a: Apps.App) {
        Sounds.click(this)
        setFocusTitle(a.label)
    }

    fun actionFocused(label: String) {
        Sounds.click(this)
        setFocusTitle(label)
    }

    private fun tile(a: Apps.App, w: Int, h: Int, gap: Int): Tile {
        val theme = theme!!
        val t = Tile(this, a, apps!!, w, h)
        t.setLook(theme.tileRadius, theme.ring)
        t.setMotion(theme.tileMotion)
        val lp = t.layoutParams as LinearLayout.LayoutParams
        lp.setMargins(dp(gap), dp(6), dp(gap), dp(6))
        t.setOnClickListener { v -> launch(a, v) }
        t.setOnLongClickListener { showOptions(a.pkg); true }
        val lift = t.onFocusChangeListener
        t.setOnFocusChangeListener { v, has ->
            lift.onFocusChange(v, has)
            if (has) {
                Sounds.click(this)
                val at = IntArray(2)
                v.getLocationOnScreen(at)
                val frac = (at[0] + v.width / 2f) / resources.displayMetrics.widthPixels
                wall!!.setParallax((0.5f - frac) * 2f)
                setFocusTitle(a.label)
                if (v.parent === dockRow) scroller!!.smoothScrollTo(0, 0)
            }
        }
        return t
    }

    private fun setFocusTitle(label: String) {
        val s = theme!!.focusedLabel(label)
        val focusTitle = focusTitle!!
        if (s.contentEquals(focusTitle.text)) return
        focusTitle.animate().cancel()
        focusTitle.text = s
        focusTitle.alpha = 0f
        focusTitle.translationX = -dp(10).toFloat()
        focusTitle.animate().alpha(1f).translationX(0f).setDuration(220).start()
    }

    /**
     * Kid lock is checked here too, straight from the settings, so locked apps still ask
     * for the PIN when the background service isn't running (after an update or a crash).
     */
    private fun locked(pkg: String): Boolean {
        val svc = AuroraService.instance
        if (svc != null) return svc.isLocked(pkg)
        val p = Prefs.of(this)
        return p.getBoolean("lock_on", false) && Pin.isSet(p) &&
            p.getStringSet("locked", Collections.emptySet())!!.contains(pkg) && !unlockedHere.contains(pkg)
    }

    private fun launch(a: Apps.App, from: View?) {
        if (locked(a.pkg)) {
            withPin {
                val svc = AuroraService.instance
                if (svc != null) svc.unlock(a.pkg) else unlockedHere.add(a.pkg)
                launch(a, from)
            }
            return
        }
        Sounds.select(this)
        apps!!.countLaunch(a)
        lastFocused = a.pkg
        start(a.launchIntent(), from)
    }

    private fun start(i: Intent?) { start(i, null) }

    /** Open something; with `from`, it zooms open out of that tile. */
    private fun start(i: Intent?, from: View?) {
        if (i == null) { Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show(); return }
        try {
            val zoom = if (from == null) null
            else ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.width, from.height).toBundle()
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), zoom)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open that", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- editing

    private fun showOptions(pkg: String) {
        val apps = apps!!
        val a = apps.get(pkg) ?: return
        val docked = apps.inDock(pkg)
        val labels = ArrayList<String>()
        val actions = ArrayList<Runnable>()
        labels.add(if (docked) "Remove from dock" else "Add to dock")
        actions.add(Runnable { apps.toggleDock(pkg) })
        if (docked) {
            labels.add("Move left")
            actions.add(Runnable { apps.move(pkg, -1) })
            labels.add("Move right")
            actions.add(Runnable { apps.move(pkg, 1) })
        }
        labels.add("Hide from home screen")
        actions.add(Runnable { if (docked) apps.toggleDock(pkg); apps.setHidden(pkg, true) })
        labels.add("App info")
        actions.add(Runnable { start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))) })

        GlassMenu.list(this, a.label, labels.toTypedArray()) { which ->
            lastFocused = pkg
            actions[which].run()
            rebuild()
        }
    }

    fun showHidden() {
        val apps = apps!!
        val hidden = apps.hiddenApps()
        if (hidden.isEmpty()) { GlassMenu.message(this, "Hidden apps", "No apps are hidden. Hold OK on an app to hide it.", null); return }
        val names = Array(hidden.size) { "" }
        for (i in names.indices) names[i] = "Show " + hidden[i].label
        GlassMenu.list(this, "Hidden apps", names) { which ->
            apps.setHidden(hidden[which].pkg, false)
            rebuild()
        }
    }

    // ---------------------------------------------------------------- clock

    /** Seasons, day and night, and holidays switch looks by themselves; rebuild when that happens. */
    private fun themeChanged(): Boolean {
        if (HomeTheme.current(this).key.equals(theme!!.key)) return false
        // Don't snatch away an open menu or PIN pad at 7 pm or midnight: switch on a later tick.
        if (GlassMenu.anyShowing()) return false
        GlassMenu.dismissAll()
        recreate()
        return true
    }

    /** Settings picked a new look. */
    fun applyTheme() {
        GlassMenu.dismissAll()
        recreate()
    }

    private fun updateClock() {
        if (resumed && themeChanged()) return
        val now = Date()
        val fmt = Prefs.of(this).getString("clock", "auto")!!
        val h24 = if (fmt == "auto") android.text.format.DateFormat.is24HourFormat(this) else fmt == "24"
        if (room != null) {
            room!!.tick(now, flipTime(now, h24))
            updatePills()
            if (temp != null) updateWeather()
            return
        }
        clock!!.setTime(flipTime(now, h24))
        date!!.text = SimpleDateFormat("EEEE · MMMM d", Locale.getDefault()).format(now).uppercase(Locale.getDefault())
        if (sleepPill != null) updatePills()
        if (temp != null) updateWeather()
    }

    // ---------------------------------------------------------------- weather, stats, sleep, night

    private fun updateWeather() {
        if (System.currentTimeMillis() - weatherAt < 30 * 60_000) return
        weatherAt = System.currentTimeMillis()
        Weather.load(this) { i ->
            if (i == null) { weatherAt = 0; return@load }   // offline: ask again next minute (Weather backs off)
            temp!!.text = i.temp
            fx!!.setWeatherCode(i.code)
            sky!!.text = i.desc + "\n" + i.hilo + (if (i.place!!.isEmpty()) "" else "  ·  " + i.place)
        }
    }

    private fun updateWeek() {
        Thread({
            val week = Stats.week(this, apps!!)
            val line = if (week.isEmpty()) "" else "This week  ·  " + Stats.format(Stats.total(week)) + "  ·  mostly " + week[0].label
            runOnUiThread { weekLine!!.text = line }
        }, "stats").start()
    }

    private fun updatePills() {
        val svc = AuroraService.instance
        val left = svc?.sleepLeft() ?: 0
        val sleepText = if (left > 0) "Sleep · " + left + "m" else "Sleep timer"
        val t = svc?.timerLeft() ?: -1
        val sw = svc?.stopwatchSecs() ?: -1
        val timerText = if (t >= 0) "Timer · " + AuroraService.clock(t.toLong())
        else if (sw >= 0) "Stopwatch · " + AuroraService.clock(sw.toLong()) else "Timer"
        if (room != null) room!!.setStatus(timerText, sleepText)
        else {
            pillText(sleepPill).text = sleepText
            pillText(timerPill).text = timerText
        }
        host!!.removeCallbacks(pillTick)
        if (t >= 0 || sw >= 0) host!!.postDelayed(pillTick, 1000)
    }

    private fun showSleep() {
        val svc = AuroraService.instance
        if (svc == null) { Toast.makeText(this, "Aurora service isn't running", Toast.LENGTH_SHORT).show(); return }
        val mins = intArrayOf(0, 15, 30, 60, 90, 120)
        val names = arrayOf("Off", "15 minutes", "30 minutes", "1 hour", "1.5 hours", "2 hours")
        GlassMenu.list(this, "Sleep timer", names) { which -> svc.setSleep(mins[which]); updatePills() }
    }

    // Keeps counting while a menu is open (the window loses focus to it), but not while we're in the background.
    private val pillTick = Runnable { if ((timerPill != null || room != null) && resumed) updatePills() }

    private fun showTimer() {
        val svc = AuroraService.instance
        if (svc == null) { Toast.makeText(this, "Aurora service isn't running", Toast.LENGTH_SHORT).show(); return }
        val mins = intArrayOf(1, 3, 5, 10, 15, 30, 60)
        val names = ArrayList<String>()
        for (m in mins) names.add(if (m == 60) "1 hour" else "$m" + (if (m == 1) " minute" else " minutes"))
        names.add("Stopwatch")
        val running = svc.timerLeft() >= 0 || svc.stopwatchSecs() >= 0
        if (running) names.add("Stop")
        GlassMenu.list(this, "Timer", names.toTypedArray()) { which ->
            if (which < mins.size) svc.setTimer(mins[which] * 60)
            else if (which == mins.size) svc.startStopwatch()
            else svc.dismissTimer()
            updatePills()
        }
    }

    /** Everything that isn't needed day to day, in one glass panel. */
    private fun showSettings() {
        val open = Runnable { AuroraSettings(this).show() }
        if (Prefs.of(this).getBoolean("settings_pin", false)) withPin(open)
        else open.run()
    }

    /** Run `then` after the kid-lock PIN is entered (straight away if there's no PIN). */
    fun withPin(then: Runnable) {
        val p = Prefs.of(this)
        if (!Pin.isSet(p)) { then.run(); return }
        // After 5 wrong PINs, wait a minute; each further miss doubles the wait (up to an hour).
        // Measured on both clocks, so setting the TV's clock forward doesn't end the wait early.
        val sinceSet = android.os.SystemClock.elapsedRealtime() - p.getLong("pin_wait_rt", Long.MAX_VALUE)
        val wait = maxOf(p.getLong("pin_wait_until", 0) - System.currentTimeMillis(),
            if (sinceSet >= 0) p.getLong("pin_wait_ms", 0) - sinceSet else 0)
        if (wait > 0) {
            val mins = (wait + 59_999) / 60_000
            Toast.makeText(this, "Too many wrong PINs. Try again in " + mins + (if (mins == 1L) " minute" else " minutes"), Toast.LENGTH_LONG).show()
            return
        }
        GlassMenu.pin(this, "Enter the PIN") { entered ->
            if (Pin.check(p, entered)) {
                p.edit().remove("pin_misses").remove("pin_wait_until").remove("pin_wait_rt").remove("pin_wait_ms").apply()
                then.run()
                return@pin
            }
            val misses = p.getInt("pin_misses", 0) + 1
            val e = p.edit().putInt("pin_misses", misses)
            if (misses >= 5) {
                val mins = Math.min(60L, 1L shl Math.min(6, misses - 5))
                e.putLong("pin_wait_until", System.currentTimeMillis() + mins * 60_000)
                    .putLong("pin_wait_rt", android.os.SystemClock.elapsedRealtime()).putLong("pin_wait_ms", mins * 60_000)
                Toast.makeText(this, "Wrong PIN. Locked for " + mins + (if (mins == 1L) " minute" else " minutes"), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Wrong PIN  ·  " + (5 - misses) + " tries left", Toast.LENGTH_SHORT).show()
            }
            e.apply()
        }
    }

    /** Clock style, what shows in the corner, how often the wallpaper changes. */
    fun applyHomePrefs() {
        val p = Prefs.of(this)
        date!!.visibility = if (p.getBoolean("show_date", true)) View.VISIBLE else View.GONE
        weatherBox!!.visibility = if (p.getBoolean("show_weather", true)) View.VISIBLE else View.GONE
        fx!!.visibility = if (theme!!.weather && p.getBoolean("show_weather", true)) View.VISIBLE else View.GONE
        val ip = lanAddress()
        val svc = AuroraService.instance
        val remote = ip != null && svc != null && svc.remoteEnabled() && svc.remote() != null   // only when it's really running
        remoteLine!!.visibility = if (remote) View.VISIBLE else View.GONE
        if (remote) remoteLine!!.text = "Phone remote  ·  " + ip + ":" + AuroraService.PORT
        wall!!.setIntervalMs(p.getInt("wp_every", 5) * 60_000L)
        updateClock()
    }

    /** Every app that isn't hidden. OK opens it; hold OK to add it to the dock, hide it, etc. */
    fun showAllApps() {
        val apps = apps!!
        val list = ArrayList<Apps.App>(apps.dock())
        list.addAll(apps.rest())
        Collections.sort(list) { a, b -> a.label.compareTo(b.label, ignoreCase = true) }
        val names = Array(list.size) { "" }
        for (i in names.indices) names[i] = list[i].label + (if (apps.inDock(list[i].pkg)) "  ·  in dock" else "")
        GlassMenu.list(this, "All apps  ·  hold OK to edit", names,
            { which -> launch(list[which], null) },
            { which -> showOptions(list[which].pkg) })
    }

    /** A Windows 95 desktop icon: a little picture with its name underneath; OK opens it. */
    private fun deskIcon(kind: Int, name: String, open: Runnable): View {
        val v = LinearLayout(this)
        v.orientation = LinearLayout.VERTICAL
        v.gravity = Gravity.CENTER_HORIZONTAL
        v.setPadding(dp(4), dp(4), dp(4), dp(4))
        val pic = object : View(this@HomeActivity) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: android.graphics.Canvas) {
                val s = width / 32f
                c.scale(s, s)
                if (kind == 0) {   // beige computer with a teal screen
                    p.color = 0xFFD8D0B8.toInt(); c.drawRect(4f, 3f, 28f, 21f, p)
                    p.color = 0xFF008080.toInt(); c.drawRect(7f, 6f, 25f, 18f, p)
                    p.color = 0xFFB8AF96.toInt(); c.drawRect(11f, 21f, 21f, 24f, p)
                    p.color = 0xFFD8D0B8.toInt(); c.drawRect(6f, 24f, 26f, 29f, p)
                    p.color = 0xFF606060.toInt(); c.drawRect(18f, 26f, 24f, 27f, p)
                } else if (kind == 1) {   // folder with a little gear
                    p.color = 0xFFE8C850.toInt(); c.drawRect(3f, 9f, 29f, 27f, p)
                    p.color = 0xFFD4B03C.toInt(); c.drawRect(3f, 6f, 14f, 10f, p)
                    p.color = 0xFF606060.toInt(); c.drawCircle(16f, 18f, 5f, p)
                    p.color = 0xFFE8C850.toInt(); c.drawCircle(16f, 18f, 2f, p)
                } else {   // wire-mesh wastebasket
                    p.color = 0xFFC0C0C0.toInt()
                    val b = android.graphics.Path()
                    b.moveTo(6f, 8f); b.lineTo(26f, 8f); b.lineTo(23f, 29f); b.lineTo(9f, 29f); b.close()
                    c.drawPath(b, p)
                    p.color = 0xFF808080.toInt()
                    var x = 9
                    while (x < 25) { c.drawLine(x.toFloat(), 9f, (x + (if (x < 16) 1 else -1)).toFloat(), 28f, p); x += 4 }
                    c.drawRect(5f, 6f, 27f, 9f, p)
                }
            }
        }
        v.addView(pic, LinearLayout.LayoutParams(dp(44), dp(44)))
        val t = text(12f, 0xFFFFFFFF.toInt(), Typeface.create("sans-serif", Typeface.NORMAL))
        t.text = name
        t.setPadding(dp(3), dp(1), dp(3), dp(1))
        v.addView(t)
        v.isFocusable = true
        v.setOnFocusChangeListener { _, has ->
            if (has) Sounds.click(this)
            t.setBackgroundColor(if (has) 0xFF000080.toInt() else 0)
            pic.alpha = if (has) 0.75f else 1f
        }
        v.setOnClickListener { Sounds.select(this); open.run() }
        val lp = LinearLayout.LayoutParams(dp(110), -2)
        lp.bottomMargin = dp(14)
        v.layoutParams = lp
        return v
    }

    /** An old Windows window: navy title bar with `title` (or "name" until it's set) and close boxes, gray body. */
    private fun win95Window(title: TextView, name: String, body: View): LinearLayout {
        val w = LinearLayout(this)
        w.orientation = LinearLayout.VERTICAL
        w.background = HomeTheme.Bevel(this, 0xFFC0C0C0.toInt(), false)
        w.setPadding(dp(3), dp(3), dp(3), dp(3))
        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF000080.toInt(), 0xFF1084D0.toInt()))
        bar.setPadding(dp(6), dp(3), dp(3), dp(3))
        if (title.text.length == 0) title.text = name
        title.setTextColor(0xFFFFFFFF.toInt())
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        title.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        bar.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        for (g in arrayOf("_", "□", "✕")) {
            val b = text(10f, 0xFF000000.toInt(), MEDIUM)
            b.text = g
            b.gravity = Gravity.CENTER
            b.background = HomeTheme.Bevel(this, 0xFFC0C0C0.toInt(), false)
            val lp = LinearLayout.LayoutParams(dp(20), dp(18))
            lp.leftMargin = dp(2)
            bar.addView(b, lp)
        }
        val blp = LinearLayout.LayoutParams(-1, -2)
        blp.bottomMargin = dp(3)
        w.addView(bar, blp)
        w.addView(body)
        return w
    }

    /** The Start button: the four-color flag and "Start". Opens every app. */
    private fun startButton(): View {
        val b = LinearLayout(this)
        b.gravity = Gravity.CENTER_VERTICAL
        val face = HomeTheme.Bevel(this, 0xFFC0C0C0.toInt(), false)
        b.background = face
        b.setPadding(dp(8), dp(5), dp(12), dp(5))
        val flag = object : View(this@HomeActivity) {
            val p = android.graphics.Paint()
            override fun onDraw(c: android.graphics.Canvas) {
                val h = width / 2f
                val cs = intArrayOf(0xFFF03A17.toInt(), 0xFF81BC06.toInt(), 0xFF05A6F0.toInt(), 0xFFFFBA08.toInt())
                for (i in 0 until 4) {
                    p.color = cs[i]
                    val x = (i % 2) * h
                    val y = (i / 2) * h
                    c.drawRect(x + 1, y + 1, x + h - 1, y + h - 1, p)
                }
            }
        }
        b.addView(flag, LinearLayout.LayoutParams(dp(18), dp(18)))
        val t = text(14f, 0xFF000000.toInt(), Typeface.create("sans-serif", Typeface.BOLD))
        t.text = "Start"
        t.setPadding(dp(6), 0, 0, 0)
        b.addView(t)
        b.isFocusable = true
        b.setOnFocusChangeListener { _, has ->
            if (has) Sounds.click(this)
            face.set(0xFFC0C0C0.toInt(), has)   // pressed in while selected
        }
        b.setOnClickListener { Sounds.select(this); showAllApps() }
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.rightMargin = dp(4)
        b.layoutParams = lp
        return b
    }

    // ---------------------------------------------------------------- view helpers

    private fun dp(v: Float): Int =
        Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics))

    private fun dp(v: Int): Int = dp(v.toFloat())

    private fun scrim(o: GradientDrawable.Orientation, color: Int): View {
        val v = View(this)
        v.background = GradientDrawable(o, intArrayOf(color, 0x00000000))
        return v
    }

    private fun text(sp: Float, color: Int, tf: Typeface?): TextView {
        val t = TextView(this)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        t.setTextColor(color)
        t.typeface = tf
        return t
    }

    private fun caps(sp: Float, color: Int): TextView {
        val t = text(sp, color, MEDIUM)
        if (theme!!.caps) {
            t.letterSpacing = 0.28f
            t.isAllCaps = true
        } else {
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * 1.3f)
        }
        if (theme!!.shadow) t.setShadowLayer(dp(6).toFloat(), 0f, 0f, 0x55000000)
        return t
    }

    /** The theme's text color at this strength. */
    private fun ink(a: Float): Int = HomeTheme.alpha(theme!!.text, a)

    private fun caps(sp: Float, color: Int, s: String?): TextView {
        val t = caps(sp, color)
        t.text = s
        return t
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.clipChildren = false
        r.clipToPadding = false
        r.setPadding(dp(8), dp(8), dp(8), dp(8))
        return r
    }

    private fun hscroll(): HorizontalScrollView {
        val s = HorizontalScrollView(this)
        s.isHorizontalScrollBarEnabled = false
        s.clipChildren = false
        s.clipToPadding = false
        s.overScrollMode = View.OVER_SCROLL_NEVER
        return s
    }

    private fun pill(label: String, onClick: Runnable): View {
        val theme = theme!!
        val pillColors = theme.pillColors
        val idle = if (pillColors != null) pillColors[quickRow!!.childCount % pillColors.size] else theme.pillTint
        val idleText = theme.pillText
        val focusText = if (pillColors != null) idle else theme.pillFocusText
        val g = GlassView(this, wall!!, (if (theme.pillRadius >= 100) dp(100) else dp(theme.pillRadius)).toFloat(), idle)
        g.setStyle(theme.pill, theme.pillEdge)
        val t = caps(if (theme.caps) theme.pillSp else theme.pillSp + 1, idleText, label)
        val pv = if (theme.caps) Math.round(8 + (theme.pillSp - 10) * 2) else 6
        t.setPadding(dp(18 + (theme.pillSp - 10) * 3), dp(pv), dp(18 + (theme.pillSp - 10) * 3), dp(pv))
        t.setShadowLayer(0f, 0f, 0f, 0)
        g.addView(t)
        val lp = LinearLayout.LayoutParams(-2, -2)
        lp.setMargins(dp(6), 0, dp(6), 0)
        g.layoutParams = lp
        g.isFocusable = true
        g.setOnClickListener { Sounds.select(this); onClick.run() }
        g.setOnFocusChangeListener { v, has ->
            if (has) Sounds.click(this)
            if (has) setFocusTitle(label)
            g.setTint(if (has) theme.pillFocus else idle)
            t.setTextColor(if (has) focusText else idleText)
            v.animate().scaleX(if (has) 1.08f else 1f).scaleY(if (has) 1.08f else 1f).setDuration(150).start()
        }
        return g
    }
}
