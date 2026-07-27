package com.jtech.zemer.offline

import java.security.MessageDigest

/**
 * The content address the manifest uses for a shard: the first 16 hex characters (8 bytes) of
 * sha256 over the **raw gzipped shard bytes** (as served by `GET /subset/<name>`, before
 * decompression). A downloaded shard is only accepted when this matches its manifest [SubsetShard.hash],
 * so a truncated or corrupted download can never be committed.
 */
fun subsetShardHash(gzippedBytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(gzippedBytes)
    return buildString(HASH_HEX_LEN) {
        for (i in 0 until HASH_HEX_LEN / 2) {
            val b = digest[i].toInt() and 0xff
            append(HEX[b ushr 4])
            append(HEX[b and 0x0f])
        }
    }
}

private const val HASH_HEX_LEN = 16
private val HEX = "0123456789abcdef".toCharArray()
