package com.jtech.zemer.lyrics

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread

/**
 * A lyrics page that arrives gzip-compressed even though the client never asked for it must still be read as
 * text by the shared [LyricsHttp] client (regression: the raw compressed bytes were parsed as lyrics and shown
 * as garbage). Served over real HTTP by a minimal local server.
 */
class LyricsHttpDecodeTest {
    private lateinit var server: ServerSocket
    private val page = "<html><body><p>שורה ראשונה</p><p>שורה שנייה</p><p>שורה שלישית</p><p>שורה רביעית</p></body></html>"

    private fun gzip(text: String): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.encodeToByteArray()) } }.toByteArray()

    @Before
    fun setUp() {
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    socket.use {
                        val input = it.getInputStream().bufferedReader()
                        while (input.readLine()?.isNotEmpty() == true) Unit
                        val body = gzip(page)
                        it.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Encoding: gzip\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".encodeToByteArray())
                            write(body)
                            flush()
                        }
                    }
                }
            }
        }
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `a gzip page is decoded to its text`() = runBlocking {
        val text = LyricsHttp.client.get("http://127.0.0.1:${server.localPort}/song").bodyAsText()
        assertEquals(page, text)
        assertTrue(LyricsUtils.looksLikeText(text))
    }

    @Test
    fun `the undecoded bytes would have been rejected as lyrics`() {
        val garbage = gzip(page + page + page).decodeToString()
        assertFalse(LyricsUtils.looksLikeText(garbage))
        assertFalse(LyricsUtils.hasLyricBody(garbage))
    }
}
