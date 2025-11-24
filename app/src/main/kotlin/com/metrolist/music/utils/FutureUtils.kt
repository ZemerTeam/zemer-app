package com.metrolist.music.utils

import androidx.concurrent.futures.CallbackToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import java.util.concurrent.Executor

fun <T> CoroutineScope.future(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): ListenableFuture<T> {
    return CallbackToFutureAdapter.getFuture { completer ->
        val job = this.launch(context) {
            try {
                val result = block()
                completer.set(result)
            } catch (cancelled: CancellationException) {
                completer.setCancelled()
            } catch (throwable: Throwable) {
                completer.setException(throwable)
            }
        }

        val directExecutor = Executor { runnable -> runnable.run() }
        completer.addCancellationListener({ job.cancel() }, directExecutor)
        "CoroutineScope.future"
    }
}

fun <T> immediateFuture(value: T): ListenableFuture<T> {
    return CallbackToFutureAdapter.getFuture { completer ->
        completer.set(value)
        "ImmediateFuture"
    }
}

fun <T> pendingFuture(): ListenableFuture<T> {
    return CallbackToFutureAdapter.getFuture { "PendingFuture" }
}
