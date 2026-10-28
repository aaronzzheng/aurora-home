package home.aurora

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import java.util.Calendar
import java.util.Locale
import java.util.Random

/**
 * How the home screen and its menus look: background, fonts, clock, dock, buttons
 * and menu colors. The games wear the same look (`games.GameSkin`).
 *
 * "Seasons" and "Day and night" pick a look from the date or time, and when
 * "Holiday looks" is on, a holiday adds its own touches on top of any look.
 */
class HomeTheme private constructor() {

    companion object {
        @JvmField val IDS = arrayOf("aurora", "retro", "classic", "art", "space", "cozy", "minimal", "cs", "daynight")
        @JvmField val NAMES = arrayOf("Aurora", "Retro", "Classic", "Art", "Space", "Cozy", "Minimal", "Computer Science", "Day and night")
        @JvmField val ABOUT = arrayOf(
                "Apple wallpapers and frosted glass", "A 1960s Japanese television", "Windows 95, pixel for pixel",
                "Famous paintings", "A window on Earth", "An open fire",
                "Paper and ink, just the essentials", "A green hacker terminal", "Sunny room by day, the fire after 7 pm")

        const val BG_PHOTOS = 0
        const val BG_ART = 1
        const val BG_DRAWN = 2
        const val CLOCK_FLIP = 0
        const val CLOCK_TEXT = 1
        const val CLOCK_LCD = 2
        const val L_STANDARD = 0
        const val L_CENTER = 1
        const val L_DESKTOP = 2
        const val L_GALLERY = 3
        const val L_TERMINAL = 4
        const val S_GLASS = 0
        const val S_SOLID = 1
        const val S_BEVEL = 2
        const val S_OUTLINE = 3
        const val S_NONE = 4
        const val S_SHELF = 5
        const val M_STANDARD = 0
        const val M_GENTLE = 1
        const val M_FLOAT = 2
        const val M_STILL = 3
        const val M_BOUNCY = 4
        const val T_DEFAULT = 0
        const val T_RETRO = 1
        const val T_ART = 2
        const val T_SPACE = 3
        const val T_COZY = 4
        const val T_MINIMAL = 5
        const val T_LIGHT = 6
        const val T_SEASONS = 7
        const val T_TERMINAL = 9

        @JvmStatic
        fun font(family: String?): Typeface = Typeface.create(family, Typeface.NORMAL)

        @JvmStatic
        fun alpha(color: Int, a: Float): Int = ((a * 255).toInt() shl 24) or (color and 0xFFFFFF)

        /** Day is 07:00 to 19:00, everywhere a look changes with the hour. */
        @JvmStatic
        fun isDay(hour: Int): Boolean = hour >= 7 && hour < 19

        @JvmStatic
        fun isNight(hour: Int): Boolean = !isDay(hour)

        /** The hour now; preview builds can pretend it's another one (see [current]). */
        @JvmStatic
        fun hour(c: Context, now: Calendar = Calendar.getInstance()): Int {
            val p = Prefs.of(c)
            if ((c.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 && p.contains("debug_hour")) return p.getInt("debug_hour", 12)
            return now.get(Calendar.HOUR_OF_DAY)
        }

        /** Plain Aurora; also what the games screen's menus use. */
        @JvmStatic
        fun aurora(): HomeTheme = make("aurora")

        @JvmStatic
        fun current(c: Context): HomeTheme {
            val now = Calendar.getInstance()
            // Preview builds only: pretend it's another date, to check seasons, holidays and day/night.
            val p = Prefs.of(c)
            if ((c.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 && p.contains("debug_month")) {
                now.set(Calendar.MONTH, p.getInt("debug_month", 0))
                now.set(Calendar.DAY_OF_MONTH, p.getInt("debug_day", 15))
                now.set(Calendar.HOUR_OF_DAY, p.getInt("debug_hour", 12))
            }
            return resolve(c, now)
        }

        @JvmStatic
        fun resolve(c: Context, now: Calendar): HomeTheme {
            val p: SharedPreferences = Prefs.of(c)
            val saved = p.getString("theme", "aurora")!!
            val id = if (saved in IDS) saved else "aurora"   // looks that were retired fall back to Aurora
            val t: HomeTheme
            if (id == "daynight") {
                val day = isDay(now.get(Calendar.HOUR_OF_DAY))
                t = make(if (day) "light" else "cozy")
                t.name = "Day and night"
                t.focusPrefix = if (day) "DAYTIME" else "EVENING"
            } else {
                t = make(id)
            }
            t.key = id + "/" + t.id + "/" + t.fx
            if (p.getBoolean("holidays", true)) holiday(t, now)
            t.key += "/" + t.greeting
            return t
        }

        @JvmStatic
        fun nameOf(id: String?): String {
            for (i in IDS.indices) if (IDS[i] == id) return NAMES[i]
            return NAMES[0]
        }

        // ------------------------------------------------------------------ the looks

        private fun make(id: String): HomeTheme {
            val t = HomeTheme()
            t.id = id
            t.name = nameOf(id)
            when (id) {
                "retro" -> {
                    t.rollBar = true; t.tvSwitchOn = true
                    t.bg = BG_DRAWN
                    t.oldTv = true
                    t.weather = false
                    val chunky = Typeface.create("sans-serif-black", Typeface.NORMAL)
                    t.thin = chunky; t.light = chunky; t.medium = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                    t.text = 0xFFFFF1D6.toInt()
                    t.scrimTop = 0x66000000; t.scrimBottom = 0x80000000.toInt()
                    t.cards = intArrayOf(0xFFF6EBD0.toInt(), 0xFFEADBB6.toInt(), 0xFFE2D0A6.toInt(), 0xFFD6C190.toInt())   // cream, like an old Copal clock
                    t.digits = 0xFF3B2416.toInt()
                    t.clockFont = Typeface.create("sans-serif-black", Typeface.NORMAL)
                    t.dockTint = 0x8C2B160B.toInt(); t.dockEdge = 0xFFE8742A.toInt()
                    t.pill = S_SOLID; t.pillTint = 0xE63B2416.toInt(); t.pillText = 0xFFFFF1D6.toInt(); t.pillEdge = 0xFFE8742A.toInt()
                    t.pillSp = 12f; t.pillRadius = 14f; t.dockRadius = 18f; t.tileRadius = 8f
                    t.pillFocus = 0xFFF2B33D.toInt(); t.pillFocusText = 0xFF3B2416.toInt()
                    t.ring = 0xFFF2B33D.toInt()
                    t.menuTop = 0xF5402614.toInt(); t.menuBottom = 0xF8261509.toInt(); t.menuEdge = 0xFFE8742A.toInt()
                    t.menuRadius = 20f
                    t.menuText = 0xFFFFF1D6.toInt(); t.menuFocus = 0xFFF2B33D.toInt(); t.menuFocusText = 0xFF3B2416.toInt()
                    t.on = 0xFFE8742A.toInt(); t.onText = 0xFF2B160B.toInt(); t.off = 0xFF6B4A33.toInt(); t.offText = 0xFFFFF1D6.toInt()
                    t.menuFont = Typeface.create("sans-serif-condensed", Typeface.BOLD); t.menuTitle = chunky
                    t.tileMotion = M_GENTLE; t.focusPrefix = "TUNED TO"; t.focusSuffix = "CHANNEL 07"
                    t.tileSkin = T_RETRO
                }
                "classic" -> {
                    t.bg = BG_DRAWN
                    t.weather = false
                    val sys = font("sans-serif")
                    val bold = Typeface.create("sans-serif", Typeface.BOLD)
                    t.thin = sys; t.light = sys; t.medium = bold
                    t.shadow = false
                    t.scrimTop = 0; t.scrimBottom = 0
                    t.layout = L_DESKTOP; t.deskIcons = true
                    t.clock = CLOCK_LCD
                    t.digits = 0xFF000000.toInt()
                    t.clockFont = Typeface.create("monospace", Typeface.BOLD)
                    t.dock = S_BEVEL; t.dockRadius = 0f; t.dockTint = 0xFFC0C0C0.toInt()
                    t.pill = S_BEVEL; t.pillRadius = 0f; t.pillTint = 0xFFC0C0C0.toInt(); t.pillText = 0xFF000000.toInt(); t.pillFocus = 0xFF000080.toInt(); t.pillFocusText = 0xFFFFFFFF.toInt()
                    t.caps = false
                    t.tileRadius = 0f; t.ring = 0xFFFFFF00.toInt()
                    t.menuBevel = true; t.titleBar = 0xFF000080.toInt()
                    t.menuTop = 0xFFC0C0C0.toInt(); t.menuBottom = 0xFFC0C0C0.toInt(); t.menuEdge = 0
                    t.menuRadius = 0f; t.rowRadius = 0f; t.dim = 0.25f
                    t.menuText = 0xFF000000.toInt(); t.menuFocus = 0xFF000080.toInt(); t.menuFocusText = 0xFFFFFFFF.toInt()
                    t.on = 0xFF000080.toInt(); t.onText = 0xFFFFFFFF.toInt(); t.off = 0xFF808080.toInt(); t.offText = 0xFFFFFFFF.toInt()
                    t.menuFont = sys; t.menuTitle = bold
                }
                "art" -> {
                    t.bg = BG_ART
                    t.layout = L_GALLERY
                    t.weather = false
                    t.scrimTop = 0; t.scrimBottom = 0
                    val serif = font("serif")
                    t.thin = serif; t.light = serif; t.medium = serif
                    t.caps = false
                    t.clock = CLOCK_TEXT
                    t.clockFont = serif
                    t.dock = S_SOLID; t.dockTint = 0x99141210.toInt(); t.dockRadius = 6f
                    t.pill = S_SOLID; t.pillRadius = 4f; t.pillTint = 0xE6F4EFE6.toInt(); t.pillText = 0xFF2A2622.toInt()
                    t.pillFocus = 0xFF2A2622.toInt(); t.pillFocusText = 0xFFF4EFE6.toInt()
                    t.tileRadius = 4f; t.ring = 0xFFD8B15B.toInt()
                    t.menuTop = 0xFFF6F1E7.toInt(); t.menuBottom = 0xFFEDE6D8.toInt(); t.menuEdge = 0x22000000
                    t.menuRadius = 6f; t.rowRadius = 4f
                    t.menuText = 0xFF2A2622.toInt(); t.menuFocus = 0xFF2A2622.toInt(); t.menuFocusText = 0xFFF6F1E7.toInt()
                    t.on = 0xFF8C2F1F.toInt(); t.onText = 0xFFFFFFFF.toInt(); t.off = 0xFFC9C0B1.toInt(); t.offText = 0xFF2A2622.toInt()
                    t.menuFont = serif; t.menuTitle = Typeface.create("serif", Typeface.ITALIC)
                    t.tileMotion = M_GENTLE; t.focusPrefix = "NOW SHOWING"
                    t.tileSkin = T_ART
                }
                "space" -> {
                    t.orbit = true
                    t.photos = "sp_"
                    t.layout = L_CENTER
                    t.fx = ThemeFx.STARS
                    t.weather = false
                    t.clock = CLOCK_TEXT
                    t.clockFont = font("sans-serif-thin")
                    t.dockTint = 0x59000008
                    t.ring = 0xFFE6D7A5.toInt()
                    t.menuTop = 0xF0070A14.toInt(); t.menuBottom = 0xF5020308.toInt(); t.menuEdge = 0x33E9EAE6
                    t.on = 0xFFFFC56A.toInt(); t.onText = 0xFF1E140A.toInt()
                    t.menuTitle = font("sans-serif-thin")
                    t.tileMotion = M_FLOAT; t.focusPrefix = "MISSION READY"
                    t.tileSkin = T_SPACE
                }
                "cozy" -> {
                    t.bg = BG_DRAWN
                    t.fx = ThemeFx.EMBERS; t.lights = true; t.shelf = true
                    t.weather = false
                    val serif = font("serif")
                    t.thin = serif; t.light = serif
                    t.text = 0xFFFFE9C9.toInt()
                    t.scrimTop = 0x66000000; t.scrimBottom = 0x99000000.toInt()
                    t.cards = intArrayOf(0xFF3A2618.toInt(), 0xFF2E1E13.toInt(), 0xFF26180F.toInt(), 0xFF1D120B.toInt())
                    t.digits = 0xFFFFB45A.toInt(); t.clockGlow = 0x66FF9A2E
                    t.clockFont = Typeface.create("serif", Typeface.BOLD)
                    t.dock = S_SHELF
                    t.pill = S_SOLID; t.pillTint = 0x991E140D.toInt(); t.pillText = 0xFFFFE9C9.toInt(); t.pillEdge = 0x40FFB45A
                    t.pillFocus = 0xFFFFB45A.toInt(); t.pillFocusText = 0xFF2A1A0E.toInt()
                    t.ring = 0xFFFFB45A.toInt()
                    t.menuTop = 0xF52E1F14.toInt(); t.menuBottom = 0xF81A110A.toInt(); t.menuEdge = 0x40FFB45A
                    t.menuText = 0xFFFFE9C9.toInt(); t.menuFocus = 0xFFFFB45A.toInt(); t.menuFocusText = 0xFF2A1A0E.toInt()
                    t.on = 0xFFFFB45A.toInt(); t.onText = 0xFF2A1A0E.toInt(); t.off = 0xFF5A4232.toInt(); t.offText = 0xFFFFE9C9.toInt()
                    t.menuFont = serif; t.menuTitle = serif
                    t.tileMotion = M_GENTLE; t.focusPrefix = "ON THE SHELF"
                    t.tileSkin = T_COZY
                }
                "minimal" -> {
                    t.dayLine = true; t.grayTiles = true
                    t.bg = BG_DRAWN
                    t.layout = L_CENTER
                    t.weather = false
                    t.shadow = false
                    t.scrimTop = 0; t.scrimBottom = 0
                    t.clock = CLOCK_TEXT
                    t.clockFont = font("sans-serif-thin")
                    t.dock = S_NONE
                    t.pill = S_OUTLINE; t.pillTint = 0; t.pillEdge = 0x40FFFFFF
                    t.tileRadius = 8f
                    t.menuTop = 0xFF000000.toInt(); t.menuBottom = 0xFF000000.toInt(); t.menuEdge = 0x33FFFFFF
                    t.menuRadius = 14f; t.rowRadius = 8f; t.dim = 0.7f
                    t.on = 0xFFFFFFFF.toInt(); t.onText = 0xFF000000.toInt(); t.off = 0xFF2A2A2A.toInt()
                    t.menuTitle = font("sans-serif-thin")
                    t.tileMotion = M_STILL; t.focusPrefix = "NOW"
                    t.tileSkin = T_MINIMAL
                }
                "light" -> {
                    t.orbs = true; t.widgets = true
                    t.bg = BG_DRAWN
                    t.weather = false
                    t.text = 0xFF1C1E24.toInt()
                    t.shadow = false
                    t.scrimTop = 0; t.scrimBottom = 0
                    t.clock = CLOCK_TEXT
                    t.clockFont = font("sans-serif-light")
                    t.cards = intArrayOf(0xFFFFFFFF.toInt(), 0xFFF4F5F7.toInt(), 0xFFEBECEF.toInt(), 0xFFE1E3E8.toInt())
                    t.digits = 0xFF1C1E24.toInt()
                    t.dockTint = 0xB3FFFFFF.toInt(); t.dockEdge = 0xFFFFFFFF.toInt()
                    t.pillTint = 0xCCFFFFFF.toInt(); t.pillText = 0xFF1C1E24.toInt(); t.pillFocus = 0xFF1C1E24.toInt(); t.pillFocusText = 0xFFFFFFFF.toInt()
                    t.ring = 0xFF1C1E24.toInt()
                    t.menuTop = 0xF5FFFFFF.toInt(); t.menuBottom = 0xF5EEF0F4.toInt(); t.menuEdge = 0x14000000; t.dim = 0.3f
                    t.menuText = 0xFF1C1E24.toInt(); t.menuFocus = 0xFF1C1E24.toInt(); t.menuFocusText = 0xFFFFFFFF.toInt()
                    t.on = 0xFF34C759.toInt(); t.onText = 0xFFFFFFFF.toInt(); t.off = 0xFFD5D8DE.toInt(); t.offText = 0xFF1C1E24.toInt()
                    t.tileMotion = M_GENTLE; t.focusPrefix = "TODAY"
                    t.tileSkin = T_LIGHT
                }
                "cs" -> {
                    t.bg = BG_DRAWN
                    t.layout = L_TERMINAL
                    t.weather = false
                    t.shadow = false; t.caps = false
                    t.scrimTop = 0; t.scrimBottom = 0
                    val code = Typeface.create("monospace", Typeface.NORMAL)
                    t.thin = code; t.light = code; t.medium = Typeface.create("monospace", Typeface.BOLD)
                    t.text = 0xFFB4FFC8.toInt()
                    t.clock = CLOCK_LCD; t.digits = 0xFF46F07A.toInt(); t.clockFont = t.medium
                    t.dock = S_OUTLINE; t.dockTint = 0xC9040E07.toInt(); t.dockEdge = 0x8022A048.toInt(); t.dockRadius = 8f
                    t.pill = S_OUTLINE; t.pillTint = 0xCC040E07.toInt(); t.pillEdge = 0x8022A048.toInt(); t.pillRadius = 6f
                    t.pillText = 0xFFB4FFC8.toInt(); t.pillFocus = 0xFF46F07A.toInt(); t.pillFocusText = 0xFF02140A.toInt()
                    t.tileRadius = 5f; t.ring = 0xFF7CFFA0.toInt()
                    t.menuTop = 0xFF06120A.toInt(); t.menuBottom = 0xFF030805.toInt(); t.menuEdge = 0x8022A048.toInt()
                    t.menuRadius = 8f; t.rowRadius = 5f; t.dim = 0.62f
                    t.menuText = 0xFFB4FFC8.toInt(); t.menuFocus = 0xFF46F07A.toInt(); t.menuFocusText = 0xFF02140A.toInt()
                    t.on = 0xFF46F07A.toInt(); t.onText = 0xFF02140A.toInt(); t.off = 0xFF0E4A20.toInt(); t.offText = 0xFFB4FFC8.toInt()
                    t.menuFont = code; t.menuTitle = t.medium
                    t.tileMotion = M_STILL; t.focusPrefix = "RUNNING"
                    t.tileSkin = T_TERMINAL
                }
                else -> t.id = "aurora"
            }
            t.sounds = t.id
            return t
        }

        /** The holiday for this date, or null. */
        @JvmStatic
        fun holidayOn(now: Calendar): String? {
            val m = now.get(Calendar.MONTH)
            val d = now.get(Calendar.DAY_OF_MONTH)
            if (m == Calendar.OCTOBER && d >= 20) return "halloween"
            if (m == Calendar.DECEMBER && d <= 26) return "christmas"
            if (m == Calendar.DECEMBER && d == 31 || m == Calendar.JANUARY && d == 1) return "newyear"
            if (m == Calendar.JULY && d <= 4) return "july4"
            return null
        }

        private fun holiday(t: HomeTheme, now: Calendar) {
            val h = holidayOn(now) ?: return
            t.holiday = h
            when (h) {
                "halloween" -> { t.greeting = "Happy Halloween"; t.fx = ThemeFx.BATS; t.accent = 0xFFFF7A1A.toInt(); t.wash = 0x407A2BB8; t.focusPrefix = "TRICK OR TREAT" }
                "christmas" -> { t.greeting = "Merry Christmas"; t.fx = ThemeFx.SNOW; t.accent = 0xFFE23B3B.toInt(); t.wash = 0x2E2EA84F; t.focusPrefix = "HOME FOR THE HOLIDAYS" }
                "newyear" -> { t.greeting = "Happy New Year"; t.fx = ThemeFx.FIREWORKS; t.accent = 0xFFFFD45E.toInt()
                    t.sparks = intArrayOf(0xFFFFD45E.toInt(), 0xFFFFF1C2.toInt(), 0xFFFFB347.toInt(), 0xFFE6E6FF.toInt()); t.focusPrefix = "COUNTDOWN READY" }
                else -> { t.greeting = "Happy 4th of July"; t.fx = ThemeFx.FIREWORKS; t.accent = 0xFFE23B3B.toInt()
                    t.sparks = intArrayOf(0xFFE23B3B.toInt(), 0xFFFFFFFF.toInt(), 0xFF3B6FE2.toInt()); t.focusPrefix = "CELEBRATE" }
            }
            if (!t.menuBevel) { t.on = t.accent; t.onText = 0xFF14100C.toInt() }
            t.ring = t.accent
        }

        // ------------------------------------------------------------------ painted backgrounds

        /** 70s sunset: a striped sun sinking behind bands of orange, mustard and rust. */
        private fun paintRetro(c: Canvas, w: Int, h: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val horizon = h * 0.62f
            p.shader = LinearGradient(0f, 0f, 0f, horizon, intArrayOf(0xFF2A140C.toInt(), 0xFF6B2A14.toInt(), 0xFFC4552A.toInt()),
                    floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), horizon, p)
            // sun with the classic slits across its lower half
            val cx = w * 0.64f
            val cy = horizon - h * 0.02f
            val r = h * 0.3f
            p.shader = LinearGradient(0f, cy - r, 0f, cy, 0xFFFFD86B.toInt(), 0xFFF0782E.toInt(), Shader.TileMode.CLAMP)
            c.save()
            c.clipRect(0f, 0f, w.toFloat(), horizon)
            c.drawCircle(cx, cy, r, p)
            p.shader = null
            p.color = 0xFFC4552A.toInt()
            for (i in 0 until 6) {
                val y = cy - r * 0.55f + i * r * 0.11f
                val th = r * (0.015f + i * 0.012f)
                p.shader = LinearGradient(0f, 0f, 0f, horizon, intArrayOf(0xFF2A140C.toInt(), 0xFF6B2A14.toInt(), 0xFFC4552A.toInt()),
                        floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(cx - r, y, cx + r, y + th, p)
            }
            c.restore()
            p.shader = null
            // the stripes
            val bands = intArrayOf(0xFFF2B33D.toInt(), 0xFFE8742A.toInt(), 0xFFC4442B.toInt(), 0xFF8C2F1F.toInt(), 0xFF4A2414.toInt(), 0xFF2B160B.toInt())
            val bh = (h - horizon) / bands.size
            for (i in bands.indices) {
                p.color = bands[i]
                c.drawRect(0f, horizon + i * bh, w.toFloat(), horizon + (i + 1) * bh + 1, p)
            }
            // soft grain so it doesn't look flat
            val rnd = Random(4)
            p.color = 0x10000000
            for (i in 0 until w * h / 900) c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, 1.2f, p)
        }

        /** Dark wooden wall with a warm lamp glowing in the top-left corner. */
        private fun paintCozy(c: Canvas, w: Int, h: Int) {
            val grain = woodGrain(640, 360)
            val p = Paint(Paint.FILTER_BITMAP_FLAG)
            c.drawBitmap(grain, null, Rect(0, 0, w, h), p)
            p.color = 0xA6120A05.toInt()
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = RadialGradient(w * 0.12f, h * 0.05f, w * 0.75f,
                    intArrayOf(0x99FFB45A.toInt(), 0x33FF8A2E, 0x00FF8A2E), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = RadialGradient(w / 2f, h / 2f, w * 0.8f, 0x00000000, 0x99000000.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }




        /** A rolling ridge from `top` (fraction of height) to the bottom of the screen. */





        /** Soft daylight: a pale sky with big blurred pastel glows, like morning light through a window. */
        private fun paintLight(c: Canvas, w: Int, h: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFFF4F7FC.toInt(), 0xFFFDF3EC.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            val glows = arrayOf(floatArrayOf(0.15f, 0.2f, 0.55f), floatArrayOf(0.85f, 0.15f, 0.5f), floatArrayOf(0.7f, 0.85f, 0.6f), floatArrayOf(0.25f, 0.95f, 0.45f), floatArrayOf(0.5f, 0.5f, 0.35f))
            val colors = intArrayOf(0xFFBFD9FF.toInt(), 0xFFFFD6C2.toInt(), 0xFFD9CCFF.toInt(), 0xFFC8F0DD.toInt(), 0xFFFFF0C4.toInt())
            for (i in glows.indices) {
                val x = glows[i][0] * w
                val y = glows[i][1] * h
                val r = glows[i][2] * w
                p.shader = RadialGradient(x, y, r, colors[i], colors[i] and 0x00FFFFFF, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            }
        }

        /** Matte charcoal, faint paper grain, and a warm desk pool for Computer Science. */
        private fun paintComputerScience(c: Canvas, w: Int, h: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF18211C.toInt(), 0xFF0A100D.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = RadialGradient(w * 0.2f, h * 0.15f, w * 0.7f,
                    0x263A5A3C, 0x00101813, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = null
            val r = Random(17)
            p.color = 0x0AFFFFFF
            for (i in 0 until w * h / 1200) c.drawRect(r.nextInt(w).toFloat(), r.nextInt(h).toFloat(), (r.nextInt(w) + 1).toFloat(), (r.nextInt(h) + 1).toFloat(), p)
        }




        /** Warm wood planks (same idea as the games' wooden table). */
        @JvmStatic
        fun woodGrain(w: Int, h: Int): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val px = IntArray(w * h)
            val r = Random(9)
            val ph = DoubleArray(5)
            for (i in ph.indices) ph[i] = r.nextDouble() * 6.28
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val warp = Math.sin(x * 0.006 + ph[0]) * 9 + Math.sin(x * 0.017 + ph[1]) * 3
                    val g = Math.sin((y + warp) * 0.42 + ph[2]) * 0.5 + Math.sin((y + warp) * 0.11 + ph[3]) * 0.35 +
                            Math.sin((y + warp * 1.7) * 1.3 + ph[4]) * 0.15
                    val plank = if ((y / 72) % 2 == 0) 0.0 else 0.06
                    val t = Math.max(0.0, Math.min(1.0, 0.5 + g * 0.32 + plank))
                    var rr = (92 + 70 * t).toInt()
                    var gg = (54 + 42 * t).toInt()
                    var bb = (28 + 22 * t).toInt()
                    if (y % 72 == 0) { rr -= 40; gg -= 26; bb -= 16 }
                    px[y * w + x] = 0xFF000000.toInt() or (rr shl 16) or (gg shl 8) or bb
                }
            }
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            return bmp
        }
    }

    @JvmField var id: String = ""
    @JvmField var name: String = ""
    @JvmField var key: String? = null
    @JvmField var layout = L_STANDARD

    // background
    @JvmField var bg = BG_PHOTOS
    @JvmField var photos: String = "wp_"
    @JvmField var fx = ThemeFx.NONE   // floating bits; a soft color glow from the top corner
    @JvmField var wash = 0
    @JvmField var weather = true
    @JvmField var oldTv = false
    @JvmField var veil = 0   // a color laid over the photos
    // little touches that give a look its character
    @JvmField var lights = false
    @JvmField var rollBar = false
    @JvmField var orbs = false
    @JvmField var orbit = false
    @JvmField var dayLine = false
    @JvmField var grayTiles = false
    @JvmField var widgets = false
    @JvmField var shelf = false
    @JvmField var tvSwitchOn = false
    @JvmField var deskIcons = false
    @JvmField var tileMotion = M_STANDARD
    @JvmField var tileSkin = T_DEFAULT
    @JvmField var focusPrefix: String? = null
    @JvmField var focusSuffix: String? = null

    // home screen text
    @JvmField var thin: Typeface = font("sans-serif-thin")
    @JvmField var light: Typeface = font("sans-serif-light")
    @JvmField var medium: Typeface = font("sans-serif-medium")
    @JvmField var text = 0xFFFFFFFF.toInt()
    @JvmField var shadow = true
    @JvmField var caps = true
    @JvmField var scrimTop = 0x99000000.toInt()
    @JvmField var scrimBottom = 0xC0000000.toInt()

    // clock
    @JvmField var clock = CLOCK_FLIP
    @JvmField var cards: IntArray = intArrayOf(0xFF26282F.toInt(), 0xFF1A1C22.toInt(), 0xFF141519.toInt(), 0xFF0E0F12.toInt())
    @JvmField var digits = 0xFFF4F4F2.toInt()
    @JvmField var clockGlow = 0
    @JvmField var clockFont: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    // dock, buttons, tiles
    @JvmField var dock = S_GLASS
    @JvmField var dockTint = 0x40080A12
    @JvmField var dockEdge = 0
    @JvmField var dockRadius = 26f
    @JvmField var pill = S_GLASS
    @JvmField var pillTint = 0x33080A12
    @JvmField var pillText = 0xF2FFFFFF.toInt()
    @JvmField var pillFocus = 0xF2FFFFFF.toInt()
    @JvmField var pillFocusText = 0xFF0B0D14.toInt()
    @JvmField var pillEdge = 0
    @JvmField var pillRadius = 100f
    @JvmField var pillSp = 10f
    @JvmField var pillColors: IntArray? = null   // one color per button (Kids)
    @JvmField var tileRadius = -1f
    @JvmField var tileScale = 1f
    @JvmField var ring = 0xF2FFFFFF.toInt()

    // menus
    @JvmField var menuTop = 0xEB1C1F2B.toInt()
    @JvmField var menuBottom = 0xF00E1017.toInt()
    @JvmField var menuEdge = 0x33FFFFFF
    @JvmField var titleBar = 0
    @JvmField var menuRadius = 26f
    @JvmField var rowRadius = 16f
    @JvmField var dim = 0.55f
    @JvmField var menuBevel = false
    @JvmField var menuText = 0xF2FFFFFF.toInt()
    @JvmField var menuFocus = 0xF2FFFFFF.toInt()
    @JvmField var menuFocusText = 0xFF0B0D14.toInt()
    @JvmField var on = 0xFF5FE3FF.toInt()
    @JvmField var onText = 0xFF0B0D14.toInt()
    @JvmField var off = 0xFF4A4E5A.toInt()
    @JvmField var offText = 0xFFFFFFFF.toInt()
    @JvmField var menuFont: Typeface = font("sans-serif")
    @JvmField var menuTitle: Typeface = font("sans-serif-light")

    @JvmField var sounds: String? = null     // which sound set; null = the default sounds
    @JvmField var greeting: String? = null   // holiday line under the date
    @JvmField var holiday: String? = null    // which holiday is on, for rooms that dress for it
    @JvmField var accent = 0
    @JvmField var sparks: IntArray? = null   // firework colors

    /** Small, theme-specific cue above the app rail. Aurora deliberately keeps its original plain label. */
    fun focusedLabel(app: String?): String? {
        if (focusPrefix == null) return app
        return if (focusSuffix == null) focusPrefix + "  ·  " + app else focusPrefix + "  ·  " + app + "  ·  " + focusSuffix
    }

    /** The painted background for [BG_DRAWN] looks. */
    fun background(): Drawable {
        when (id) {
            "retro" -> return Painted { c, w, h -> paintRetro(c, w, h) }
            "classic" -> return Painted { c, _, _ -> c.drawColor(0xFF008080.toInt()) }
            "cozy" -> return Painted { c, w, h -> paintCozy(c, w, h) }
            "light" -> return Painted { c, w, h -> paintLight(c, w, h) }
            "cs" -> return Painted { c, w, h -> paintComputerScience(c, w, h) }
            else -> return Painted { c, _, _ -> c.drawColor(0xFF000000.toInt()) }
        }
    }

    fun interface Painter { fun paint(c: Canvas, w: Int, h: Int) }

    private class Painted(private val p: Painter) : Drawable() {
        override fun draw(c: Canvas) { val b = bounds; p.paint(c, b.width(), b.height()) }
        override fun setAlpha(a: Int) { }
        override fun setColorFilter(cf: ColorFilter?) { }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }

    // ------------------------------------------------------------------ Windows 95 edges

    /** Gray box with the raised (or pressed-in) 3D edge of old Windows buttons and windows. */
    class Bevel(c: Context, face: Int, sunken: Boolean) : Drawable() {
        private val p = Paint()
        private val u: Float = Math.max(1f, c.resources.displayMetrics.density)
        private var face: Int = face
        private var sunken: Boolean = sunken

        fun set(face: Int, sunken: Boolean) { this.face = face; this.sunken = sunken; invalidateSelf() }

        override fun draw(c: Canvas) {
            val b = bounds
            val l = b.left.toFloat()
            val t = b.top.toFloat()
            val r = b.right.toFloat()
            val bt = b.bottom.toFloat()
            p.color = face
            c.drawRect(b, p)
            val lt = if (sunken) 0xFF808080.toInt() else 0xFFFFFFFF.toInt()
            val rb = if (sunken) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            val lt2 = if (sunken) 0xFF000000.toInt() else 0xFFDFDFDF.toInt()
            val rb2 = if (sunken) 0xFFDFDFDF.toInt() else 0xFF808080.toInt()
            edge(c, l, t, r, bt, lt, rb)
            edge(c, l + u, t + u, r - u, bt - u, lt2, rb2)
        }

        private fun edge(c: Canvas, l: Float, t: Float, r: Float, b: Float, lt: Int, rb: Int) {
            p.color = lt
            c.drawRect(l, t, r, t + u, p)
            c.drawRect(l, t, l + u, b, p)
            p.color = rb
            c.drawRect(l, b - u, r, b, p)
            c.drawRect(r - u, t, r, b, p)
        }

        override fun setAlpha(a: Int) { }
        override fun setColorFilter(cf: ColorFilter?) { }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }

    // ------------------------------------------------------------------ old TV glass

    /** Wood cabinet edge, rounded tube corners, scanlines and a soft vignette. Drawn once. */
    class OldTv(c: Context) : Drawable() {
        private var cache: Bitmap? = null
        private val u: Float = c.resources.displayMetrics.density

        override fun draw(c: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            var cached = cache
            if (cached == null || cached.width != b.width() || cached.height != b.height()) { cached = render(b.width(), b.height()); cache = cached }
            c.drawBitmap(cached, b.left.toFloat(), b.top.toFloat(), null)
        }

        private fun render(w: Int, h: Int): Bitmap {
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            // scanlines
            val line = Bitmap.createBitmap(1, 3, Bitmap.Config.ARGB_8888)
            line.setPixel(0, 2, 0x22000000)
            p.shader = BitmapShader(line, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            // tube vignette
            p.shader = RadialGradient(w / 2f, h / 2f, w * 0.62f, intArrayOf(0x00000000, 0x00000000, 0x8C000000.toInt()),
                    floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            // wooden cabinet around a rounded screen
            val edge = 14 * u
            val round = 36 * u
            val frame = Path()
            frame.fillType = Path.FillType.EVEN_ODD
            frame.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
            frame.addRoundRect(RectF(edge, edge, w - edge, h - edge), round, round, Path.Direction.CW)
            val wood = woodGrain(640, 360)
            val ws: Shader = BitmapShader(wood, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
            p.shader = ws
            c.drawPath(frame, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2 * u
            p.color = 0x99000000.toInt()
            c.drawRoundRect(RectF(edge, edge, w - edge, h - edge), round, round, p)
            return out
        }

        override fun setAlpha(a: Int) { }
        override fun setColorFilter(cf: ColorFilter?) { }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
