package com.it_nomads.fluttersecurestorage.ciphers

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

private enum class KeyCipherAlgorithm(
    val minVersionCode: Int,
    val create: (Context) -> KeyCipher,
) {
    RSA_ECB_PKCS1Padding(1, ::RSACipher18Implementation),
    RSA_ECB_OAEPwithSHA_256andMGF1Padding(Build.VERSION_CODES.M, ::RSACipherOAEPImplementation),
}

private enum class StorageCipherAlgorithm(
    val minVersionCode: Int,
    val create: (Context, KeyCipher) -> StorageCipher,
) {
    AES_CBC_PKCS7Padding(1, ::StorageCipher18Implementation),
    AES_GCM_NoPadding(Build.VERSION_CODES.M, ::StorageCipherGCMImplementation),
}

class StorageCipherFactory(
    source: SharedPreferences,
    options: Map<String, Any?>,
) {
    private val savedKeyAlgorithm = source.getString(
        ELEMENT_PREFERENCES_ALGORITHM_KEY,
        DEFAULT_KEY_ALGORITHM.name,
    ).toEnumOrDefault(DEFAULT_KEY_ALGORITHM)

    private val savedStorageAlgorithm = source.getString(
        ELEMENT_PREFERENCES_ALGORITHM_STORAGE,
        DEFAULT_STORAGE_ALGORITHM.name,
    ).toEnumOrDefault(DEFAULT_STORAGE_ALGORITHM)

    private val currentKeyAlgorithm = options.algorithmOption(
        key = "keyCipherAlgorithm",
        defaultValue = DEFAULT_KEY_ALGORITHM,
    )

    private val currentStorageAlgorithm = options.algorithmOption(
        key = "storageCipherAlgorithm",
        defaultValue = DEFAULT_STORAGE_ALGORITHM,
    )

    fun requiresReEncryption(): Boolean =
        savedKeyAlgorithm != currentKeyAlgorithm || savedStorageAlgorithm != currentStorageAlgorithm

    @Throws(Exception::class)
    fun getSavedStorageCipher(context: Context): StorageCipher =
        savedStorageAlgorithm.create(context, savedKeyAlgorithm.create(context))

    @Throws(Exception::class)
    fun getCurrentStorageCipher(context: Context): StorageCipher =
        currentStorageAlgorithm.create(context, currentKeyAlgorithm.create(context))

    fun storeCurrentAlgorithms(editor: SharedPreferences.Editor) {
        editor.putString(ELEMENT_PREFERENCES_ALGORITHM_KEY, currentKeyAlgorithm.name)
        editor.putString(ELEMENT_PREFERENCES_ALGORITHM_STORAGE, currentStorageAlgorithm.name)
    }

    fun removeCurrentAlgorithms(editor: SharedPreferences.Editor) {
        editor.remove(ELEMENT_PREFERENCES_ALGORITHM_KEY)
        editor.remove(ELEMENT_PREFERENCES_ALGORITHM_STORAGE)
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(defaultValue: T): T =
        enumValues<T>().firstOrNull { it.name == this } ?: defaultValue

    private fun Map<String, Any?>.algorithmOption(
        key: String,
        defaultValue: KeyCipherAlgorithm,
    ): KeyCipherAlgorithm =
        (this[key]?.toString()).toEnumOrDefault(defaultValue).takeIfSupported(defaultValue)

    private fun Map<String, Any?>.algorithmOption(
        key: String,
        defaultValue: StorageCipherAlgorithm,
    ): StorageCipherAlgorithm =
        (this[key]?.toString()).toEnumOrDefault(defaultValue).takeIfSupported(defaultValue)

    private fun KeyCipherAlgorithm.takeIfSupported(defaultValue: KeyCipherAlgorithm): KeyCipherAlgorithm =
        if (minVersionCode <= Build.VERSION.SDK_INT) this else defaultValue

    private fun StorageCipherAlgorithm.takeIfSupported(
        defaultValue: StorageCipherAlgorithm,
    ): StorageCipherAlgorithm =
        if (minVersionCode <= Build.VERSION.SDK_INT) this else defaultValue

    private companion object {
        const val ELEMENT_PREFERENCES_ALGORITHM_PREFIX = "FlutterSecureSAlgorithm"
        const val ELEMENT_PREFERENCES_ALGORITHM_KEY = ELEMENT_PREFERENCES_ALGORITHM_PREFIX + "Key"
        const val ELEMENT_PREFERENCES_ALGORITHM_STORAGE = ELEMENT_PREFERENCES_ALGORITHM_PREFIX + "Storage"
        val DEFAULT_KEY_ALGORITHM = KeyCipherAlgorithm.RSA_ECB_PKCS1Padding
        val DEFAULT_STORAGE_ALGORITHM = StorageCipherAlgorithm.AES_CBC_PKCS7Padding
    }
}
