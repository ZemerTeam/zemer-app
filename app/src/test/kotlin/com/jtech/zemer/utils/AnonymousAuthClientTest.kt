package com.jtech.zemer.utils

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for issue #284 ("anonymous login times out too soon"). The anonymous credential
 * fetch used to run on a bare `HttpClient()` whose engine default timeout (~5-15 s) was too short for
 * users on slow/spotty service. These bounds keep the ceilings generous so the guard fails loudly if
 * someone shrinks them back toward the old defaults.
 *
 * The live network + Compose path itself is not JVM-unit-testable (the project has no Robolectric), so
 * this guards the intent — the tuned timeout constants — rather than the request.
 */
class AnonymousAuthClientTest {

    @Test
    fun `request timeout is generous enough for slow connections`() {
        assertTrue(
            "request timeout must stay well above the old ~15s engine default",
            AnonymousAuthClient.REQUEST_TIMEOUT_MS >= 60_000L
        )
    }

    @Test
    fun `connect timeout is generous enough for slow connections`() {
        assertTrue(
            "connect timeout must stay well above the old ~5-10s engine default",
            AnonymousAuthClient.CONNECT_TIMEOUT_MS >= 30_000L
        )
    }

    @Test
    fun `socket timeout is generous enough for slow connections`() {
        assertTrue(
            "socket timeout must stay generous for weak connections",
            AnonymousAuthClient.SOCKET_TIMEOUT_MS >= 60_000L
        )
    }
}
