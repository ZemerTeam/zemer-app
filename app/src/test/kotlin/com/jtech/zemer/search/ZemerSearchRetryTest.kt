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
                    s.getOutputStream().apply {
                        write("HTTP/1.1 $status X\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        flush()
                    }
                }
            }
        }
    }

    @After
    fun stop() = server.close()

    private fun get(vararg script: Int): Int = runBlocking {
        statuses.addAll(script.toList())
        HttpClient(CIO) { retryRestartingServer(delayMs = 10) }.use { client ->
            client.get("http://127.0.0.1:${server.localPort}/home-rows").status.value
        }
    }

    @Test
    fun `a restart answer is retried once and the second answer is used`() {
        assertEquals(200, get(502))
        assertEquals(2, hits.get())
        hits.set(0)
        assertEquals(200, get(503))
        assertEquals(2, hits.get())
    }

    @Test
    fun `a second restart answer is returned, not retried again`() {
        assertEquals(502, get(502, 502))
        assertEquals(2, hits.get())
    }

    @Test
    fun `other errors are not retried`() {
        assertEquals(500, get(500))
        assertEquals(404, get(404))
        assertEquals(2, hits.get())
    }

    @Test
    fun `an unreachable server fails at once, without a retry delay`() {
        val port = server.localPort
        server.close()
        val started = System.nanoTime()
        val result = runBlocking {
            HttpClient(CIO) { retryRestartingServer(delayMs = 5_000) }.use { client ->
                runCatching { client.get("http://127.0.0.1:$port/home-rows") }
            }
        }
        assertTrue(result.isFailure)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 4_000)
    }
}
