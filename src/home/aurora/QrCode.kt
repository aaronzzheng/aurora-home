package home.aurora

import java.nio.charset.StandardCharsets

/**
 * Minimal QR code maker (byte mode, medium error correction, versions 1-10), enough
 * for the phone remote's link. Follows the QR spec and Nayuki's reference design.
 */
class QrCode private constructor(ver: Int, all: ByteArray) {

    companion object {
        private val ECC_PER_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26)
        private val NUM_BLOCKS = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5)

        @JvmStatic
        fun encode(text: String): QrCode {
            val data = text.toByteArray(StandardCharsets.UTF_8)
            var ver = 1
            while (ver <= 10) {
                val bits = 4 + (if (ver <= 9) 8 else 16) + data.size * 8
                if (bits <= dataCodewords(ver) * 8) break
                ver++
            }
            if (ver > 10) throw IllegalArgumentException("text too long")
            val cap = dataCodewords(ver) * 8
            val bb = BitBuf()
            bb.add(4, 4)
            bb.add(data.size, if (ver <= 9) 8 else 16)
            for (b in data) bb.add(b.toInt() and 0xFF, 8)
            bb.add(0, Math.min(4, cap - bb.len))
            bb.add(0, (8 - bb.len % 8) % 8)
            var pad = 0xEC
            while (bb.len < cap) {
                bb.add(pad, 8)
                pad = pad xor 0xEC xor 0x11
            }
            val cw = ByteArray(bb.len / 8)
            for (i in 0 until bb.len) if (bb.bit(i)) cw[i ushr 3] = (cw[i ushr 3].toInt() or (1 shl (7 - (i and 7)))).toByte()
            return QrCode(ver, interleave(ver, cw))
        }

        private fun bit(v: Int, i: Int): Boolean = ((v ushr i) and 1) != 0

        private fun alignPositions(ver: Int): IntArray {
            if (ver == 1) return IntArray(0)
            val n = ver / 7 + 2
            val step = (ver * 8 + n * 3 + 5) / (n * 4 - 4) * 2
            val r = IntArray(n)
            r[0] = 6
            var i = n - 1
            var pos = ver * 4 + 17 - 7
            while (i >= 1) {
                r[i] = pos
                i--
                pos -= step
            }
            return r
        }

        private fun rawModules(ver: Int): Int {
            var r = (16 * ver + 128) * ver + 64
            if (ver >= 2) {
                val n = ver / 7 + 2
                r -= (25 * n - 10) * n - 55
                if (ver >= 7) r -= 36
            }
            return r
        }

        private fun dataCodewords(ver: Int): Int {
            return rawModules(ver) / 8 - ECC_PER_BLOCK[ver] * NUM_BLOCKS[ver]
        }

        // ------------------------------------------------------------------ error correction

        private fun interleave(ver: Int, data: ByteArray): ByteArray {
            val blocks = NUM_BLOCKS[ver]
            val ecc = ECC_PER_BLOCK[ver]
            val raw = rawModules(ver) / 8
            val shortBlocks = blocks - raw % blocks
            val shortLen = raw / blocks
            val div = rsDivisor(ecc)
            val bl = arrayOfNulls<ByteArray>(blocks)
            var k = 0
            for (i in 0 until blocks) {
                val dl = shortLen - ecc + (if (i < shortBlocks) 0 else 1)
                val d = ByteArray(dl)
                System.arraycopy(data, k, d, 0, dl)
                k += dl
                val e = rsRemainder(d, div)
                val b = ByteArray(shortLen + 1)
                System.arraycopy(d, 0, b, 0, dl)
                System.arraycopy(e, 0, b, b.size - ecc, ecc)
                bl[i] = b   // short blocks keep an empty slot before their ecc so columns line up
            }
            val out = ByteArray(raw)
            var o = 0
            for (i in 0 until bl[0]!!.size)
                for (j in 0 until blocks)
                    if (i != shortLen - ecc || j >= shortBlocks) out[o++] = bl[j]!![i]
            return out
        }

        private fun rsDivisor(degree: Int): ByteArray {
            val r = ByteArray(degree)
            r[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in 0 until degree) {
                    r[j] = mul(r[j].toInt() and 0xFF, root).toByte()
                    if (j + 1 < degree) r[j] = (r[j].toInt() xor r[j + 1].toInt()).toByte()
                }
                root = mul(root, 0x02)
            }
            return r
        }

        private fun rsRemainder(data: ByteArray, div: ByteArray): ByteArray {
            val r = ByteArray(div.size)
            for (b in data) {
                val f = (b.toInt() xor r[0].toInt()) and 0xFF
                System.arraycopy(r, 1, r, 0, r.size - 1)
                r[r.size - 1] = 0
                for (i in r.indices) r[i] = (r[i].toInt() xor mul(div[i].toInt() and 0xFF, f)).toByte()
            }
            return r
        }

        private fun mul(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z and 0xFF
        }
    }

    @JvmField val size: Int = ver * 4 + 17
    private val modules: Array<BooleanArray> = Array(size) { BooleanArray(size) }
    private val isFunction: Array<BooleanArray> = Array(size) { BooleanArray(size) }

    /** Black squares, [y][x]. */
    fun get(x: Int, y: Int): Boolean = modules[y][x]

    init {
        drawFunctions(ver)
        drawCodewords(all)
        var best = 0
        var bestPenalty = Long.MAX_VALUE
        for (m in 0 until 8) {
            applyMask(m)
            drawFormat(m)
            val p = penalty()
            if (p < bestPenalty) { best = m; bestPenalty = p }
            applyMask(m)   // undo (XOR)
        }
        applyMask(best)
        drawFormat(best)
    }

    // ------------------------------------------------------------------ layout

    private fun drawFunctions(ver: Int) {
        for (i in 0 until size) {
            set(6, i, i % 2 == 0)
            set(i, 6, i % 2 == 0)
        }
        finder(3, 3)
        finder(size - 4, 3)
        finder(3, size - 4)
        val pos = alignPositions(ver)
        for (i in pos.indices)
            for (j in pos.indices)
                if (!(i == 0 && j == 0 || i == 0 && j == pos.size - 1 || i == pos.size - 1 && j == 0))
                    for (dy in -2..2)
                        for (dx in -2..2)
                            set(pos[i] + dx, pos[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1)
        drawFormat(0)   // reserve
        if (ver >= 7) {
            var rem = ver
            for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
            val bits = (ver.toLong() shl 12) or rem.toLong()
            for (i in 0 until 18) {
                val b = ((bits ushr i) and 1L) != 0L
                val a = size - 11 + i % 3
                val c = i / 3
                set(a, c, b)
                set(c, a, b)
            }
        }
    }

    private fun finder(cx: Int, cy: Int) {
        for (dy in -4..4)
            for (dx in -4..4) {
                val d = Math.max(Math.abs(dx), Math.abs(dy))
                val x = cx + dx
                val y = cy + dy
                if (x >= 0 && x < size && y >= 0 && y < size) set(x, y, d != 2 && d != 4)
            }
    }

    private fun drawFormat(mask: Int) {
        val data = (0 shl 3) or mask   // medium error correction = 00
        var rem = data
        for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        val bits = ((data shl 10) or rem) xor 0x5412
        for (i in 0..5) set(8, i, bit(bits, i))
        set(8, 7, bit(bits, 6))
        set(8, 8, bit(bits, 7))
        set(7, 8, bit(bits, 8))
        for (i in 9 until 15) set(14 - i, 8, bit(bits, i))
        for (i in 0 until 8) set(size - 1 - i, 8, bit(bits, i))
        for (i in 8 until 15) set(8, size - 15 + i, bit(bits, i))
        set(8, size - 8, true)
    }

    private fun drawCodewords(data: ByteArray) {
        var i = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size)
                for (j in 0 until 2) {
                    val x = right - j
                    val up = ((right + 1) and 2) == 0
                    val y = if (up) size - 1 - vert else vert
                    if (!isFunction[y][x] && i < data.size * 8) {
                        modules[y][x] = bit(data[i ushr 3].toInt(), 7 - (i and 7))
                        i++
                    }
                }
            right -= 2
        }
    }

    private fun applyMask(m: Int) {
        for (y in 0 until size)
            for (x in 0 until size) {
                val inv: Boolean = when (m) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                modules[y][x] = modules[y][x] xor (inv and !isFunction[y][x])
            }
    }

    /** Simplified penalty (runs, 2x2 blocks, balance); any mask scans, this just picks a tidy one. */
    private fun penalty(): Long {
        var p = 0L
        for (y in 0 until size) {
            var run = 1
            for (x in 1 until size) {
                if (modules[y][x] == modules[y][x - 1]) { run++; if (run == 5) p += 3 else if (run > 5) p++ }
                else run = 1
            }
        }
        for (x in 0 until size) {
            var run = 1
            for (y in 1 until size) {
                if (modules[y][x] == modules[y - 1][x]) { run++; if (run == 5) p += 3 else if (run > 5) p++ }
                else run = 1
            }
        }
        var dark = 0
        for (y in 0 until size)
            for (x in 0 until size) {
                if (modules[y][x]) dark++
                if (x < size - 1 && y < size - 1) {
                    val c = modules[y][x]
                    if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) p += 3
                }
            }
        val total = size * size
        p += (Math.abs(dark * 20 - total * 10) + total - 1) / total * 10
        return p
    }

    private fun set(x: Int, y: Int, dark: Boolean) {
        modules[y][x] = dark
        isFunction[y][x] = true
    }

    private class BitBuf {
        private val bits = java.util.BitSet()
        var len = 0
        fun add(v: Int, n: Int) { for (i in n - 1 downTo 0) bits.set(len++, ((v ushr i) and 1) != 0) }
        fun bit(i: Int): Boolean = bits.get(i)
    }
}
