package com.jtech.zemer.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The ONE Ktor client every lyrics source shares (the resolver, LRCLIB, SimpMusic, Musixmatch): lenient JSON
 * that ignores unknown keys, 15 s request / 10 s connect / 15 s socket timeouts, and no throw on a non-2xx -
 * each caller judges its own status. Four private copies of this config once lived one per source.
 */
object LyricsHttp {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    val client: HttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 10_000; socketTimeoutMillis = 15_000 }
            expectSuccess = false
        }
    }
}
