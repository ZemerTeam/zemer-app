package com.jtech.zemer.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * The artist list is large, and a fixed time cap failed every slow link before it could finish. With a stall
 * limit, a body that keeps arriving completes however long it takes (up to the total cap), and one that stops
 * fails after the stall limit. Same engine as the app (CIO), timings scaled down.
 */
class ContentReadTimeoutsTest {
    private lateinit var server: ServerSocket

    /** Each response sends [chunks] body chunks with [gapMs] between them. */
    @Volatile
    private var chunks = 6

    @Volatile
    private var gapMs = 400L

    @Before
    fun start() {
        server = ServerSocket(0, 10, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    socket.use { s ->
                        val reader = s.getInputStream().bufferedReader()
                        while (reader.readLine()?.isNotEmpty() == true) Unit
                        val out = s.getOutputStream()
                        out.write("HTTP/1.1 200 OK\r\nContent-Length: ${chunks * 10}\r\nConnection: close\r\n\r\n".toByteArray())
                        out.flush()
                        repeat(chunks) {
                            Thread.sleep(gapMs)
                            out.write("0123456789".toByteArray())
                            out.flush()
                        }
                    }
                }
            }
        }
    }

    @After
    fun stop() = server.close()

    private fun read(totalMs: Long, stallMs: Long?): Result<String> = runBlocking {
        HttpClient(CIO) { install(HttpTimeout) }.use { client ->
            runCatching {
                client.get("http://127.0.0.1:${server.localPort}/whitelist") {
                    timeout { contentReadTimeouts(totalMs, stallMs) }
                }.bodyAsText()
            }
        }
    }

    @Test
    fun `a slow body that keeps arriving completes past what a short total cap would allow`() {
        // 6 chunks, 400 ms apart: about 2.4 s in total, never more than 0.4 s without data
        assertTrue(read(totalMs = 1_500, stallMs = null).isFailure) // the old shape: a short fixed cap fails
        assertEquals(60, read(totalMs = 10_000, stallMs = 1_000).getOrThrow().length)
    }

    @Test
    fun `a body that stops arriving fails after the stall limit, not the total cap`() {
        chunks = 2
        gapMs = 3_000
        val started = System.currentTimeMillis()
        assertTrue(read(totalMs = 20_000, stallMs = 1_000).isFailure)
        assertTrue(System.currentTimeMillis() - started < 3_000)
    }
}
