package com.jtech.zemer.utils

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

object EnvironmentPaths {
    val DEFAULT_RELATIVE_DOWNLOAD_PATH: String = "${Environment.DIRECTORY_MUSIC}/Zemer"

    fun String.toRelativePath(): String {
        val documentPath =
            if (startsWith("content://")) {
                runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(this)) }.getOrNull()
            } else {
                this
            }

        return documentPath?.substringAfter(":", documentPath)?.trim('/') ?: ""
    }

    fun String.toStorageRoot(): File = File(Environment.getExternalStorageDirectory(), toRelativePath())

    fun String.toUserFacingPath(): String = toRelativePath().ifBlank { DEFAULT_RELATIVE_DOWNLOAD_PATH }
}
