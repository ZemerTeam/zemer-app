package com.jtech.zemer.sig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random
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

    private fun compile(harness: String = "hmac_host_harness.c"): File? {
        val cc = listOf("cc", "gcc", "clang").firstOrNull { runCatching { ProcessBuilder(it, "--version").start().waitFor() == 0 }.getOrDefault(false) }
            ?: return null
        val root = File(System.getProperty("user.dir")).let { if (File(it, "src/main/cpp").isDirectory) it else File(it, "app") }
        val out = File.createTempFile("hmac_host", "").apply { deleteOnExit() }
        val p = ProcessBuilder(
            cc, "-O2", "-std=c99", "-Wall", "-Werror",
            "-I", File(root, "src/main/cpp/zemersig").path,
            File(root, "src/test/cpp/$harness").path,
            File(root, "src/main/cpp/zemersig/hmac_sha256.c").path,
            File(root, "src/main/cpp/zemersig/apk_cert.c").path,
            File(root, "src/main/cpp/zemersig/code_check.c").path,
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

    /**
     * The build's masking (generateZemerSigKey* in app/build.gradle.kts, mirrored here since a build script cannot
     * be imported): masked[order[i]] = key[i] ^ mask[i] ^ cert[i], cert = SHA-256 of the signing certificate DER.
     */
    private fun buildMask(key: ByteArray, certDigest: ByteArray, random: Random): String {
        val mask = ByteArray(32).also(random::nextBytes)
        val order = (0 until 32).shuffled(random)
        val masked = ByteArray(32)
        for (i in 0 until 32) masked[order[i]] = (key[i].toInt() xor mask[i].toInt() xor certDigest[i].toInt()).toByte()
        return "${hex.formatHex(masked)} ${hex.formatHex(mask)} ${hex.formatHex(ByteArray(32) { order[it].toByte() })}"
    }

    @Test
    fun `the certificate is read from an APK's own signing block`() {
        val exe = compile("apk_cert_harness.c")
        assumeTrue("no C compiler on this host", exe != null)
        // signed-fixture.bin: a minimal APK signed (v2 + v3) with the repo's public persistent-debug key (named .bin:
        // the build drops .apk files from test resources)
        val fixture = javaClass.classLoader!!.getResourceAsStream("sig/signed-fixture.bin")!!.readBytes()
        val apk = File.createTempFile("fixture", ".apk").apply { deleteOnExit(); writeBytes(fixture) }
        val unsigned = File.createTempFile("unsigned", ".apk").apply { deleteOnExit(); writeBytes(apk.readBytes().copyOf(4_000)) }
        val p = ProcessBuilder(exe!!.path, apk.path, unsigned.path, "/nonexistent").start()
        val out = p.inputStream.bufferedReader().readLines().also { check(p.waitFor() == 0) }
        assertEquals(listOf("52c741171548b6a41f0b29e9e7c3af854a42a04ea261e8a67d47544929c6834a", "-", "-"), out)
    }

    @Test
    fun `code patched in memory is told apart from the file it was loaded from`() {
        val exe = compile("code_check_harness.c")
        assumeTrue("no C compiler on this host", exe != null)
        val p = ProcessBuilder(exe!!.path).start()
        val lines = p.inputStream.bufferedReader().readLines().also { check(p.waitFor() == 0) }
        assumeTrue("the host refuses writable code", lines.getOrNull(1) != "skip")
        assertEquals(listOf("0", "1", "0"), lines)
    }

    @Test
    fun `the signer rebuilds a bound key only with the certificate it was bound to`() {
        val exe = compile("sig_unmask_harness.c")
        assumeTrue("no C compiler on this host", exe != null)
        val random = Random(7)
        val key = ByteArray(32).also(random::nextBytes)
        val cert = ByteArray(900).also(random::nextBytes)
        val otherCert = cert.copyOf().also { it[400] = (it[400] + 1).toByte() }
        val bound = buildMask(key, MessageDigest.getInstance("SHA-256").digest(cert), random)
        val unbound = buildMask(key, ByteArray(32), random)
        val p = ProcessBuilder(exe!!.path).start()
        p.outputStream.bufferedWriter().use { w ->
            listOf("$bound ${hex.formatHex(cert)}", "$bound ${hex.formatHex(otherCert)}", "$bound -", "$unbound -")
                .forEach { w.write(it); w.newLine() }
        }
        val out = p.inputStream.bufferedReader().readLines().also { check(p.waitFor() == 0) }
        assertEquals(hex.formatHex(key), out[0])
        assertNotEquals("another certificate gives another key", hex.formatHex(key), out[1])
        assertNotEquals("an unread certificate gives another key", hex.formatHex(key), out[2])
        assertEquals("an unbound (debug) key needs no certificate", hex.formatHex(key), out[3])
    }

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
