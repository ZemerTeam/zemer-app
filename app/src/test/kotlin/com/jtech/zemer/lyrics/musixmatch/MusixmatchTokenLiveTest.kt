package com.jtech.zemer.lyrics.musixmatch

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Runs the app's OWN ktor request shape against Musixmatch token.get, to see if ktor is refused where curl is not. */
class MusixmatchTokenLiveTest {
    @Test
    fun `ktor token get from this JVM`() = runBlocking {
        assumeTrue(System.getenv("MXM_LIVE") == "1")
        val client = HttpClient(CIO)
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
        val body = client.get("https://apic-desktop.musixmatch.com/ws/1.1/token.get?app_id=web-desktop-app-v1.0") {
            header(HttpHeaders.UserAgent, ua); header(HttpHeaders.Cookie, "x-mxm-token-guid=")
        }.bodyAsText()
        println("KTOR-TOKEN " + body.take(200))
        client.close()
    }
}
