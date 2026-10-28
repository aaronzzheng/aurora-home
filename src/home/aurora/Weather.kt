package home.aurora

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Current weather from Open-Meteo (free, no account, no key). The location is
 * looked up once from the public IP (ipapi.co) unless set from the phone remote.
 * Results are cached for 30 minutes.
 */
object Weather {

    class Info {
        @JvmField var temp: String? = null
        @JvmField var desc: String? = null
        @JvmField var hilo: String? = null
        @JvmField var place: String? = null
        @JvmField var code = 0
    }

    fun interface Callback { fun on(i: Info?) }

    private const val CACHE_MS = 30 * 60_000L
    private val MAIN = Handler(Looper.getMainLooper())

    @JvmStatic
    fun prefs(c: Context): SharedPreferences = Prefs.of(c)

    @JvmStatic
    fun load(c: Context, cb: Callback) {
        val app = c.applicationContext
        Thread({
            var i: Info? = null
            try { i = fetch(app) } catch (ignored: Exception) { }
            val out = i
            MAIN.post { cb.on(out) }
        }, "weather").start()
    }

    @Throws(Exception::class)
    private fun fetch(c: Context): Info {
        val p = prefs(c)
        if (!p.contains("w_lat")) locateByIp(c)
        var json = p.getString("w_cache", null)
        if (json == null || System.currentTimeMillis() - p.getLong("w_time", 0) > CACHE_MS) {
            val f = p.getBoolean("w_fahrenheit", true)
            json = get("https://api.open-meteo.com/v1/forecast?latitude=" + p.getFloat("w_lat", 0f) +
                "&longitude=" + p.getFloat("w_lon", 0f) +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto" +
                (if (f) "&temperature_unit=fahrenheit" else ""))
            p.edit().putString("w_cache", json).putLong("w_time", System.currentTimeMillis()).apply()
        }
        val o = JSONObject(json)
        val cur = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val i = Info()
        i.temp = Math.round(cur.getDouble("temperature_2m")).toString() + "°"
        i.code = cur.getInt("weather_code")
        i.desc = describe(i.code, cur.optInt("is_day", 1) == 1)
        i.hilo = "H " + Math.round(first(d.getJSONArray("temperature_2m_max"))) + "°  L " +
            Math.round(first(d.getJSONArray("temperature_2m_min"))) + "°"
        i.place = p.getString("w_place", "")
        return i
    }

    @Throws(Exception::class)
    private fun first(a: JSONArray): Double = a.getDouble(0)

    @Throws(Exception::class)
    private fun locateByIp(c: Context) {
        val o = JSONObject(get("https://ipapi.co/json/"))
        save(c, o.getDouble("latitude"), o.getDouble("longitude"), o.optString("city"), o.optString("country_code"))
    }

    /** Set location from a city name or ZIP typed on the phone. Returns the resolved place name. */
    @JvmStatic
    @Throws(Exception::class)
    fun setPlace(c: Context, query: String): String {
        val o = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" +
            URLEncoder.encode(query, "UTF-8")))
        val r = o.optJSONArray("results")
        if (r == null || r.length() == 0) throw IOException("Couldn't find \"$query\"")
        val g = r.getJSONObject(0)
        save(c, g.getDouble("latitude"), g.getDouble("longitude"), g.optString("name"), g.optString("country_code"))
        return g.optString("name")
    }

    private fun save(c: Context, lat: Double, lon: Double, place: String, country: String) {
        val e = prefs(c).edit()
            .putFloat("w_lat", lat.toFloat()).putFloat("w_lon", lon.toFloat())
            .putString("w_place", place)
            .remove("w_cache")
        if (!prefs(c).getBoolean("w_unit_set", false))
            e.putBoolean("w_fahrenheit", "US".equals(country, ignoreCase = true) || "LR".equals(country, ignoreCase = true))
        e.apply()
    }

    @JvmStatic
    fun fahrenheit(c: Context): Boolean = prefs(c).getBoolean("w_fahrenheit", true)

    @JvmStatic
    fun setFahrenheit(c: Context, f: Boolean) {
        prefs(c).edit().putBoolean("w_fahrenheit", f).putBoolean("w_unit_set", true).remove("w_cache").apply()
    }

    /** Forget the city and look it up from the internet connection again. */
    @JvmStatic
    fun useMyLocation(c: Context) {
        prefs(c).edit().remove("w_lat").remove("w_lon").remove("w_place").remove("w_cache").apply()
    }

    @JvmStatic
    fun describe(code: Int, day: Boolean): String {
        if (code == 0) return if (day) "Sunny" else "Clear"
        if (code <= 2) return if (day) "Partly cloudy" else "Mostly clear"
        if (code == 3) return "Cloudy"
        if (code == 45 || code == 48) return "Fog"
        if (code >= 51 && code <= 57) return "Drizzle"
        if (code >= 61 && code <= 67) return "Rain"
        if (code >= 71 && code <= 77) return "Snow"
        if (code >= 80 && code <= 82) return "Showers"
        if (code == 85 || code == 86) return "Snow showers"
        if (code >= 95) return "Thunderstorms"
        return ""
    }

    @JvmStatic
    @Throws(IOException::class)
    fun get(url: String): String {
        val h = URL(url).openConnection() as HttpURLConnection
        h.connectTimeout = 8000
        h.readTimeout = 8000
        h.setRequestProperty("User-Agent", "AuroraHome/3")
        try {
            h.inputStream.use { inp ->
                val b = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = inp.read(buf)
                    if (n <= 0) break
                    b.write(buf, 0, n)
                }
                return b.toString("UTF-8")
            }
        } finally {
            h.disconnect()
        }
    }
}
