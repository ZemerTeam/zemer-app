package com.jtech.zemer.crash

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.IdentityHashMap

/**
 * Per-launch non-fatal dedup: the first value of each key is held, repeats raise its count, until [drain] hands them
 * out; a drained key is done for the launch, and at most [max] keys are reported per launch. Not thread-safe (the
 * reporter holds its lock).
 */
internal class PerLaunchDedup<T>(private val max: Int) {
    private class Held<T>(val value: T) { var count = 1 }

    private val held = LinkedHashMap<String, Held<T>>()
    private val done = HashSet<String>()

    /** True when [key] is new and now held (the caller then schedules a drain). */
    fun offer(key: String, value: () -> T): Boolean {
        if (key in done) return false
        held[key]?.let { it.count++; return false }
        if (done.size + held.size >= max) return false
        held[key] = Held(value())
        return true
    }

    /** The held values with their counts, oldest first. */
    fun drain(): List<Pair<T, Int>> = held.values.map { it.value to it.count }.also {
        done += held.keys
        held.clear()
    }
}

/** The build and device a report comes from (no identifiers). */
internal data class CrashEnvironment(
    val versionCode: Int,
    val versionName: String,
    val commit: String,
    val runNumber: Int,
    val buildType: String,
    val sdk: Int,
    val manufacturer: String,
    val model: String,
    val abi: String,
    val debug: Boolean,
)

/**
 * The `POST /v1/crash` body (contract: handoff-docs/zemer-app-crash-reporting-request.md), built purely so it is
 * JVM-tested: the cause chain outermost first with suppressed exceptions, scrubbed text, and the size caps.
 */
internal object CrashReport {
    const val MAX_EXCEPTION_CHARS = 64 * 1024
    const val MAX_BREADCRUMBS = 200
    const val MAX_BREADCRUMB_CHARS = 32 * 1024
    private const val MAX_CAUSES = 10

    fun build(
        kind: String,
        throwable: Throwable,
        thread: String,
        occurredAt: Long,
        count: Int,
        breadcrumbs: List<String>,
        env: CrashEnvironment,
        secrets: Collection<String> = emptyList(),
    ): JsonObject = buildJsonObject {
        put("kind", kind)
        put("occurredAt", occurredAt)
        put("sentAt", occurredAt)
        put("count", count)
        put("app", buildJsonObject {
            put("versionCode", env.versionCode)
            put("versionName", env.versionName)
            put("commit", env.commit)
            put("runNumber", env.runNumber)
            put("buildType", env.buildType)
        })
        put("device", buildJsonObject {
            put("sdk", env.sdk)
            put("manufacturer", env.manufacturer)
            put("model", env.model)
            put("abi", env.abi)
        })
        put("thread", thread)
        put("exception", JsonArray(causeChain(throwable, secrets)))
        put("breadcrumbs", buildJsonArray { capBreadcrumbs(breadcrumbs, secrets).forEach { add(JsonPrimitive(it)) } })
        put("debug", env.debug)
    }

    /** With a new `sentAt`: a queued report is stamped when it actually goes out. */
    fun withSentAt(report: JsonObject, sentAt: Long) = JsonObject(report + ("sentAt" to JsonPrimitive(sentAt)))

    /** The app's per-launch dedup key: the root cause's class and top frames (the server groups on its own). */
    fun fingerprint(throwable: Throwable): String {
        val root = chain(throwable).last()
        return (listOf(root.javaClass.name) + root.stackTrace.take(5).map { "${it.className}.${it.methodName}" })
            .joinToString("|")
    }

    /** A cancellation anywhere in the cause chain: a coroutine being cancelled, not an error worth a report. */
    fun isCancellation(throwable: Throwable): Boolean = chain(throwable).any { it is java.util.concurrent.CancellationException }

    /**
     * A plain connectivity failure (refused, unresolved, timed out) that names no Zemer server: expected on filtered
     * or offline networks, not an app bug. Failed reads of Zemer's own servers are counted by the fetch_fail event.
     */
    fun isOffZemerConnectivityFailure(throwable: Throwable): Boolean {
        val causes = chain(throwable)
        val connectivity = causes.any {
            it is java.net.ConnectException || it is java.net.UnknownHostException ||
                it is java.net.SocketTimeoutException || it is java.nio.channels.UnresolvedAddressException ||
                it.message?.contains("ECONNREFUSED") == true
        }
        return connectivity && causes.none { it.message?.contains("zemer.io", ignoreCase = true) == true }
    }

    private fun chain(throwable: Throwable): List<Throwable> {
        val seen = IdentityHashMap<Throwable, Unit>()
        return generateSequence(throwable) { it.cause }
            .takeWhile { seen.put(it, Unit) == null }
            .take(MAX_CAUSES)
            .toList()
    }

    private fun causeChain(throwable: Throwable, secrets: Collection<String>): List<JsonObject> {
        val stacks = chain(throwable).map { t -> t.stackTrace.joinToString("\n") { "at $it" } }.toMutableList()
        // over the cap: halve the longest stack until it fits (the top frames are kept, the tail goes)
        while (stacks.sumOf { it.length } > MAX_EXCEPTION_CHARS) {
            val i = stacks.indices.maxBy { stacks[it].length }
            stacks[i] = stacks[i].take(stacks[i].length / 2)
        }
        return chain(throwable).mapIndexed { i, t ->
            buildJsonObject {
                put("class", t.javaClass.name)
                put("message", t.message?.let { CrashScrubber.scrub(it, secrets).take(2_000) })
                put("stack", stacks[i])
                put("suppressed", buildJsonArray {
                    t.suppressed.take(5).forEach { s ->
                        add(buildJsonObject {
                            put("class", s.javaClass.name)
                            put("message", s.message?.let { CrashScrubber.scrub(it, secrets).take(2_000) })
                            put("stack", s.stackTrace.take(20).joinToString("\n") { "at $it" })
                        })
                    }
                })
            }
        }
    }

    /** The newest lines that fit: at most [MAX_BREADCRUMBS] and [MAX_BREADCRUMB_CHARS], scrubbed. */
    private fun capBreadcrumbs(lines: List<String>, secrets: Collection<String>): List<String> {
        val out = ArrayDeque<String>()
        var chars = 0
        for (line in lines.asReversed()) {
            if (out.size == MAX_BREADCRUMBS) break
            val scrubbed = CrashScrubber.scrub(line, secrets).take(1_000)
            if (chars + scrubbed.length > MAX_BREADCRUMB_CHARS) break
            out.addFirst(scrubbed)
            chars += scrubbed.length
        }
        return out
    }
}
