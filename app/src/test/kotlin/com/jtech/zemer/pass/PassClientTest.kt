package com.jtech.zemer.pass

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** [PassClient] over real HTTP against a minimal local server: request shapes and status handling. */
class PassClientTest {
    private lateinit var server: ServerSocket
    private lateinit var client: PassClient
    private val bodies = mutableMapOf<String, String>()
    private val routes = mutableMapOf<String, Pair<Int, String>>()

    @Before
    fun setUp() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    socket.use { s ->
                        val input = s.getInputStream().bufferedReader()
                        val (method, path) = input.readLine().split(" ")
                        val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }
                            .associate { it.substringBefore(":").lowercase() to it.substringAfter(":").trim() }
                        val len = headers["content-length"]?.toInt() ?: 0
                        val buf = CharArray(len); var read = 0
                        while (read < len) { val n = input.read(buf, read, len - read); if (n < 0) break; read += n }
                        synchronized(bodies) { bodies["$method $path"] = String(buf, 0, read) }
                        val (status, reply) = routes["$method $path"] ?: (404 to """{"error":"not found"}""")
                        val bytes = reply.encodeToByteArray()
                        s.getOutputStream().apply {
                            write("HTTP/1.1 $status X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".encodeToByteArray())
                            write(bytes); flush()
                        }
                    }
                }
            }
        }
        client = PassClient("http://127.0.0.1:${server.localPort}", HttpClient(CIO))
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `challenge, attest and renew send the contract shapes`() = runBlocking {
        routes["GET /pass/challenge"] = 200 to """{"nonce":"AAECAwQF_-8","ttlSec":300}"""
        routes["POST /pass/attest"] = 200 to """{"pass":"p1","keyId":"k1","expiresAt":1793100000000}"""
        routes["POST /pass/renew"] = 200 to """{"pass":"p2","keyId":"k1","expiresAt":1793200000000}"""
        val nonce = client.challenge()
        assertEquals("AAECAwQF_-8", nonce.text)
        assertTrue(byteArrayOf(0, 1, 2, 3, 4, 5, -1, -17).contentEquals(nonce.bytes))
        val grant = client.attest(nonce, listOf("Y2VydDE=", "Y2VydDI="))
        assertEquals(PassGrant("p1", "k1", 1_793_100_000_000L), grant)
        assertEquals("""{"nonce":"AAECAwQF_-8","chain":["Y2VydDE=","Y2VydDI="]}""", bodies["POST /pass/attest"])
        val renewed = client.renew("k1", nonce, "c2ln")
        assertEquals("p2", renewed.pass)
        assertEquals("""{"keyId":"k1","nonce":"AAECAwQF_-8","sig":"c2ln"}""", bodies["POST /pass/renew"])
    }

    @Test
    fun `a refusal carries its status`() = runBlocking {
        routes["GET /pass/challenge"] = 200 to """{"nonce":"AA","ttlSec":300}"""
        routes["POST /pass/attest"] = 403 to """{"error":"security level software"}"""
        routes["POST /pass/renew"] = 403 to """{"error":"renewal refused"}"""
        val nonce = client.challenge()
        for (call in listOf<suspend () -> Unit>({ client.attest(nonce, listOf("eA==")) }, { client.renew("k", nonce, "c2ln") })) {
            try {
                call()
                fail("expected 403")
            } catch (e: PassRefusedException) {
                assertEquals(403, e.status)
            }
        }
    }
}
