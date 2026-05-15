package com.it_nomads.fluttersecurestorage.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.VisibleForTesting
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.util.Arrays
import javax.crypto.KeyGenerator

object MasterKeys {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val lock = Any()

    const val KEY_SIZE = MasterKey.DEFAULT_AES_GCM_MASTER_KEY_SIZE

    @JvmStatic
    @Throws(GeneralSecurityException::class, IOException::class)
    fun getOrCreate(keyGenParameterSpec: KeyGenParameterSpec): String {
        validate(keyGenParameterSpec)
        synchronized(lock) {
            if (!keyExists(keyGenParameterSpec.keystoreAlias)) {
                generateKey(keyGenParameterSpec)
            }
        }
        return keyGenParameterSpec.keystoreAlias
    }

    @VisibleForTesting
    internal fun validate(spec: KeyGenParameterSpec) {
        require(spec.keySize == KEY_SIZE) {
            "invalid key size, want $KEY_SIZE bits got ${spec.keySize} bits"
        }
        require(Arrays.equals(spec.blockModes, arrayOf(KeyProperties.BLOCK_MODE_GCM))) {
            "invalid block mode, want ${KeyProperties.BLOCK_MODE_GCM} got ${Arrays.toString(spec.blockModes)}"
        }
        require(spec.purposes == (KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)) {
            "invalid purposes mode, want PURPOSE_ENCRYPT | PURPOSE_DECRYPT got ${spec.purposes}"
        }
        require(Arrays.equals(spec.encryptionPaddings, arrayOf(KeyProperties.ENCRYPTION_PADDING_NONE))) {
            "invalid padding mode, want ${KeyProperties.ENCRYPTION_PADDING_NONE} got ${Arrays.toString(spec.encryptionPaddings)}"
        }
        require(!spec.isUserAuthenticationRequired || spec.userAuthenticationValidityDurationSeconds >= 1) {
            "per-operation authentication is not supported (UserAuthenticationValidityDurationSeconds must be >0)"
        }
    }

    @Throws(GeneralSecurityException::class)
    private fun generateKey(keyGenParameterSpec: KeyGenParameterSpec) {
        try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
                init(keyGenParameterSpec)
                generateKey()
            }
        } catch (providerException: ProviderException) {
            throw GeneralSecurityException(providerException.message, providerException)
        }
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    private fun keyExists(keyAlias: String): Boolean =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.containsAlias(keyAlias)
}
