package com.jtech.zemer.extensions

fun <T> tryOrNull(block: () -> T): T? =
    try {
        block()
    } catch (_: Exception) {
        null
    }
