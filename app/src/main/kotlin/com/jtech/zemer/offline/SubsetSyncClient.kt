package com.jtech.zemer.offline

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the subset manifest and shards from the Zemer search server. Deliberately separate from
 * [com.jtech.zemer.search.ZemerSearchClient] so a large shard download never contends with the
 * latency-sensitive search client (longer timeouts here, and no JSON body handling for shards).
 *
 * No `ContentEncoding` plugin is installed, so [downloadShard] returns the shard's raw
 * `application/gzip` payload untouched — which is exactly what [subsetShardHash] is computed over.
 */
@Singleton
class SubsetSyncClient @Inject constructor() {

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = SHARD_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** The current server manifest. Throws [IOException] on a non-2xx response or transport failure. */
    suspend fun fetchManifest(): SubsetManifest {
        val response: HttpResponse = client.get("$BASE_URL/subset/manifest")
        if (!response.status.isSuccess()) {
            throw IOException("subset manifest returned HTTP ${response.status.value}")
        }
        return json.decodeFromString(SubsetManifest.serializer(), response.bodyAsText())
    }

    /** Raw gzipped bytes of one shard. Throws [IOException] on a non-2xx response or transport failure. */
    suspend fun downloadShard(name: String): ByteArray {
        require(isValidShardName(name)) { "invalid shard name: $name" }
        val response: HttpResponse = client.get("$BASE_URL/subset/$name")
        if (!response.status.isSuccess()) {
            throw IOException("subset shard $name returned HTTP ${response.status.value}")
        }
        return response.readRawBytes()
    }

    companion object {
        const val BASE_URL = "https://search.zemer.io"
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val SHARD_TIMEOUT_MS = 60_000L
    }
}
