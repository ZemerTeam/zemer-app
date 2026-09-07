package com.jtech.zemer.utils

import io.ktor.client.engine.cio.CIOEngineConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckerDownloadClientTest {
    @Test
    fun `download client has no whole-request timeout`() {
        UpdateChecker.downloadHttpClient().use { client ->
            val config = client.engineConfig as CIOEngineConfig
            assertEquals(0L, config.requestTimeout)
        }
    }
}
