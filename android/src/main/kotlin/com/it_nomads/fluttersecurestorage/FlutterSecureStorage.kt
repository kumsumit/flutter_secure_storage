package com.it_nomads.fluttersecurestorage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.it_nomads.fluttersecurestorage.ciphers.StorageCipher
import com.it_nomads.fluttersecurestorage.ciphers.StorageCipherFactory
import com.it_nomads.fluttersecurestorage.crypto.EncryptedSharedPreferences
import com.it_nomads.fluttersecurestorage.crypto.MasterKey
import java.nio.charset.StandardCharsets

class FlutterSecureStorage(
    context: Context,
    options: Map<String, Any?>,
) {
    private var preferencesKeyPrefix = options.stringOption(PREF_OPTION_PREFIX, DEFAULT_KEY_PREFIX)
    private val encryptedPreferences: SharedPreferences

    init {
        val sharedPreferencesName = options.stringOption(PREF_OPTION_NAME, DEFAULT_PREF_NAME)
        val deleteOnFailure = (options[PREF_OPTION_DELETE_ON_FAILURE] as? String).toBoolean()
        encryptedPreferences = getEncryptedSharedPreferences(
            deleteOnFailure = deleteOnFailure,
            options = options,
            context = context.applicationContext,
            sharedPreferencesName = sharedPreferencesName,
        )
    }

    fun containsKey(key: String?): Boolean = encryptedPreferences.contains(addPrefixToKey(key))

    fun read(key: String?): String? = encryptedPreferences.getString(addPrefixToKey(key), null)

    fun write(key: String?, value: String) {
        encryptedPreferences.edit().putString(addPrefixToKey(key), value).apply()
    }

    fun delete(key: String?) {
        encryptedPreferences.edit().remove(addPrefixToKey(key)).apply()
    }

    fun deleteAll() {
        encryptedPreferences.edit().clear().apply()
    }

    fun readAll(): Map<String, String> =
        encryptedPreferences.all.mapNotNull { (key, value) ->
            if (key.startsWith(preferencesKeyPrefix) && value is String) {
                key.removePrefix("$preferencesKeyPrefix" + "_") to value
            } else {
                null
            }
        }.toMap()

    private fun addPrefixToKey(key: String?): String = "${preferencesKeyPrefix}_$key"

    private fun getEncryptedSharedPreferences(
        deleteOnFailure: Boolean,
        options: Map<String, Any?>,
        context: Context,
        sharedPreferencesName: String,
    ): SharedPreferences =
        try {
            initializeEncryptedSharedPreferencesManager(context, sharedPreferencesName).also { target ->
                if (!target.getBoolean(PREF_KEY_MIGRATED, false)) {
                    migrateToEncryptedPreferences(
                        context = context,
                        sharedPreferencesName = sharedPreferencesName,
                        target = target,
                        deleteOnFailure = deleteOnFailure,
                        options = options,
                    )
                }
            }
        } catch (exception: Exception) {
            if (!deleteOnFailure) {
                Log.w(TAG, "initialization failed, resetOnError false, so throwing exception.", exception)
                throw exception
            }

            Log.w(TAG, "initialization failed, resetting storage", exception)
            context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE).edit().clear().apply()

            try {
                initializeEncryptedSharedPreferencesManager(context, sharedPreferencesName)
            } catch (resetException: Exception) {
                Log.e(TAG, "initialization after reset failed", resetException)
                throw resetException
            }
        }

    private fun initializeEncryptedSharedPreferencesManager(
        context: Context,
        sharedPreferencesName: String,
    ): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyGenParameterSpec(
                KeyGenParameterSpec.Builder(
                    MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            .build()

        return EncryptedSharedPreferences.create(
            context = context,
            fileName = sharedPreferencesName,
            masterKey = masterKey,
            prefKeyEncryptionScheme = EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            prefValueEncryptionScheme = EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun migrateToEncryptedPreferences(
        context: Context,
        sharedPreferencesName: String,
        target: SharedPreferences,
        deleteOnFailure: Boolean,
        options: Map<String, Any?>,
    ) {
        val source = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val sourceEntries = source.all
        if (sourceEntries.isEmpty()) return

        var successful = 0
        var failed = 0

        try {
            val cipher = StorageCipherFactory(source, options).getSavedStorageCipher(context)

            sourceEntries.forEach { (key, value) ->
                if (key.startsWith(preferencesKeyPrefix) && value is String) {
                    try {
                        target.edit().putString(key, decryptValue(value, cipher)).apply()
                        source.edit().remove(key).apply()
                        successful++
                    } catch (exception: Exception) {
                        Log.e(TAG, "Migration failed for one stored value.", exception)
                        failed++
                        if (deleteOnFailure) {
                            source.edit().remove(key).apply()
                        }
                    }
                }
            }

            if (successful > 0) Log.i(TAG, "Successfully migrated $successful keys.")
            if (failed > 0) Log.w(TAG, "Failed to migrate $failed keys.")
            if (failed == 0 || deleteOnFailure) {
                target.edit().putBoolean(PREF_KEY_MIGRATED, true).apply()
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Migration failed due to initialisation error.", exception)
            if (deleteOnFailure) {
                target.edit().putBoolean(PREF_KEY_MIGRATED, true).apply()
            }
        }
    }

    private fun decryptValue(value: String, cipher: StorageCipher): String {
        val data = Base64.decode(value, Base64.DEFAULT)
        return String(cipher.decrypt(data), StandardCharsets.UTF_8)
    }

    private fun Map<String, Any?>.stringOption(key: String, defaultValue: String): String =
        (this[key] as? String)?.takeIf { it.isNotEmpty() } ?: defaultValue

    private companion object {
        const val TAG = "FlutterSecureStorage"
        const val DEFAULT_PREF_NAME = "FlutterSecureStorage"
        const val DEFAULT_KEY_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg"
        const val PREF_OPTION_NAME = "sharedPreferencesName"
        const val PREF_OPTION_PREFIX = "preferencesKeyPrefix"
        const val PREF_OPTION_DELETE_ON_FAILURE = "resetOnError"
        const val PREF_KEY_MIGRATED = "preferencesMigrated"
    }
}
