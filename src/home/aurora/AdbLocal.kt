package home.aurora

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher

/**
 * "Arrows in every app": presses remote buttons through the TV's own debugging
 * connection on 127.0.0.1:5555, which reaches apps that draw their own screens
 * (YouTube, Netflix) where accessibility can't move the selection.
 *
 * A minimal ADB client: connect, sign the TV's challenge with Aurora's own key
 * (the TV asks once, "Allow debugging?"), then run `input keyevent`. It only ever
 * sends key codes chosen in [press]; nothing else is run. One shell stays open and
 * takes each press as a line, so a press doesn't pay for a new stream every time.
 */
class AdbLocal(private val ctx: Context) {

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "aurora-adb").apply { isDaemon = true } }
    private val queued = AtomicInteger()
    private var sock: Socket? = null
    private var input: DataInputStream? = null
    private var out: OutputStream? = null
    private var nextId = 1

    /** The last reason pressing failed, for the settings screen ("Allow it on the TV", "Debugging is off"). */
    @Volatile var problem: String? = null
        private set

    /** Press a key (an Android KEYCODE_*). Presses that pile up behind a slow one are dropped, not queued forever. */
    fun press(keycode: Int, then: ((Boolean) -> Unit)? = null) {
        if (queued.get() > 1) { then?.invoke(false); return }
        queued.incrementAndGet()
        worker.execute {
            val ok = try { shellLine("input keyevent $keycode"); problem = null; true } catch (e: Exception) {
                Log.w("Aurora", "adb: ${e.message}")
                problem = e.message
                close()
                false
            }
            queued.decrementAndGet()
            then?.invoke(ok)
        }
    }

    /** Connect now (this is what makes the TV ask "Allow debugging?" the first time). */
    fun connect(then: (Boolean) -> Unit) {
        worker.execute {
            val ok = try { ensure(); problem = null; true } catch (e: Exception) { problem = e.message; close(); false }
            then(ok)
        }
    }

    fun close() {
        try { sock?.close() } catch (ignored: Exception) { }
        sock = null; input = null; out = null
        shellLocal = 0; shellRemote = 0
    }

    /** The service is going away: drop the connection and the worker thread. */
    fun shutdown() {
        worker.execute { close() }
        worker.shutdown()
    }

    // ------------------------------------------------------------------ protocol

    private fun ensure() {
        if (sock?.isConnected == true && sock?.isClosed == false) return
        val s = Socket()
        try {
            s.connect(InetSocketAddress("127.0.0.1", 5555), 1500)
        } catch (e: Exception) {
            throw IllegalStateException("The TV's network debugging is off")
        }
        s.soTimeout = 4000
        sock = s
        triedSignature = false
        input = DataInputStream(s.getInputStream())
        out = s.getOutputStream()
        send(CNXN, VERSION, MAX_DATA, "host::\u0000".toByteArray())
        val keys = keys()
        var sentKey = false
        while (true) {
            val m = try { read(if (sentKey) 60_000 else 4000) } catch (e: Exception) {
                throw IllegalStateException(if (sentKey) "Choose Allow on the TV (and tick Always allow)" else "The TV didn't answer")
            }
            when (m.cmd) {
                CNXN -> return
                AUTH -> {
                    if (m.arg0 != AUTH_TOKEN) throw IllegalStateException("Unexpected reply from the TV")
                    if (!sentKey && m.data.size == 20 && !triedSignature) {
                        triedSignature = true
                        send(AUTH, AUTH_SIGNATURE, 0, sign(keys, m.data))
                    } else if (!sentKey) {
                        // Not known yet: offer the key, and the TV shows "Allow debugging?"
                        sentKey = true
                        send(AUTH, AUTH_RSAPUBLICKEY, 0, publicKey(keys.public as RSAPublicKey))
                    } else throw IllegalStateException("Choose Allow on the TV")
                }
                else -> throw IllegalStateException("Unexpected reply from the TV")
            }
        }
    }

    private var triedSignature = false
    private var shellLocal = 0
    private var shellRemote = 0

    /**
     * Send one line to the long-lived shell, opening it first if need be. Waits only for
     * adbd to take the line, not for the command to finish, so quick presses pipeline.
     */
    private fun shellLine(cmd: String) {
        ensure()
        if (shellRemote == 0) {
            val local = nextId++
            send(OPEN, local, 0, "shell:\u0000".toByteArray())
            while (shellRemote == 0) {
                val m = read(8000)
                when {
                    m.cmd == OKAY && m.arg1 == local -> { shellLocal = local; shellRemote = m.arg0 }
                    m.cmd == CLSE && m.arg1 == local -> throw IllegalStateException("The TV refused a shell")
                    m.cmd == WRTE -> send(OKAY, m.arg1, m.arg0, ByteArray(0))
                }
            }
        }
        send(WRTE, shellLocal, shellRemote, "$cmd\n".toByteArray())
        while (true) {
            val m = read(8000)
            when {
                m.cmd == OKAY && m.arg1 == shellLocal -> return                      // line taken
                m.cmd == WRTE -> send(OKAY, m.arg1, m.arg0, ByteArray(0))           // prompt and echo: acknowledge, ignore
                m.cmd == CLSE && m.arg1 == shellLocal -> {
                    shellLocal = 0; shellRemote = 0
                    run(cmd)   // the shell ended under us: run this one on its own
                    return
                }
            }
        }
    }

    /** Run one shell command and wait for it to finish. */
    private fun run(cmd: String) {
        ensure()
        val local = nextId++
        send(OPEN, local, 0, "shell:$cmd\u0000".toByteArray())
        var remote = 0
        while (true) {
            val m = read(8000)
            when (m.cmd) {
                OKAY -> remote = m.arg0
                WRTE -> send(OKAY, local, m.arg0, ByteArray(0))
                CLSE -> { send(CLSE, local, if (remote != 0) remote else m.arg0, ByteArray(0)); return }
            }
        }
    }

    private class Msg(val cmd: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    private fun send(cmd: Int, a0: Int, a1: Int, data: ByteArray) {
        val h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0
        for (b in data) sum += (b.toInt() and 0xFF)
        h.putInt(cmd).putInt(a0).putInt(a1).putInt(data.size).putInt(sum).putInt(cmd.inv())
        val o = out ?: throw IllegalStateException("Not connected")
        o.write(h.array())
        o.write(data)
        o.flush()
    }

    private fun read(timeout: Int): Msg {
        sock?.soTimeout = timeout
        val i = input ?: throw IllegalStateException("Not connected")
        val head = ByteArray(24)
        i.readFully(head)
        val h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = h.int; val a0 = h.int; val a1 = h.int; val len = h.int
        if (len < 0 || len > MAX_DATA * 4) throw IllegalStateException("Bad reply from the TV")
        val data = ByteArray(len)
        i.readFully(data)
        return Msg(cmd, a0, a1, data)
    }

    // ------------------------------------------------------------------ Aurora's key

    private fun keys(): KeyPair {
        val dir = File(ctx.filesDir, "adb").apply { mkdirs() }
        val priv = File(dir, "key")
        val pub = File(dir, "key.pub")
        val kf = KeyFactory.getInstance("RSA")
        if (priv.exists() && pub.exists()) {
            return KeyPair(kf.generatePublic(X509EncodedKeySpec(pub.readBytes())), kf.generatePrivate(PKCS8EncodedKeySpec(priv.readBytes())))
        }
        val g = KeyPairGenerator.getInstance("RSA")
        g.initialize(2048)
        val kp = g.generateKeyPair()
        priv.writeBytes(kp.private.encoded)
        pub.writeBytes(kp.public.encoded)
        return kp
    }

    /** ADB signs the 20-byte challenge as if it were a SHA-1 digest (PKCS#1 v1.5). */
    private fun sign(kp: KeyPair, token: ByteArray): ByteArray {
        val c = Cipher.getInstance("RSA/ECB/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, kp.private)
        c.update(SIGNATURE_PADDING)
        return c.doFinal(token)
    }

    /** The public key in ADB's own format: base64 of the mincrypt struct, then a name. */
    private fun publicKey(k: RSAPublicKey): ByteArray {
        val words = 64
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n = k.modulus
        val n0inv = r32.subtract(n.modInverse(r32)).toInt()
        val rr = BigInteger.ONE.shiftLeft(4096).mod(n)
        val b = ByteBuffer.allocate(4 + 4 + 256 + 256 + 4).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(words).putInt(n0inv)
        b.put(littleEndian(n, 256)).put(littleEndian(rr, 256))
        b.putInt(k.publicExponent.toInt())
        val text = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " Aurora Home phone remote\u0000"
        return text.toByteArray()
    }

    private fun littleEndian(v: BigInteger, size: Int): ByteArray {
        val be = v.toByteArray()
        val out = ByteArray(size)
        for (i in 0 until size) {
            val j = be.size - 1 - i
            out[i] = if (j >= 0) be[j] else 0
        }
        return out
    }

    private companion object {
        const val CNXN = 0x4e584e43
        const val AUTH = 0x48545541
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val CLSE = 0x45534c43
        const val WRTE = 0x45545257
        const val VERSION = 0x01000000
        const val MAX_DATA = 256 * 1024
        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_RSAPUBLICKEY = 3

        /** PKCS#1 v1.5 type-1 padding plus the SHA-1 DigestInfo prefix, for a 2048-bit key and a 20-byte digest. */
        val SIGNATURE_PADDING: ByteArray = ByteArray(256 - 20).also { p ->
            val prefix = intArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)
            p[0] = 0x00; p[1] = 0x01
            for (i in 2 until p.size - prefix.size - 1) p[i] = 0xFF.toByte()
            p[p.size - prefix.size - 1] = 0x00
            for (i in prefix.indices) p[p.size - prefix.size + i] = prefix[i].toByte()
        }
    }
}
