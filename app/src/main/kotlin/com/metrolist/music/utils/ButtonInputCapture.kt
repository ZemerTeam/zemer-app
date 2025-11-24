package com.metrolist.music.utils

import android.view.KeyEvent
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

object ButtonInputCapture {
    private val capturing = AtomicBoolean(false)
    private val skipNext = AtomicBoolean(false)
    private val channel = Channel<KeyEvent>(Channel.BUFFERED)
    val events: Flow<KeyEvent> = channel.receiveAsFlow()

    fun beginCapture(skipFirstEvent: Boolean = true) {
        capturing.set(true)
        skipNext.set(skipFirstEvent)
        clear()
    }

    fun endCapture() {
        capturing.set(false)
        skipNext.set(false)
        clear()
    }

    fun notify(event: KeyEvent) {
        if (!capturing.get()) return
        if (skipNext.compareAndSet(true, false)) return
        channel.trySend(event)
    }

    fun clear() {
        while (channel.tryReceive().isSuccess) {
            // drain
        }
    }

    fun isCapturing(): Boolean = capturing.get()
}
