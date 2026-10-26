package com.jtech.zemer.pass

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * The pass header over real HTTP: the OkHttp interceptor sends it to a Zemer host (resolved to a local server
 * by a test DNS) and never to any other host; the Ktor plugin never sends it to a non-Zemer host either.
 */
class ZemerPassHeaderTest {
    private lateinit var server: ServerSocket
    private val seen = mutableListOf<Map<String, String>>()

    @Before
    fun setUp() {
        ZemerPass.current = "test-pass"
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    socket.use { s ->
                        val input = s.getInputStream().bufferedReader()
                        input.readLine()
                        val headers = generateSequence { input.readLine()?.takeIf { it.isNotEmpty() } }
                            .associate { it.substringBefore(":").lowercase() to it.substringAfter(":").trim() }
                        synchronized(seen) { seen += headers }
                        s.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".encodeToByteArray()); flush() }
                    }
                }
            }
        }
    }

    @After
    fun tearDown() {
        ZemerPass.current = null
        server.close()
    }

    private val local = InetAddress.getByName("127.0.0.1")

    @Test
    fun `okhttp sends the pass to Zemer hosts only`() {
        val client = OkHttpClient.Builder()
            .dns(Dns { host -> if (host.endsWith("zemer.io") || host == "example.org") listOf(local) else Dns.SYSTEM.lookup(host) })
            .addInterceptor(ZemerPass.OkHttpInterceptor)
            .build()
        val port = server.localPort
        for (host in listOf("search.zemer.io", "stream.zemer.io", "content.zemer.io", "tracking.zemer.io", "example.org")) {
            client.newCall(Request.Builder().url("http://$host:$port/x").build()).execute().close()
        }
        val sent = synchronized(seen) { seen.map { it["host"]!!.substringBefore(":") to it["x-zemer-pass"] } }
        assertEquals(
            listOf("search.zemer.io" to "test-pass", "stream.zemer.io" to "test-pass", "content.zemer.io" to "test-pass", "tracking.zemer.io" to null, "example.org" to null),
            sent,
        )
    }

    @Test
    fun `okhttp sends nothing when there is no pass`() {
        ZemerPass.current = null
        val client = OkHttpClient.Builder().dns(Dns { listOf(local) }).addInterceptor(ZemerPass.OkHttpInterceptor).build()
        client.newCall(Request.Builder().url("http://search.zemer.io:${server.localPort}/x").build()).execute().close()
        assertNull(synchronized(seen) { seen.single()["x-zemer-pass"] })
    }

    @Test
    fun `ktor plugin never sends the pass to a non-Zemer host`() = runBlocking {
        HttpClient(CIO) { install(ZemerPass.KtorPlugin) }.use { it.get("http://127.0.0.1:${server.localPort}/x") }
        assertNull(synchronized(seen) { seen.single()["x-zemer-pass"] })
    }
}
