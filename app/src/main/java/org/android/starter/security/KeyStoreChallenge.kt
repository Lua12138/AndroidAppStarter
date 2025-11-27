package org.android.starter.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.ProviderException
import java.security.spec.ECGenParameterSpec
import java.util.Date

typealias PEM = ByteArray

fun PEM?.toPemString(): String {
    if (this == null) {
        return "NULL"
    }
    val encoded = Base64.encodeToString(this, Base64.NO_WRAP)
    val builder = StringBuilder()
    builder.append("-----BEGIN CERTIFICATE-----\n")
    builder.append(encoded.chunked(64).joinToString("\n"))
    builder.append("\n-----END CERTIFICATE-----\n")
    return builder.toString()
}

object KeyStoreChallenge {
    private val TAG = "KeyAttestation"

    /**
     * How to verify see to
     * https://developer.android.com/privacy-and-security/security-key-attestation?hl=zh-cn#key_attestation_ext_schema
     */
    fun <T> use(alias: String, challenge: ByteArray, block: (Boolean, List<PEM>) -> T): T {
        if (!this.challenge(alias, true, challenge)) {
            if (!this.challenge(alias, false, challenge)) {
                return block(false, listOf())
            }
        }

        val (okay, certs) = this.load(alias)
        return block(okay, certs)
    }

    fun load(alias: String): Pair<Boolean, List<PEM>> {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val certChain = keyStore.getCertificateChain(alias)
        if (certChain == null || certChain.isEmpty()) {
            Log.e(TAG, "cannot found certificate chain within $alias")
            return Pair(false, listOf())
        }

        val certs = mutableListOf<ByteArray>()
        certChain.forEach { cert ->
            certs.add(cert.encoded)
        }

        return Pair(certs.isNotEmpty(), certs)
    }

    fun challenge(
        alias: String,
        devicePropertiesAttestationIncluded: Boolean,
        challenge: ByteArray
    ): Boolean {
        if (challenge.isNotEmpty() && challenge.size < 16) {
            throw RuntimeException("challenge must be gt 16 bytes or be empty(disable challenge)")
        }
        val now = Date()
        // https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html#parameterspec-names
        val spec = ECGenParameterSpec("secp256r1")
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).setAlgorithmParameterSpec(spec)
            .setDigests("SHA-256", "SHA-384", "SHA-512")
            .setKeyValidityStart(now)

        if (Build.VERSION.SDK_INT > 17 && challenge.isNotEmpty()) {
            builder.setAttestationChallenge(challenge)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setDevicePropertiesAttestationIncluded(devicePropertiesAttestationIncluded)
        }

        val generator =
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(builder.build())

        val keyPair = try {
            // https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setDevicePropertiesAttestationIncluded(boolean)
            // KeyGenerator.generateKey() will throw ProviderException if device properties attestation fails or is not supported.
            generator.generateKeyPair()
        } catch (_: ProviderException) {
            return false
        }
        Log.d(TAG, "generate public key success: ${keyPair.public.encoded.toPemString()}")
        // It should be null if the operator from tee
        Log.d(TAG, "generate private key success: ${keyPair.private?.encoded.toPemString()}")

        return true
    }
}