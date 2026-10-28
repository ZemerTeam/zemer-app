package com.jtech.zemer.sig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The production C signer (`src/main/cpp/zemersig/hmac_sha256.c`) compiled for the host and checked against
 * the JDK's HMAC-SHA256: every server vector's canonical message, the RFC 4231 HMAC test cases, and message
 * lengths across the SHA-256 block boundaries. Skipped only where no C compiler exists (CI has one).
 */
class SigNativeHostTest {
    private val hex = HexFormat.of()

    private fun compile(): File? {
        val cc = listOf("cc", "gcc", "clang").firstOrNull { runCatching { ProcessBuilder(it, "--version").start().waitFor() == 0 }.getOrDefault(false) }
            ?: return null
        val root = File(System.getProperty("user.dir")).let { if (File(it, "src/main/cpp").isDirectory) it else File(it, "app") }
        val out = File.createTempFile("hmac_host", "").apply { deleteOnExit() }
        val p = ProcessBuilder(
            cc, "-O2", "-std=c99", "-Wall", "-Werror",
            "-I", File(root, "src/main/cpp/zemersig").path,
            File(root, "src/test/cpp/hmac_host_harness.c").path,
            File(root, "src/main/cpp/zemersig/hmac_sha256.c").path,
            "-o", out.path,
        ).redirectErrorStream(true).start()
        val log = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "host compile failed:\n$log" }
        return out
    }

    private fun run(exe: File, key: ByteArray, messages: List<ByteArray>): List<String> {
        val p = ProcessBuilder(exe.path).start()
        p.outputStream.bufferedWriter().use { w ->
            w.write(hex.formatHex(key)); w.newLine()
            messages.forEach { w.write(hex.formatHex(it)); w.newLine() }
        }
        return p.inputStream.bufferedReader().readLines().also { check(p.waitFor() == 0) }
    }

    private fun jdk(key: ByteArray, msg: ByteArray) =
        hex.formatHex(Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(msg))

    @Test
    fun `the C HMAC matches the JDK on vectors, RFC 4231 cases and every block-boundary length`() {
        val exe = compile()
        assumeTrue("no C compiler on this host", exe != null)
        val key = ByteArray(32) { (it + 1).toByte() }
        val vectorMessages = listOf("sig-vectors.json", "sig-vectors-generated.json").flatMap { name ->
            val text = javaClass.classLoader!!.getResourceAsStream("sig/$name")!!.readBytes().decodeToString()
            Regex(""""method":\s*"([^"]*)",\s*"url":\s*"([^"]*)",\s*"t":\s*(\d+)""").findAll(text).map { m ->
                val url = m.groupValues[2]
                val q = url.indexOf('?')
                SigCanonical.message(m.groupValues[1], if (q < 0) url else url.substring(0, q), if (q < 0) null else url.substring(q + 1), m.groupValues[3].toLong())
            }.toList()
        }
        assertTrue("all contract + generated vectors were read", vectorMessages.size >= 407)
        val lengths = (0..200).map { n -> ByteArray(n) { (it * 31 + 7).toByte() } }
        val messages = vectorMessages + lengths
        assertEquals(messages.map { jdk(key, it) }, run(exe!!, key, messages))

        // RFC 4231: a short key (case 2) and a key longer than the block (case 6)
        assertEquals(
            listOf("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"),
            run(exe, "Jefe".toByteArray(), listOf("what do ya want for nothing?".toByteArray())),
        )
        assertEquals(
            listOf("60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54"),
            run(exe, ByteArray(131) { 0xaa.toByte() }, listOf("Test Using Larger Than Block-Size Key - Hash Key First".toByteArray())),
        )
    }
}
