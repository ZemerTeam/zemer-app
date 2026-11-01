package com.jtech.zemer.sig

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The install credential at rest: AES-GCM under a non-exportable Android Keystore key, in `noBackupFilesDir`
 * (a restored backup or another device starts without it and registers again). Every failure reads as "none".
 */
internal class InstallKeyStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    fun load(versionKeyId: String): InstallCredential? = runCatching {
        val stored = file.readFully()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: return null, GCMParameterSpec(128, stored, 0, IV_LEN))
        InstallCredential.parse(cipher.doFinal(stored, IV_LEN, stored.size - IV_LEN).decodeToString(), versionKeyId)
    }.getOrNull()

    fun save(credential: InstallCredential): Boolean = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, existingKey() ?: newKey()) }
        val sealed = cipher.iv + cipher.doFinal(InstallCredential.encode(credential).toByteArray())
        val out = file.startWrite()
        runCatching { out.write(sealed); file.finishWrite(out) }.onFailure { file.failWrite(out) }.getOrThrow()
    }.isSuccess

    fun clear() {
        runCatching { file.delete() }
    }

    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(ALIAS, null) as? SecretKey

    private fun newKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "zemer_install_credential"
        const val FILE_NAME = "install_credential.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
