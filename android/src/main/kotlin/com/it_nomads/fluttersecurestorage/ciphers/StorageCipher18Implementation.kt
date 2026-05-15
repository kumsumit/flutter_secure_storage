package com.it_nomads.fluttersecurestorage.ciphers

import android.content.Context
import android.util.Base64
import android.util.Log
import java.security.Key
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal open class StorageCipher18Implementation(
    context: Context,
    rsaCipher: KeyCipher,
    private val shouldCreateKeyIfMissing: Boolean = true,
) : StorageCipher {
    private val secureRandom = SecureRandom()
    private val cipher: Cipher = getCipher()
    private val secretKey: Key

    init {
        val preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val aesPreferencesKey = aesPreferencesKey
        val storedAesKey = preferences.getString(aesPreferencesKey, null)
        var restoredKey: Key? = null

        if (storedAesKey != null) {
            try {
                val encrypted = Base64.decode(storedAesKey, Base64.DEFAULT)
                restoredKey = rsaCipher.unwrap(encrypted, KEY_ALGORITHM)
            } catch (exception: Exception) {
                Log.e(TAG, "unwrap key failed", exception)
            }
        }

        secretKey = restoredKey ?: if (shouldCreateKeyIfMissing) {
            generateAndStoreKey(rsaCipher, preferences.edit(), aesPreferencesKey)
        } else {
            throw IllegalStateException("No legacy AES key found for migration.")
        }
    }

    protected open val aesPreferencesKey: String
        get() = "VGhpcyBpcyB0aGUga2V5IGZvciBhIHNlY3VyZSBzdG9yYWdlIEFFUyBLZXkK"

    protected open fun getCipher(): Cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")

    override fun encrypt(input: ByteArray): ByteArray {
        val iv = ByteArray(ivSize).also(secureRandom::nextBytes)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec(iv))

        val payload = cipher.doFinal(input)
        return iv + payload
    }

    override fun decrypt(input: ByteArray): ByteArray {
        val iv = input.copyOfRange(0, ivSize)
        val payload = input.copyOfRange(ivSize, input.size)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec(iv))
        return cipher.doFinal(payload)
    }

    protected open val ivSize: Int
        get() = 16

    protected open fun parameterSpec(iv: ByteArray): AlgorithmParameterSpec = IvParameterSpec(iv)

    private fun generateAndStoreKey(
        rsaCipher: KeyCipher,
        editor: android.content.SharedPreferences.Editor,
        aesPreferencesKey: String,
    ): Key {
        val keyBytes = ByteArray(KEY_SIZE).also(secureRandom::nextBytes)
        val key = SecretKeySpec(keyBytes, KEY_ALGORITHM)
        val encryptedKey = rsaCipher.wrap(key)
        editor.putString(aesPreferencesKey, Base64.encodeToString(encryptedKey, Base64.DEFAULT)).apply()
        return key
    }

    private companion object {
        const val TAG = "StorageCipher18Impl"
        const val KEY_SIZE = 32
        const val KEY_ALGORITHM = "AES"
        const val SHARED_PREFERENCES_NAME = "FlutterSecureKeyStorage"
    }
}
