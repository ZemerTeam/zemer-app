package com.jtech.zemer.pass

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * The hardware-backed key behind the pass: an EC P-256 signing key in AndroidKeyStore whose attestation
 * certificate chain proves to the server that this is the genuine app on a real device. StrongBox when the
 * device has it, else the TEE; a device that can only attest in software is refused by the server.
 */
internal object PassKeystore {
    private const val ALIAS = "zemer_pass"
    private const val PROVIDER = "AndroidKeyStore"

    private fun keyStore() = KeyStore.getInstance(PROVIDER).apply { load(null) }

    /** Replaces the key with a new one attested over [challenge]; returns its chain, leaf first, as standard base64 DER. */
    fun createAttested(challenge: ByteArray): List<String> {
        val keyStore = keyStore()
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(challenge)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
        val strongBoxMade = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && runCatching {
            generator.initialize(spec(strongBox = true))
            generator.generateKeyPair()
        }.isSuccess
        if (!strongBoxMade) {
            generator.initialize(spec(strongBox = false))
            generator.generateKeyPair()
        }
        val chain = keyStore.getCertificateChain(ALIAS)
        check(!chain.isNullOrEmpty()) { "no attestation chain" }
        return chain.map { Base64.getEncoder().encodeToString(it.encoded) }
    }

    /** SHA256withECDSA (DER) over [message] with the attested key, standard base64; null when there is no key. */
    fun sign(message: ByteArray): String? {
        val key = keyStore().getKey(ALIAS, null) as? PrivateKey ?: return null
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update(message)
            sign()
        }
        return Base64.getEncoder().encodeToString(signature)
    }
}
