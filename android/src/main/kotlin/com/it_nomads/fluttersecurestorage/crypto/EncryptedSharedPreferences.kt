package com.it_nomads.fluttersecurestorage.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Pair
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.subtle.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.GeneralSecurityException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class EncryptedSharedPreferences private constructor(
    private val fileName: String,
    private val masterKeyAlias: String,
    private val sharedPreferences: SharedPreferences,
    private val valueAead: Aead,
    private val keyDeterministicAead: DeterministicAead,
) : SharedPreferences {
    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> =
        sharedPreferences.all.mapNotNull { (key, _) ->
            if (isReservedKey(key)) {
                null
            } else {
                val decryptedKey = decryptKey(key) ?: return@mapNotNull null
                decryptedKey to getDecryptedObject(decryptedKey)
            }
        }.toMap().toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        getDecryptedObject(key) as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val value = getDecryptedObject(key) as? Set<String> ?: emptySet()
        return value.takeIf { it.isNotEmpty() }?.toMutableSet() ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int =
        getDecryptedObject(key) as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        getDecryptedObject(key) as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        getDecryptedObject(key) as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        getDecryptedObject(key) as? Boolean ?: defValue

    override fun contains(key: String?): Boolean {
        requireNotReservedKey(key)
        return sharedPreferences.contains(encryptKey(key))
    }

    override fun edit(): SharedPreferences.Editor = Editor(this, sharedPreferences.edit())

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners.remove(listener)
    }

    private fun getDecryptedObject(key: String?): Any? {
        requireNotReservedKey(key)
        val normalizedKey = key ?: NULL_VALUE

        try {
            val encryptedKey = encryptKey(normalizedKey)
            val encryptedValue = sharedPreferences.getString(encryptedKey, null) ?: return null
            val cipherText = Base64.decode(encryptedValue, Base64.DEFAULT)
            val value = valueAead.decrypt(cipherText, encryptedKey.toByteArray(UTF_8))
            val buffer = ByteBuffer.wrap(value)
            val typeId = buffer.int

            return when (val type = EncryptedType.fromId(typeId)) {
                EncryptedType.STRING -> {
                    val stringLength = buffer.int
                    val stringSlice = buffer.slice().apply { limit(stringLength) }
                    UTF_8.decode(stringSlice).toString().takeUnless { it == NULL_VALUE }
                }
                EncryptedType.INT -> buffer.int
                EncryptedType.LONG -> buffer.long
                EncryptedType.FLOAT -> buffer.float
                EncryptedType.BOOLEAN -> buffer.get() != 0.toByte()
                EncryptedType.STRING_SET -> decryptStringSet(buffer)
                null -> throw SecurityException("Unknown type ID for encrypted pref value: $typeId")
            }
        } catch (exception: GeneralSecurityException) {
            throw SecurityException("Could not decrypt value. ${exception.message}", exception)
        }
    }

    private fun decryptStringSet(buffer: ByteBuffer): Set<String>? {
        val stringSet = linkedSetOf<String>()
        while (buffer.hasRemaining()) {
            val subStringLength = buffer.int
            val subStringSlice = buffer.slice().apply { limit(subStringLength) }
            buffer.position(buffer.position() + subStringLength)
            stringSet.add(UTF_8.decode(subStringSlice).toString())
        }
        return stringSet.takeUnless { it.size == 1 && it.first() == NULL_VALUE }
    }

    internal fun encryptKey(key: String?): String {
        val normalizedKey = key ?: NULL_VALUE
        return try {
            val encryptedKeyBytes = keyDeterministicAead.encryptDeterministically(
                normalizedKey.toByteArray(UTF_8),
                fileName.toByteArray(UTF_8),
            )
            Base64.encode(encryptedKeyBytes)
        } catch (exception: GeneralSecurityException) {
            throw SecurityException("Could not encrypt key. ${exception.message}", exception)
        }
    }

    internal fun decryptKey(encryptedKey: String): String? =
        try {
            val clearText = keyDeterministicAead.decryptDeterministically(
                Base64.decode(encryptedKey, Base64.DEFAULT),
                fileName.toByteArray(UTF_8),
            )
            String(clearText, UTF_8).takeUnless { it == NULL_VALUE }
        } catch (exception: GeneralSecurityException) {
            throw SecurityException("Could not decrypt key. ${exception.message}", exception)
        }

    internal fun isReservedKey(key: String?): Boolean =
        key == KEY_KEYSET_ALIAS || key == VALUE_KEYSET_ALIAS

    internal fun encryptKeyValuePair(key: String?, value: ByteArray): Pair<String, String> {
        val encryptedKey = encryptKey(key)
        val cipherText = valueAead.encrypt(value, encryptedKey.toByteArray(UTF_8))
        return Pair(encryptedKey, Base64.encode(cipherText))
    }

    private fun requireNotReservedKey(key: String?) {
        if (isReservedKey(key)) {
            throw SecurityException("$key is a reserved key for the encryption keyset.")
        }
    }

    private class Editor(
        private val encryptedSharedPreferences: EncryptedSharedPreferences,
        private val editor: SharedPreferences.Editor,
    ) : SharedPreferences.Editor {
        private val keysChanged = CopyOnWriteArrayList<String?>()
        private val clearRequested = AtomicBoolean(false)

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            val normalizedValue = value ?: NULL_VALUE
            val stringBytes = normalizedValue.toByteArray(UTF_8)
            val buffer = ByteBuffer.allocate(INT_BYTES + INT_BYTES + stringBytes.size)
                .putInt(EncryptedType.STRING.id)
                .putInt(stringBytes.size)
                .put(stringBytes)
            putEncryptedObject(key, buffer.array())
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            val normalizedValues = values ?: mutableSetOf(NULL_VALUE)
            val byteValues = normalizedValues.map { it.toByteArray(UTF_8) }
            val totalBytes = byteValues.sumOf { it.size } + (normalizedValues.size + 1) * INT_BYTES
            val buffer = ByteBuffer.allocate(totalBytes).putInt(EncryptedType.STRING_SET.id)
            byteValues.forEach {
                buffer.putInt(it.size)
                buffer.put(it)
            }
            putEncryptedObject(key, buffer.array())
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            putEncryptedObject(
                key,
                ByteBuffer.allocate(INT_BYTES + INT_BYTES)
                    .putInt(EncryptedType.INT.id)
                    .putInt(value)
                    .array(),
            )
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            putEncryptedObject(
                key,
                ByteBuffer.allocate(INT_BYTES + LONG_BYTES)
                    .putInt(EncryptedType.LONG.id)
                    .putLong(value)
                    .array(),
            )
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            putEncryptedObject(
                key,
                ByteBuffer.allocate(INT_BYTES + FLOAT_BYTES)
                    .putInt(EncryptedType.FLOAT.id)
                    .putFloat(value)
                    .array(),
            )
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            putEncryptedObject(
                key,
                ByteBuffer.allocate(INT_BYTES + BYTE_BYTES)
                    .putInt(EncryptedType.BOOLEAN.id)
                    .put((if (value) 1 else 0).toByte())
                    .array(),
            )
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (encryptedSharedPreferences.isReservedKey(key)) {
                throw SecurityException("$key is a reserved key for the encryption keyset.")
            }
            editor.remove(encryptedSharedPreferences.encryptKey(key))
            keysChanged.add(key)
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested.set(true)
        }

        override fun commit(): Boolean {
            clearKeysIfNeeded()
            return try {
                editor.commit()
            } finally {
                notifyListeners()
                keysChanged.clear()
            }
        }

        override fun apply() {
            clearKeysIfNeeded()
            editor.apply()
            notifyListeners()
            keysChanged.clear()
        }

        private fun clearKeysIfNeeded() {
            if (clearRequested.getAndSet(false)) {
                encryptedSharedPreferences.all.keys
                    .filterNot { keysChanged.contains(it) || encryptedSharedPreferences.isReservedKey(it) }
                    .forEach { editor.remove(encryptedSharedPreferences.encryptKey(it)) }
            }
        }

        private fun putEncryptedObject(key: String?, value: ByteArray) {
            if (encryptedSharedPreferences.isReservedKey(key)) {
                throw SecurityException("$key is a reserved key for the encryption keyset.")
            }

            keysChanged.add(key)
            try {
                val encryptedPair = encryptedSharedPreferences.encryptKeyValuePair(key, value)
                editor.putString(encryptedPair.first, encryptedPair.second)
            } catch (exception: GeneralSecurityException) {
                throw SecurityException("Could not encrypt data: ${exception.message}", exception)
            }
        }

        private fun notifyListeners() {
            encryptedSharedPreferences.listeners.forEach { listener ->
                keysChanged.forEach { key ->
                    listener.onSharedPreferenceChanged(encryptedSharedPreferences, key)
                }
            }
        }
    }

    private enum class EncryptedType(val id: Int) {
        STRING(0),
        STRING_SET(1),
        INT(2),
        LONG(3),
        FLOAT(4),
        BOOLEAN(5);

        companion object {
            fun fromId(id: Int): EncryptedType? = entries.firstOrNull { it.id == id }
        }
    }

    enum class PrefKeyEncryptionScheme(private val deterministicAeadKeyTemplateName: String) {
        AES256_SIV("AES256_SIV");

        @Throws(GeneralSecurityException::class)
        fun getKeyTemplate(): KeyTemplate = KeyTemplates.get(deterministicAeadKeyTemplateName)
    }

    enum class PrefValueEncryptionScheme(private val aeadKeyTemplateName: String) {
        AES256_GCM("AES256_GCM");

        @Throws(GeneralSecurityException::class)
        fun getKeyTemplate(): KeyTemplate = KeyTemplates.get(aeadKeyTemplateName)
    }

    companion object {
        private const val KEY_KEYSET_ALIAS = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
        private const val VALUE_KEYSET_ALIAS = "__androidx_security_crypto_encrypted_prefs_value_keyset__"
        private const val NULL_VALUE = "__NULL__"
        private const val INT_BYTES = 4
        private const val LONG_BYTES = 8
        private const val FLOAT_BYTES = 4
        private const val BYTE_BYTES = 1

        @JvmStatic
        @Throws(GeneralSecurityException::class, java.io.IOException::class)
        fun create(
            context: Context,
            fileName: String,
            masterKey: MasterKey,
            prefKeyEncryptionScheme: PrefKeyEncryptionScheme,
            prefValueEncryptionScheme: PrefValueEncryptionScheme,
        ): SharedPreferences =
            create(
                fileName = fileName,
                masterKeyAlias = masterKey.getKeyAlias(),
                context = context,
                prefKeyEncryptionScheme = prefKeyEncryptionScheme,
                prefValueEncryptionScheme = prefValueEncryptionScheme,
            )

        @Throws(GeneralSecurityException::class, java.io.IOException::class)
        private fun create(
            fileName: String,
            masterKeyAlias: String,
            context: Context,
            prefKeyEncryptionScheme: PrefKeyEncryptionScheme,
            prefValueEncryptionScheme: PrefValueEncryptionScheme,
        ): SharedPreferences {
            DeterministicAeadConfig.register()
            AeadConfig.register()

            val applicationContext = context.applicationContext
            val daeadKeysetHandle: KeysetHandle = AndroidKeysetManager.Builder()
                .withKeyTemplate(prefKeyEncryptionScheme.getKeyTemplate())
                .withSharedPref(applicationContext, KEY_KEYSET_ALIAS, fileName)
                .withMasterKeyUri(MasterKey.KEYSTORE_PATH_URI + masterKeyAlias)
                .build()
                .keysetHandle

            val aeadKeysetHandle: KeysetHandle = AndroidKeysetManager.Builder()
                .withKeyTemplate(prefValueEncryptionScheme.getKeyTemplate())
                .withSharedPref(applicationContext, VALUE_KEYSET_ALIAS, fileName)
                .withMasterKeyUri(MasterKey.KEYSTORE_PATH_URI + masterKeyAlias)
                .build()
                .keysetHandle

            val daead = daeadKeysetHandle.getPrimitive(
                RegistryConfiguration.get(),
                DeterministicAead::class.java,
            )
            val aead = aeadKeysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)

            return EncryptedSharedPreferences(
                fileName = fileName,
                masterKeyAlias = masterKeyAlias,
                sharedPreferences = applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE),
                valueAead = aead,
                keyDeterministicAead = daead,
            )
        }
    }
}
