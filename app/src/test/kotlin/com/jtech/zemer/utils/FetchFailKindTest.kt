package com.jtech.zemer.utils

import com.jtech.zemer.sync.ContentServerException
import com.jtech.zemer.tracking.TrackingEvents
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.nio.channels.UnresolvedAddressException

class FetchFailKindTest {
    @Test
    fun `each failure maps to its telemetry kind`() {
        assertEquals("challenge", fetchFailKind(ContentServerException(403, "x", challenge = true)))
        assertEquals("http_403", fetchFailKind(ContentServerException(403, "x")))
        assertEquals("http_503", fetchFailKind(ContentServerException(503, "x")))
        assertEquals("timeout", fetchFailKind(HttpRequestTimeoutException("u", 1000L)))
        assertEquals("timeout", fetchFailKind(ConnectTimeoutException("c")))
        assertEquals("io", fetchFailKind(IOException("reset")))
        assertEquals("io", fetchFailKind(UnresolvedAddressException()))
        assertEquals("non_json", fetchFailKind(SerializationException("html")))
        assertEquals("empty", fetchFailKind(IllegalStateException("content mirror: empty /whitelist")))
        assertEquals("other", fetchFailKind(IllegalStateException("content mirror: missing gate in /whitelist/version")))
    }

    @Test
    fun `fetch_fail event shape`() {
        assertEquals(
            """{"type":"fetch_fail","t":5,"host":"content","path":"whitelist","kind":"http_403"}""",
            TrackingEvents.fetchFail(5, "content", "whitelist", "http_403").toString(),
        )
    }
}
