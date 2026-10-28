package com.jtech.zemer.sync

import com.jtech.zemer.sync.models.DevicePreferencesEntity
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import java.util.Date

/**
 * [ContentServerApi] over real HTTP (a minimal local HTTP/1.1 server on a [ServerSocket]; the JDK
 * HttpServer is not on the Android unit-test classpath): request shape, status mapping, timeouts.
 */
class ContentServerApiTest {
    private data class Seen(val method: String, val path: String, val auth: String?, val contentType: String?, val body: String)

    private lateinit var server: ServerSocket
    private lateinit var api: ContentServerApi
    private val seen = mutableListOf<Seen>()

    // "METHOD /path" -> (status, body); a null body means "hang past the client timeout"
    private val routes = mutableMapOf<String, Pair<Int, String?>>()
    @Volatile private var retryAfter: String? = null

    @Before
    fun setUp() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { socket.use(::handle) }
            }
        }
        val client = HttpClient(CIO) {
            install(HttpTimeout) { requestTimeoutMillis = 1_000 }
        }
        api = ContentServerApi("http://127.0.0.1:${server.localPort}/", client)
    }

    private fun handle(socket: Socket) {
        val input = socket.getInputStream().bufferedReader()
        val (method, path) = input.readLine().split(" ")
        val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }
            .associate { it.substringBefore(":").lowercase() to it.substringAfter(":").trim() }
        val body = CharArray(headers["content-length"]?.toInt() ?: 0).also { input.read(it) }.concatToString()
        synchronized(seen) { seen += Seen(method, path, headers["authorization"], headers["content-type"], body) }
        val (status, reply) = routes["$method $path"] ?: (404 to """{"error":"not found"}""")
        if (reply == null) Thread.sleep(3_000)
        val bytes = (reply ?: "{}").encodeToByteArray()
        socket.getOutputStream().apply {
            write("HTTP/1.1 $status X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\n${retryAfter?.let { "Retry-After: $it\r\n" } ?: ""}Connection: close\r\n\r\n".encodeToByteArray())
            write(bytes)
            flush()
        }
    }

    @After
    fun tearDown() = server.close()

    private suspend fun expectStatus(status: Int, block: suspend () -> Unit) {
        try {
            block()
            fail("expected HTTP $status")
        } catch (e: ContentServerException) {
            assertEquals(status, e.status)
        }
    }

    @Test
    fun `report is POSTed as JSON and returns the server id`() = runBlocking {
        routes["POST /reports"] = 201 to """{"ok":true,"id":"r123"}"""
        val id = api.submitReport(buildJsonObject { put("artistId", JsonPrimitive("UC1")); put("reason", JsonPrimitive("other")) })
        assertEquals("r123", id)
        val req = seen.single()
        assertEquals("/reports", req.path)
        assertNull(req.auth)
        assertTrue(req.contentType!!.startsWith("application/json"))
        assertEquals("""{"artistId":"UC1","reason":"other"}""", req.body)
    }

    @Test
    fun `report rejection and rate limit throw with the status`() = runBlocking {
        routes["POST /reports"] = 400 to """{"error":"artistId required"}"""
        expectStatus(400) { api.submitReport(buildJsonObject {}) }
        routes["POST /reports"] = 429 to """{"error":"rate limited"}"""
        expectStatus(429) { api.submitReport(buildJsonObject {}) }
        // Retry-After is carried on the exception
        retryAfter = "90"
        try {
            api.submitReport(buildJsonObject {})
            fail("expected 429")
        } catch (e: ContentServerException) {
            assertEquals(90L, e.retryAfterSec)
        }
    }

    @Test
    fun `getPrefs sends the bearer token and decodes the doc`() = runBlocking {
        routes["GET /prefs"] = 200 to """{"userId":"uid1","userEmail":"a@b.c","contentFilters":{"blockVideos":true},"devices":[{"deviceId":"d1"}],"updatedAt":"2026-10-26T00:00:00Z"}"""
        val doc = api.getPrefs("tok")!!
        assertEquals("Bearer tok", seen.single().auth)
        assertEquals("uid1", doc.userId)
        assertTrue(doc.contentFilters.blockVideos)
        assertEquals("d1", doc.devices.single().deviceId)
    }

    @Test
    fun `getPrefs 404 is null, 401 and 5xx throw`() = runBlocking {
        routes["GET /prefs"] = 404 to """{"error":"no preferences"}"""
        assertNull(api.getPrefs("tok"))
        routes["GET /prefs"] = 401 to """{"error":"bad token"}"""
        expectStatus(401) { api.getPrefs("tok") }
        routes["GET /prefs"] = 503 to "down"
        expectStatus(503) { api.getPrefs("tok") }
    }

    @Test
    fun `putPrefs sends the whole doc with the token, 200 and 201 both succeed`() = runBlocking {
        routes["PUT /prefs"] = 201 to """{"ok":true,"changed":true}"""
        api.putPrefs("tok", DevicePreferencesEntity(userId = "uid1", createdAt = Date(0)))
        routes["PUT /prefs"] = 200 to """{"ok":true,"changed":false}"""
        api.putPrefs("tok", DevicePreferencesEntity(userId = "uid1"))
        val req = seen.first()
        assertEquals("PUT", req.method)
        assertEquals("Bearer tok", req.auth)
        assertTrue(req.body.contains(""""createdAt":"1970-01-01T00:00:00.000Z""""))
        assertTrue(req.body.contains(""""femalePasscodeHash":null"""))
        routes["PUT /prefs"] = 400 to """{"error":"bad date"}"""
        expectStatus(400) { api.putPrefs("tok", DevicePreferencesEntity()) }
    }

    @Test
    fun `devicePrefs reads filters and email without auth, 404 is null`() = runBlocking {
        routes["GET /prefs/device/abc-123"] = 200 to """{"contentFilters":{"allowFemaleSingers":true,"femalePasscodeHash":"h"},"userEmail":"a@b.c"}"""
        val restore = api.devicePrefs("abc-123")!!
        assertNull(seen.single().auth)
        assertEquals("a@b.c", restore.userEmail)
        assertTrue(restore.contentFilters.allowFemaleSingers)
        assertEquals("h", restore.contentFilters.femalePasscodeHash)
        assertNull(api.devicePrefs("unknown"))
        routes["GET /prefs/device/abc-123"] = 429 to "{}"
        expectStatus(429) { api.devicePrefs("abc-123") }
    }

    @Test
    fun `a 200 that is not a device answer fails instead of restoring defaults`() = runBlocking {
        // e.g. a proxy's JSON error page or a Cloudflare challenge served with 200
        routes["GET /prefs/device/abc"] = 200 to """{"ok":false}"""
        try {
            api.devicePrefs("abc")
            fail("expected a parse failure")
        } catch (e: kotlinx.serialization.SerializationException) {
            // expected
        }
        routes["GET /prefs/device/abc"] = 200 to "<html>Just a moment...</html>"
        try {
            api.devicePrefs("abc")
            fail("expected a parse failure")
        } catch (e: kotlinx.serialization.SerializationException) {
            // expected
        }
    }

    @Test
    fun `a hung server times out instead of hanging the sync`() = runBlocking {
        routes["GET /prefs"] = 200 to null
        try {
            api.getPrefs("tok")
            fail("expected a timeout")
        } catch (e: HttpRequestTimeoutException) {
            // expected
        }
    }

    @Test
    fun `an unreachable server throws an IOException`() = runBlocking {
        server.close()
        try {
            api.devicePrefs("abc")
            fail("expected a connection failure")
        } catch (e: java.io.IOException) {
            // expected (connection refused)
        }
    }
}
