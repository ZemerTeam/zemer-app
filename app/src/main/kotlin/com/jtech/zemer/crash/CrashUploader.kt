package com.jtech.zemer.crash

import com.jtech.zemer.net.ZemerHeaders
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import java.io.File
import java.util.UUID

/** What to do with a queued report after one send attempt. */
internal enum class CrashSendOutcome { DONE, KEEP }

/**
 * 2xx: stored. 400/413: rejected for good (never retried, it would fail again). Anything else (429, 5xx, no
 * answer as `null`): kept for the next attempt.
 */
internal fun crashSendOutcome(status: Int?): CrashSendOutcome = when {
    status == null -> CrashSendOutcome.KEEP
    status in 200..299 || status == 400 || status == 413 -> CrashSendOutcome.DONE
    else -> CrashSendOutcome.KEEP
}

/** Reports waiting to be sent, one JSON file each under [dir], oldest first, at most [max]. */
internal class CrashQueue(private val dir: File, private val max: Int = 50) {
    fun add(report: JsonObject) {
        dir.mkdirs()
        val name = "${System.currentTimeMillis()}-${UUID.randomUUID()}"
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(report.toString())
        tmp.renameTo(File(dir, "$name.json"))
        files().dropLast(max).forEach { it.delete() }
    }

    fun files(): List<File> = dir.listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.name }.orEmpty()
}

/** Sends queued reports to `tracking.zemer.io/v1/crash`, signed like every Zemer request. */
internal class CrashUploader(private val queue: CrashQueue, private val endpoint: String = ENDPOINT) {
    private val client by lazy {
        HttpClient(CIO) {
            install(ZemerHeaders.KtorPlugin)
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 10_000
            }
            expectSuccess = false
        }
    }

    /** Sends oldest first and stops at the first report the server could not take now. */
    suspend fun sendAll() {
        for (file in queue.files()) {
            val report = runCatching { Json.parseToJsonElement(file.readText()) as JsonObject }.getOrNull()
            if (report == null) { file.delete(); continue }
            val status = runCatching {
                client.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(CrashReport.withSentAt(report, System.currentTimeMillis()).toString())
                }.status.value
            }.onFailure { Timber.tag(TAG).w("crash report not sent: %s", it.javaClass.simpleName) }.getOrNull()
            if (crashSendOutcome(status) == CrashSendOutcome.KEEP) return
            file.delete()
        }
    }

    companion object {
        const val ENDPOINT = "https://tracking.zemer.io/v1/crash"
        private const val TAG = "Zemer_Crash"
    }
}
