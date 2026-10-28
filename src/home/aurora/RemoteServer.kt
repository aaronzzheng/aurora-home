package home.aurora

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioManager
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Tiny HTTP server for the phone remote. LAN clients only, and only requests
 * addressed to this TV (so a web page can't reach it through DNS rebinding). A
 * phone pairs once by typing the 6-digit code shown on the TV (plus the family
 * PIN, if one is set) and gets a token for every later call.
 */
class RemoteServer(private val svc: AuroraService, private val port: Int) {

    private companion object {
        const val TAG = "AuroraRemote"
        const val MAX_HEADERS = 50
        const val MAX_BODY = 1_048_576
        const val REQUEST_MS = 8_000L   // a whole request must arrive within this
    }

    private val prefs = Prefs.of(svc)
    private val rnd = SecureRandom()
    // Three workers and a short queue; anything beyond that is turned away, not piled up.
    private val pool = ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS, ArrayBlockingQueue(16))
    private val icons = ConcurrentHashMap<String, ByteArray>()
    @Volatile private var socket: ServerSocket? = null
    @Volatile private var running = false
    private val guard = PairGuard(rnd)
    private var pairAskedAt = 0L

    fun start() {
        running = true
        Thread({
            val ss = bind() ?: return@Thread
            socket = ss
            if (!running) { close(ss); return@Thread }   // stopped while binding
            while (running) {
                val s = try {
                    ss.accept()
                } catch (e: IOException) {
                    if (!running) break
                    // One bad accept (say, out of file handles) mustn't end the server for good.
                    Log.w(TAG, "accept failed: $e")
                    try { Thread.sleep(250) } catch (ie: InterruptedException) { break }
                    continue
                }
                try {
                    pool.execute { handle(s) }
                } catch (busy: RejectedExecutionException) {
                    close(s)
                }
            }
            close(ss)
        }, "remote-server").start()
    }

    /** Bind the port, retrying briefly: a server that was just stopped may still hold it. */
    private fun bind(): ServerSocket? {
        for (i in 0 until 20) {
            if (!running) return null
            var ss: ServerSocket? = null
            try {
                ss = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                return ss
            } catch (e: IOException) {
                close(ss)
                if (i == 19) Log.w(TAG, "couldn't open port $port: $e")
                try { Thread.sleep(250) } catch (ie: InterruptedException) { return null }
            }
        }
        return null
    }

    private fun close(c: Closeable?) {
        try { c?.close() } catch (ignored: IOException) { }
    }

    fun stop() {
        running = false
        close(socket)
        pool.shutdownNow()
    }

    // ------------------------------------------------------------------ HTTP plumbing

    private class Req(val method: String, val path: String, val q: Map<String, String>) {
        var token: String? = null
        var agent: String? = null
        var host: String? = null
        var ip = ""
        var body = ""
    }

    private fun handle(s: Socket) {
        try {
            s.use { sock ->
                sock.soTimeout = 5_000
                val a = sock.inetAddress
                if (!(a.isSiteLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress)) return
                val r = parse(BufferedInputStream(sock.getInputStream()), System.currentTimeMillis() + REQUEST_MS) ?: return
                r.ip = a.hostAddress ?: ""
                val out = sock.getOutputStream()
                if (!forThisTv(r.host)) { json(out, 403, err("wrong host")); return }
                route(r, out)
            }
        } catch (e: Exception) {
            Log.w(TAG, "request failed: $e")
        }
    }

    /**
     * A web page on another site can't send its requests here by pointing its own
     * name at this TV (DNS rebinding): the Host must be this TV's address or localhost.
     */
    private fun forThisTv(host: String?): Boolean {
        var h = host?.trim()?.lowercase(Locale.ROOT) ?: return false
        if (h.startsWith("[")) return h.startsWith("[::1]")
        h = h.substringBefore(':')
        if (h == "localhost" || h == "127.0.0.1") return true
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .any { n -> n.inetAddresses.toList().any { it.hostAddress == h } }
        } catch (e: Exception) {
            false
        }
    }

    private fun parse(input: InputStream, deadline: Long): Req? {
        val line = readLine(input, deadline) ?: return null
        val parts = line.split(" ")
        if (parts.size < 2) return null
        val target = parts[1]
        val q = HashMap<String, String>()
        if ('?' in target) {
            for (kv in target.substringAfter('?').split("&")) {
                val e = kv.indexOf('=')
                if (e > 0) q[dec(kv.substring(0, e))] = dec(kv.substring(e + 1))
            }
        }
        val r = Req(parts[0], target.substringBefore('?'), q)
        var len = 0
        var headers = 0
        while (true) {
            val h = readLine(input, deadline)
            if (h.isNullOrEmpty()) break
            if (++headers > MAX_HEADERS) throw IOException("too many headers")
            val lower = h.lowercase(Locale.ROOT)
            when {
                lower.startsWith("host:") -> r.host = h.substring(5).trim()
                lower.startsWith("x-token:") -> r.token = h.substring(8).trim()
                lower.startsWith("user-agent:") -> r.agent = h.substring(11).trim()
                lower.startsWith("content-length:") -> len = h.substring(15).trim().toIntOrNull() ?: return null
            }
        }
        // Parameters travel in the query string; only a restore sends a body (a backup file).
        if (len < 0 || len > MAX_BODY) return null
        val body = ByteArray(len)
        var got = 0
        while (got < len) {
            if (System.currentTimeMillis() > deadline) throw IOException("request too slow")
            val n = input.read(body, got, len - got)
            if (n < 0) break
            got += n
        }
        r.body = String(body, 0, got, Charsets.UTF_8)
        if (r.token == null) r.token = q["t"]
        return r
    }

    /** One CRLF/LF-terminated line, or null at end of stream. Gives up after [deadline]. */
    private fun readLine(input: InputStream, deadline: Long): String? {
        val b = ByteArrayOutputStream()
        var any = false
        while (true) {
            val c = input.read()
            if (c < 0) break
            if (System.currentTimeMillis() > deadline) throw IOException("request too slow")
            any = true
            if (c == '\n'.code) break
            if (c != '\r'.code) b.write(c)
            if (b.size() > 8192) throw IOException("header too long")
        }
        return if (any) b.toString("UTF-8") else null
    }

    private fun dec(s: String): String = try { URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s }

    private fun send(out: OutputStream, code: Int, type: String, body: ByteArray, extraHeaders: String = "") {
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            extraHeaders +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    private fun json(out: OutputStream, code: Int, o: JSONObject) =
        send(out, code, "application/json; charset=utf-8", o.toString().toByteArray(Charsets.UTF_8))

    private fun ok(): JSONObject = JSONObject().put("ok", true)

    private fun err(msg: String?): JSONObject = JSONObject().put("ok", false).put("error", msg)

    private fun <T> onMain(work: () -> T): T = svc.onMain { work() }

    // ------------------------------------------------------------------ routes

    private fun route(r: Req, out: OutputStream) {
        if (r.path == "/" || r.path == "/index.html") {
            send(out, 200, "text/html; charset=utf-8", asset("remote.html"))
            return
        }
        if (r.path == "/api/pair") { json(out, 200, pair(r.q["code"], r.q["pin"], r)); return }
        val token = r.token
        if (token == null || !authorized(token)) { json(out, 401, err("pair first")); return }
        Security.used(prefs, token, r.ip)

        val p = r.path
        // Anything but the page's own polling counts as someone using the remote.
        if (p != "/api/state" && p != "/api/icon") svc.userActive()
        when (p) {
            "/api/state" -> json(out, 200, state())
            "/api/backup" -> {
                val name = "aurora-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"
                send(out, 200, "application/json; charset=utf-8", Backup.export(svc).toString(2).toByteArray(Charsets.UTF_8),
                    "Content-Disposition: attachment; filename=\"$name\"\r\n")
            }
            "/api/restore" -> {
                val b = try { Backup.read(r.body) } catch (e: Exception) { json(out, 200, err(e.message)); return }
                // Nothing changes until someone says yes on the TV (with the PIN, if there is one).
                Backup.offer(b)
                svc.launch(Intent(svc, HomeActivity::class.java).putExtra("restore", true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                json(out, 200, ok().put("message", "Confirm on the TV to restore"))
            }
            "/api/icon" -> {
                val png = icon(r.q["pkg"])
                if (png == null) json(out, 404, err("no icon")) else send(out, 200, "image/png", png)
            }
            "/api/launch" -> {
                val a = Apps(svc).apply { reload() }.get(r.q["pkg"])
                // Locked apps still need the PIN on the TV, however they're opened.
                json(out, 200, if (a != null && onMain { svc.launch(a.launchIntent()) }) ok() else err("not installed"))
            }
            "/api/key" -> json(out, 200, key(r.q["k"]))
            "/api/type" -> {
                val text = r.q["s"] ?: ""
                val typed = onMain { svc.type(text, r.q["enter"] == "1") }
                json(out, 200, if (typed) ok() else err("No text box is selected on the TV. Select one first, or use YouTube search."))
            }
            "/api/search" -> {
                val q = r.q["q"]
                val done = !q.isNullOrEmpty() && onMain { svc.searchYouTube(q) }
                json(out, 200, if (done) ok() else err("Search isn't available"))
            }
            "/api/inputs" ->
                json(out, 200, if (onMain { svc.launch(Intent("com.android.tv.action.VIEW_INPUTS")) }) ok() else err("no input picker"))
            "/api/sleep" -> {
                val min = r.q["min"]?.toIntOrNull() ?: 0
                onMain { svc.setSleep(min) }
                json(out, 200, ok().put("sleep", svc.sleepLeft()))
            }
            "/api/night" -> {
                val on = r.q["on"] != "0"
                val start = r.q["start"]?.toIntOrNull() ?: svc.nightStart()
                val end = r.q["end"]?.toIntOrNull() ?: svc.nightEnd()
                val level = r.q["level"]?.toIntOrNull() ?: svc.nightLevel()
                onMain { svc.setNight(on, Math.floorMod(start, 24), Math.floorMod(end, 24), level) }
                json(out, 200, ok().put("active", svc.nightActive()))
            }
            "/api/timer" -> {
                val sec = r.q["sec"]?.toIntOrNull() ?: 0
                onMain { svc.setTimer(sec) }
                json(out, 200, ok().put("timer", svc.timerLeft()))
            }
            "/api/stopwatch" -> {
                onMain { svc.startStopwatch() }
                json(out, 200, ok())
            }
            "/api/art" -> {
                val on = r.q["on"] == "1"
                onMain { prefs.edit().putBoolean("dream_art", on).apply() }
                json(out, 200, ok().put("art", on))
            }
            "/api/habits" -> {
                val h = svc.habits ?: run { json(out, 200, err("not ready")); return }
                onMain {
                    h.set(bool(r.q["pervol"]), bool(r.q["safe"]), r.q["safepct"]?.let { it.toIntOrNull() ?: 25 }, bool(r.q["resume"]))
                    r.q["sounds"]?.let { Sounds.setEnabled(svc, it == "1") }
                }
                json(out, 200, ok())
            }
            "/api/off" -> {
                json(out, 200, ok())
                out.flush()
                onMain { svc.powerOff() }
            }
            "/api/wallpaper" -> {
                onMain { HomeActivity.nextWallpaper() }
                json(out, 200, ok())
            }
            "/api/location" -> try {
                val place = Weather.setPlace(svc, r.q["q"] ?: "")
                onMain { HomeActivity.refreshWeather() }
                json(out, 200, ok().put("place", place))
            } catch (e: Exception) {
                json(out, 200, err(e.message))
            }
            else -> json(out, 404, err("unknown"))
        }
    }

    private fun key(k: String?): JSONObject {
        when (k) {
            "up", "down", "left", "right", "ok", "menu" -> {
                val moved = onMain { svc.arrow(k) }
                return if (moved) ok() else ok().put("stuck", true)
            }
            "back" -> onMain { svc.back() }
            "home" -> onMain { svc.home() }
            "playpause" -> svc.media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "next" -> svc.media(KeyEvent.KEYCODE_MEDIA_NEXT)
            "prev" -> svc.media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "ff" -> svc.media(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            "rew" -> svc.media(KeyEvent.KEYCODE_MEDIA_REWIND)
            "volup" -> svc.volume(AudioManager.ADJUST_RAISE)
            "voldown" -> svc.volume(AudioManager.ADJUST_LOWER)
            "mute" -> svc.volume(AudioManager.ADJUST_TOGGLE_MUTE)
            null -> return err("no key")
            else -> return err("unknown key")
        }
        return ok()
    }

    private fun state(): JSONObject {
        val apps = Apps(svc).apply { reload() }
        val list = JSONArray()
        for (a in apps.dock()) list.put(JSONObject().put("pkg", a.pkg).put("label", a.label).put("dock", true))
        for (a in apps.rest()) list.put(JSONObject().put("pkg", a.pkg).put("label", a.label))
        for (a in apps.hiddenApps()) list.put(JSONObject().put("pkg", a.pkg).put("label", a.label).put("hidden", true))

        val week = Stats.week(svc, apps)
        val stats = JSONArray()
        for (e in week) stats.put(JSONObject().put("label", e.label).put("pkg", e.pkg).put("ms", e.ms))

        val h = svc.habits
        return ok()
            .put("theme", HomeTheme.current(svc).id)
            .put("full", svc.fullRemote())
            .put("apps", list)
            .put("sleep", svc.sleepLeft())
            .put("timer", svc.timerLeft())
            .put("stopwatch", svc.stopwatchSecs())
            .put("ringing", svc.timerRinging())
            .put("art", prefs.getBoolean("dream_art", false))
            .put("habits", if (h == null) JSONObject() else JSONObject()
                .put("resume", h.resume()).put("pervol", h.perAppVolume())
                .put("safe", h.safeStart()).put("safepct", h.safeLevel())
                .put("sounds", Sounds.enabled(svc)))
            .put("night", JSONObject().put("on", svc.nightEnabled()).put("start", svc.nightStart())
                .put("end", svc.nightEnd()).put("level", svc.nightLevel()).put("active", svc.nightActive()))
            .put("place", prefs.getString("w_place", ""))
            .put("statsAllowed", Stats.allowed(svc))
            .put("stats", stats)
            .put("weekTotal", Stats.total(week))
    }

    // ------------------------------------------------------------------ pairing

    private fun pair(code: String?, pin: String?, r: Req): JSONObject {
        val now = System.currentTimeMillis()
        val pinNeeded = Pin.isSet(prefs)
        if (code == null) {
            if (guard.paused(now)) return err("Too many wrong tries. Try again in ${guard.minutesLeft(now)} minutes.")
            // Codes only appear on a TV that's on, where whoever holds the remote can see them.
            if (!(svc.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive) return err("Turn the TV on first")
            synchronized(this) {
                // Asking again within a few seconds keeps the same code instead of minting new ones.
                if (guard.current(now) != null && now - pairAskedAt < 10_000) return ok().put("pin", pinNeeded)
                pairAskedAt = now
            }
            val shown = guard.newCode(now)
            val spaced = shown.substring(0, 3) + " " + shown.substring(3)
            onMain { svc.showBanner("Phone remote code   $spaced", 120_000) }
            return ok().put("pin", pinNeeded)
        }
        val pinOk = !pinNeeded || Pin.check(prefs, pin)
        val problem = guard.check(code, pinOk, now)
        if (problem != null) {
            if (guard.paused(now)) onMain { svc.showBanner("Phone remote  ·  too many wrong tries, pairing paused for an hour", 8000) }
            return err(problem).put("pin", pinNeeded)
        }
        onMain { svc.showBanner("Phone paired", 2500) }
        val token = ByteArray(24).also(rnd::nextBytes).joinToString("") { "%02x".format(it) }
        // Ten phones at most: make room by forgetting the one used longest ago, not all of them.
        val phones = Security.phones(prefs, true)
        while (phones.size >= 10) Security.forget(prefs, phones.removeAt(phones.size - 1).token)
        Security.paired(prefs, token, r.agent, r.ip)
        return ok().put("token", token)
    }

    /** A fresh one-time code asked for at the TV (shown on screen, or inside its QR code). Lifts any pause. */
    fun newPairCode(): String {
        guard.unpause()
        return guard.newCode(System.currentTimeMillis())
    }

    private fun authorized(token: String): Boolean = token in (prefs.getStringSet("remote_tokens", emptySet()) ?: emptySet())

    // ------------------------------------------------------------------ assets

    private fun asset(name: String): ByteArray = svc.assets.open(name).use { it.readBytes() }

    private fun icon(pkg: String?): ByteArray? {
        if (pkg == null) return null
        icons[pkg]?.let { return it }
        val apps = Apps(svc).apply { reload() }
        val a = apps.get(pkg) ?: return null
        val banner = a.banner(apps.pm())
        val d = banner ?: a.icon(apps.pm())
        val w = if (banner != null) 320 else 160
        val h = if (banner != null) 180 else 160
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        val png = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        icons[pkg] = png
        return png
    }

    private fun bool(s: String?): Boolean? = s?.let { it == "1" }
}
