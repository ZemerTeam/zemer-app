package com.jtech.zemer.search

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** A search-server restart (502/503) is retried once; other failures are not retried. Same engine as the app. */
class ZemerSearchRetryTest {
    private lateinit var server: ServerSocket
    private val statuses = ConcurrentLinkedQueue<Int>()
    private val hits = AtomicInteger()

    @Before
    fun start() {
        server = ServerSocket(0, 10, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use { s ->
                    val reader = s.getInputStream().bufferedReader()
                    while (reader.readLine()?.isNotEmpty() == true) Unit
                    hits.incrementAndGet()
                    val status = statuses.poll() ?: 200
                    // a 302 redirects to the same path; a 503 asks for a long wait the client must not honor
                    val extra = when (status) {
                        302 -> "Location: /home-rows\r\n"
                        503 -> "Retry-After: 60\r\n"
                        else -> ""
                    }
                    s.getOutputStream().apply {
                        write("HTTP/1.1 $status X\r\n${extra}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        flush()
                    }
                }
            }
        }
    }

    @After
    fun stop() = server.close()

    private fun get(vararg script: Int, path: String = "/home-rows"): Int = runBlocking {
        statuses.addAll(script.toList())
        HttpClient(CIO).also { it.retryRestartingServer(delayMs = 10) }.use { client ->
            client.get("http://127.0.0.1:${server.localPort}$path").status.value
        }
    }

    @Test
    fun `a restart answer is retried once and the second answer is used`() {
        assertEquals(200, get(502))
        assertEquals(2, hits.get())
        hits.set(0)
        val started = System.nanoTime()
        assertEquals(200, get(503))
        assertEquals(2, hits.get())
        // the fixed delay, not the 60 s Retry-After
        assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000)
    }

    @Test
    fun `a second restart answer is returned, not retried again`() {
        assertEquals(502, get(502, 502))
        assertEquals(2, hits.get())
    }

    @Test
    fun `redirect hops do not use up the retry`() {
        assertEquals(200, get(302, 502))
        assertEquals(3, hits.get())
        hits.set(0)
        assertEquals(200, get(302, 302))
        assertEquals(3, hits.get())
    }

    @Test
    fun `other errors and an off-air station are not retried`() {
        assertEquals(500, get(500))
        assertEquals(404, get(404))
        assertEquals(503, get(503, path = "/station"))
        assertEquals(3, hits.get())
    }

    @Test
    fun `an unreachable server fails at once, without a retry delay`() {
        val port = server.localPort
        server.close()
        val started = System.nanoTime()
        val result = runBlocking {
            HttpClient(CIO).also { it.retryRestartingServer(delayMs = 5_000) }.use { client ->
                runCatching { client.get("http://127.0.0.1:$port/home-rows") }
            }
        }
        assertTrue(result.isFailure)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 4_000)
    }
}
