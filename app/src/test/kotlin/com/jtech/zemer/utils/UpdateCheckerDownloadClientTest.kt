package com.jtech.zemer.utils

import com.jtech.zemer.utils.updater.UpdateDownloadFailure
import com.jtech.zemer.utils.updater.classifyUpdateDownloadFailure
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class UpdateCheckerDownloadClientTest {

    @Test
    fun `download client has no whole-request timeout but a bounded idle timeout`() {
        UpdateChecker.downloadHttpClient().use { client ->
            val config = client.engineConfig as CIOEngineConfig
            assertEquals(0L, config.requestTimeout)
            assertEquals(UpdateChecker.DOWNLOAD_IDLE_TIMEOUT_MS, config.endpoint.socketTimeout)
        }
    }

    /**
     * A server that accepts the connection, sends complete headers with a body length, then never
     * sends a byte. Without the idle bound this read would hang forever (the request timeout is
     * disabled); with it the download fails as a TIMEOUT within the idle interval.
     */
    @Test
    fun `an established connection that sends no body bytes fails as a timeout`() {
        ServerSocket(0).use { server ->
            val accepted = thread(isDaemon = true) {
                runCatching {
                    val socket = server.accept()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\nContent-Type: application/zip\r\n\r\n".toByteArray())
                        flush()
                    }
                    Thread.sleep(10_000) // hold the socket open, silent
                    socket.close()
                }
            }
            val started = System.currentTimeMillis()
            val thrown: Throwable? = runCatching {
                runBlocking {
                    UpdateChecker.downloadHttpClient(idleTimeoutMs = 500).use { client ->
                        client.prepareGet("http://127.0.0.1:${server.localPort}/stalled.zip").execute { response ->
                            val channel = response.bodyAsChannel()
                            val buffer = ByteArray(8192)
                            while (!channel.isClosedForRead) channel.readAvailable(buffer)
                        }
                    }
                }
            }.exceptionOrNull()
            if (thrown == null) fail("stalled body read did not fail")
            val elapsed = System.currentTimeMillis() - started
            assertTrue("failed after ${elapsed}ms, expected within the idle bound", elapsed < 8_000)
            assertEquals(UpdateDownloadFailure.TIMEOUT, classifyUpdateDownloadFailure(thrown!!))
            accepted.interrupt()
        }
    }
}
