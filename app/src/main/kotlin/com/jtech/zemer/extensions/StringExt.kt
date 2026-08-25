package com.jtech.zemer.extensions

import androidx.sqlite.db.SimpleSQLiteQuery

inline fun <reified T : Enum<T>> String?.toEnum(defaultValue: T): T =
    if (this == null) {
        defaultValue
    } else {
        try {
            enumValueOf(this)
        } catch (_: IllegalArgumentException) {
            defaultValue
        }
    }

fun String.toSQLiteQuery(): SimpleSQLiteQuery = SimpleSQLiteQuery(this)
