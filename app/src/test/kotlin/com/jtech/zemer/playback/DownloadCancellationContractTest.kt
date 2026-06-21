package com.jtech.zemer.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Locks the control-flow contract that `MediaStoreDownloadManager.performDownload` depends on. That
 * method cannot be instantiated in a plain JVM test (it needs a Context, MediaStoreHelper and the Room
 * DB — Robolectric-level infrastructure), so instead of faking it we pin the exact exception-handling
 * shape it uses, which would regress silently if someone "simplified" the dedicated
 * `catch (CancellationException)` rethrow away.
 *
 * The bug this guards: `performDownload` retries inside `catch (e: Exception) { ... }`. Because
 * kotlinx's [CancellationException] IS an [Exception], a naive single catch SWALLOWS cancellation —
 * a cancelled download then resurrects itself, overwrites the CANCELLED state and pins the foreground
 * service open forever. The fix is to rethrow cancellation before the retry branch.
 */
class DownloadCancellationContractTest {

    @Test
    fun cancellationException_isAnException_soABareCatchWouldSwallowIt() {
        // Documents WHY the dedicated rethrow is required.
        assertTrue(CancellationException("x") is Exception)
    }

    /** The FIXED shape: rethrow cancellation, then retry other failures. */
    private suspend fun retryingWork(attempts: AtomicInteger, work: suspend () -> Unit) {
        try {
            work()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            attempts.incrementAndGet()
            delay(1)
            retryingWork(attempts, work)
        }
    }

    @Test
    fun cancelling_doesNotTriggerRetries() = runBlocking {
        val retries = AtomicInteger(0)
        val started = AtomicInteger(0)
        val job = launch(Dispatchers.Default) {
            retryingWork(retries) {
                started.incrementAndGet()
                delay(10_000) // suspends until cancelled
            }
        }
        while (started.get() == 0) yield()
        job.cancel()
        job.join()
        // The fixed structure must NOT have entered the retry branch on cancellation.
        assertEquals(0, retries.get())
    }

    @Test
    fun realFailures_stillRetry() = runBlocking {
        val retries = AtomicInteger(0)
        var calls = 0
        retryingWork(retries) {
            calls++
            if (calls < 3) throw RuntimeException("transient")
            // succeeds on the 3rd attempt
        }
        assertEquals(2, retries.get())
    }
}
