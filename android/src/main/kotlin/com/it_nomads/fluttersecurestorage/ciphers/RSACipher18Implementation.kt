package com.it_nomads.fluttersecurestorage.ciphers

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.math.BigInteger
import java.security.Key
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.AlgorithmParameterSpec
import java.util.Calendar
import java.util.Locale
import javax.crypto.Cipher
import javax.security.auth.x500.X500Principal

internal open class RSACipher18Implementation(
    protected val context: Context,
) : KeyCipher {
    protected val keyAlias: String = createKeyAlias()

    init {
        createRSAKeysIfNeeded()
    }

    protected open fun createKeyAlias(): String =
        "${context.packageName}.FlutterSecureStoragePluginKey"

    override fun wrap(key: Key): ByteArray {
        val cipher = getRSACipher()
        cipher.init(Cipher.WRAP_MODE, publicKey, algorithmParameterSpec)
        return cipher.wrap(key)
    }

    override fun unwrap(wrappedKey: ByteArray, algorithm: String): Key {
        val cipher = getRSACipher()
        cipher.init(Cipher.UNWRAP_MODE, privateKey, algorithmParameterSpec)
        return cipher.unwrap(wrappedKey, algorithm, Cipher.SECRET_KEY)
    }

    private val privateKey: PrivateKey
        get() {
            val key = keyStore.getKey(keyAlias, null)
                ?: error("No key found under alias: $keyAlias")
            return key as? PrivateKey ?: error("Not an instance of a PrivateKey")
        }

    private val publicKey: PublicKey
        get() {
            val certificate = keyStore.getCertificate(keyAlias)
                ?: error("No certificate found under alias: $keyAlias")
            return certificate.publicKey ?: error("No key found under alias: $keyAlias")
        }

    protected open fun getRSACipher(): Cipher =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidOpenSSL")
        } else {
            Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidKeyStoreBCWorkaround")
        }

    protected open val algorithmParameterSpec: AlgorithmParameterSpec?
        get() = null

    private fun createRSAKeysIfNeeded() {
        if (keyStore.getKey(keyAlias, null) == null) {
            createKeys()
        }
    }

    private fun setLocale(locale: Locale) {
        Locale.setDefault(locale)
        val config: Configuration = context.resources.configuration
        config.setLocale(locale)
        context.createConfigurationContext(config)
    }

    private fun createKeys() {
        val localeBeforeFakingEnglishLocale = Locale.getDefault()
        try {
            setLocale(Locale.ENGLISH)
            val start = Calendar.getInstance()
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 25) }

            val generator = KeyPairGenerator.getInstance(TYPE_RSA, KEYSTORE_PROVIDER_ANDROID)
            val spec = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                makeAlgorithmParameterSpecLegacy(start, end)
            } else {
                makeAlgorithmParameterSpec(start, end)
            }

            generator.initialize(spec)
            generator.generateKeyPair()
        } finally {
            setLocale(localeBeforeFakingEnglishLocale)
        }
    }

    @Suppress("DEPRECATION")
    private fun makeAlgorithmParameterSpecLegacy(
        start: Calendar,
        end: Calendar,
    ): AlgorithmParameterSpec =
        KeyPairGeneratorSpec.Builder(context)
            .setAlias(keyAlias)
            .setSubject(X500Principal("CN=$keyAlias"))
            .setSerialNumber(BigInteger.valueOf(1))
            .setStartDate(start.time)
            .setEndDate(end.time)
            .build()

    @RequiresApi(Build.VERSION_CODES.M)
    protected open fun makeAlgorithmParameterSpec(
        start: Calendar,
        end: Calendar,
    ): AlgorithmParameterSpec =
        KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT,
        )
            .setCertificateSubject(X500Principal("CN=$keyAlias"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            .setCertificateSerialNumber(BigInteger.valueOf(1))
            .setCertificateNotBefore(start.time)
            .setCertificateNotAfter(end.time)
            .build()

    private val keyStore: KeyStore
        get() = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID).apply { load(null) }

    private companion object {
        const val KEYSTORE_PROVIDER_ANDROID = "AndroidKeyStore"
        const val TYPE_RSA = "RSA"
    }
}
