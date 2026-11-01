package com.jtech.zemer.sig

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * This install's own signing key, issued by the content server's `POST /install` (signed with the build's version
 * key). Its [id] (`i<versionCode>.…`, debug `id<versionCode>.…`) names the build it was issued for; the server
 * retires it with that version. The [key] is used only for the request HMAC: never logged, never sent.
 */
class InstallCredential(val id: String, val key: ByteArray) {
    /** True when it was issued for the build whose version key id is [versionKeyId] ("39", debug "d39"). */
    fun issuedFor(versionKeyId: String): Boolean = id.startsWith("i$versionKeyId.")

    override fun toString() = "InstallCredential($id)"

    companion object {
        @Serializable
        private data class Wire(val id: String = "", val key: String = "")

        private val json = Json { ignoreUnknownKeys = true }

        /** A `/install` answer (or the stored copy), or null unless it carries an id for [versionKeyId] and a 32-byte key. */
        fun parse(text: String, versionKeyId: String): InstallCredential? {
            val wire = runCatching { json.decodeFromString(Wire.serializer(), text) }.getOrNull() ?: return null
            val key = runCatching { Base64.getDecoder().decode(wire.key) }.getOrNull()?.takeIf { it.size == 32 } ?: return null
            return InstallCredential(wire.id, key).takeIf { it.issuedFor(versionKeyId) }
        }

        /** The stored form, read back by [parse]. */
        fun encode(credential: InstallCredential): String =
            json.encodeToString(Wire.serializer(), Wire(credential.id, Base64.getEncoder().encodeToString(credential.key)))
    }
}
