package com.it_nomads.fluttersecurestorage

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.it_nomads.fluttersecurestorage.crypto.EncryptedSharedPreferences
import com.it_nomads.fluttersecurestorage.crypto.MasterKey
import com.it_nomads.fluttersecurestorage.crypto.MasterKeys

class FlutterSecureStorage(
    context: Context,
    options: Map<String, Any?>,
) {
    private val config = AndroidStorageConfig.from(options)
    private val preferencesKeyPrefix = config.preferencesKeyPrefix
    private val encryptedPreferences: SharedPreferences

    init {
        encryptedPreferences = getEncryptedSharedPreferences(
            context = context.applicationContext,
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
            val prefix = "${preferencesKeyPrefix}_"
            if (key.startsWith(prefix) && value is String) {
                key.removePrefix(prefix) to value
            } else {
                null
            }
        }.toMap()

    private fun addPrefixToKey(key: String?): String = "${preferencesKeyPrefix}_$key"

    private fun getEncryptedSharedPreferences(
        context: Context,
    ): SharedPreferences =
        try {
            initializeEncryptedSharedPreferencesManager(context)
        } catch (exception: Exception) {
            if (!config.resetOnError) {
                Log.w(TAG, "initialization failed, resetOnError false, so throwing exception.", exception)
                throw exception
            }

            Log.w(TAG, "initialization failed, resetting storage", exception)
            MasterKeys.remove(config.keystoreAlias)
            check(
                context.getSharedPreferences(
                    config.sharedPreferencesName,
                    Context.MODE_PRIVATE,
                ).edit().clear().commit(),
            ) {
                "Failed to reset encrypted preferences."
            }

            try {
                initializeEncryptedSharedPreferencesManager(context)
            } catch (resetException: Exception) {
                Log.e(TAG, "initialization after reset failed", resetException)
                throw resetException
            }
        }

    private fun initializeEncryptedSharedPreferencesManager(
        context: Context,
    ): SharedPreferences {
        val masterKey = MasterKey.Builder(context, config.keystoreAlias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .buildWithBestAvailableSecurity(context)

        return EncryptedSharedPreferences.create(
            context = context,
            fileName = config.sharedPreferencesName,
            masterKey = masterKey,
            prefKeyEncryptionScheme = EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            prefValueEncryptionScheme = EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun MasterKey.Builder.buildWithBestAvailableSecurity(
        context: Context,
    ): MasterKey {
        // Bind the key to recent user authentication (biometric / device
        // credential) at the hardware level when requested.
        applyUserAuthentication()

        val securityLevel = config.storageSecurityLevel
        val shouldRequestStrongBox = securityLevel != STORAGE_SECURITY_LEVEL_ANDROID_KEYSTORE &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        if (!shouldRequestStrongBox) return build()

        return try {
            setRequestStrongBoxBacked(true).build()
        } catch (exception: Exception) {
            if (securityLevel == STORAGE_SECURITY_LEVEL_STRONG_BOX_ONLY) {
                Log.e(TAG, "StrongBox-backed master key was required but could not be created.", exception)
                throw exception
            }

            Log.w(TAG, "StrongBox-backed master key unavailable; falling back to Android Keystore.", exception)
            MasterKey.Builder(context, config.keystoreAlias)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .applyUserAuthentication()
                .build()
        }
    }

    private fun MasterKey.Builder.applyUserAuthentication(): MasterKey.Builder = apply {
        if (!config.userAuthenticationRequired) return@apply
        setUserAuthenticationRequired(
            true,
            config.userAuthenticationValidityDurationSeconds,
            config.strongBiometricOnly,
        )
    }

    private companion object {
        const val TAG = "FlutterSecureStorage"
        const val STORAGE_SECURITY_LEVEL_STRONG_BOX_ONLY = "strongBoxOnly"
        const val STORAGE_SECURITY_LEVEL_ANDROID_KEYSTORE = "androidKeystore"
    }
}
