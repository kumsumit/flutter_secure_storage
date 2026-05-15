package com.it_nomads.fluttersecurestorage.crypto

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.DoNotInline
import androidx.annotation.IntRange
import androidx.annotation.RequiresApi
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

class MasterKey internal constructor(
    private val keyAlias: String,
    private val keyGenParameterSpec: KeyGenParameterSpec?,
) {
    enum class KeyScheme {
        AES256_GCM,
    }

    fun isKeyStoreBacked(): Boolean =
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(keyAlias)
        } catch (_: Exception) {
            false
        }

    fun isUserAuthenticationRequired(): Boolean =
        keyGenParameterSpec?.let(Api23Impl::isUserAuthenticationRequired) ?: false

    @SuppressLint("MethodNameUnits")
    fun getUserAuthenticationValidityDurationSeconds(): Int =
        keyGenParameterSpec?.let(Api23Impl::getUserAuthenticationValidityDurationSeconds) ?: 0

    fun isStrongBoxBacked(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            keyGenParameterSpec?.let(Api28Impl::isStrongBoxBacked) == true

    override fun toString(): String =
        "MasterKey{keyAlias=$keyAlias, isKeyStoreBacked=${isKeyStoreBacked()}}"

    internal fun getKeyAlias(): String = keyAlias

    class Builder @JvmOverloads constructor(
        context: Context,
        private val keyAlias: String = DEFAULT_MASTER_KEY_ALIAS,
    ) {
        private val context = context.applicationContext
        private var keyGenParameterSpec: KeyGenParameterSpec? = null
        private var keyScheme: KeyScheme? = null
        private var authenticationRequired = false
        private var userAuthenticationValidityDurationSeconds = defaultAuthenticationValidityDurationSeconds
        private var requestStrongBoxBacked = false

        fun setKeyScheme(keyScheme: KeyScheme): Builder = apply {
            require(this.keyGenParameterSpec == null) { "KeyScheme set after setting a KeyGenParamSpec" }
            require(keyScheme == KeyScheme.AES256_GCM) { "Unsupported scheme: $keyScheme" }
            this.keyScheme = keyScheme
        }

        fun setUserAuthenticationRequired(authenticationRequired: Boolean): Builder =
            setUserAuthenticationRequired(authenticationRequired, defaultAuthenticationValidityDurationSeconds)

        fun setUserAuthenticationRequired(
            authenticationRequired: Boolean,
            @IntRange(from = 1) userAuthenticationValidityDurationSeconds: Int,
        ): Builder = apply {
            this.authenticationRequired = authenticationRequired
            this.userAuthenticationValidityDurationSeconds = userAuthenticationValidityDurationSeconds
        }

        fun setRequestStrongBoxBacked(requestStrongBoxBacked: Boolean): Builder = apply {
            this.requestStrongBoxBacked = requestStrongBoxBacked
        }

        fun setKeyGenParameterSpec(keyGenParameterSpec: KeyGenParameterSpec): Builder = apply {
            require(keyScheme == null) { "KeyGenParamSpec set after setting a KeyScheme" }
            val specAlias = keyGenParameterSpec.keystoreAlias
            require(keyAlias == specAlias) {
                "KeyGenParamSpec's key alias does not match provided alias ($keyAlias vs $specAlias"
            }
            this.keyGenParameterSpec = keyGenParameterSpec
        }

        @Throws(GeneralSecurityException::class, IOException::class)
        fun build(): MasterKey {
            if (keyScheme == null && keyGenParameterSpec == null) {
                throw IllegalArgumentException("build() called before setKeyGenParameterSpec or setKeyScheme.")
            }

            if (keyScheme == KeyScheme.AES256_GCM) {
                val keyGenBuilder = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(DEFAULT_AES_GCM_MASTER_KEY_SIZE)
                    .setRandomizedEncryptionRequired(true)

                if (authenticationRequired) {
                    keyGenBuilder.setUserAuthenticationRequired(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Api30Impl.setUserAuthenticationParameters(
                            keyGenBuilder,
                            userAuthenticationValidityDurationSeconds,
                            KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        keyGenBuilder.setUserAuthenticationValidityDurationSeconds(
                            userAuthenticationValidityDurationSeconds,
                        )
                    }
                }

                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    requestStrongBoxBacked &&
                    context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
                ) {
                    Api28BuilderImpl.setIsStrongBoxBacked(keyGenBuilder)
                }

                keyGenParameterSpec = keyGenBuilder.build()
            }

            val spec = requireNotNull(keyGenParameterSpec) {
                "KeyGenParameterSpec was null after build() check"
            }
            return MasterKey(MasterKeys.getOrCreate(spec), spec)
        }
    }

    private object Api23Impl {
        @DoNotInline
        fun isUserAuthenticationRequired(keyGenParameterSpec: KeyGenParameterSpec): Boolean =
            keyGenParameterSpec.isUserAuthenticationRequired

        @Suppress("DEPRECATION")
        @DoNotInline
        fun getUserAuthenticationValidityDurationSeconds(keyGenParameterSpec: KeyGenParameterSpec): Int =
            keyGenParameterSpec.userAuthenticationValidityDurationSeconds
    }

    @RequiresApi(28)
    private object Api28Impl {
        @DoNotInline
        fun isStrongBoxBacked(keyGenParameterSpec: KeyGenParameterSpec): Boolean =
            keyGenParameterSpec.isStrongBoxBacked
    }

    @RequiresApi(28)
    private object Api28BuilderImpl {
        @DoNotInline
        fun setIsStrongBoxBacked(builder: KeyGenParameterSpec.Builder) {
            builder.setIsStrongBoxBacked(true)
        }
    }

    @RequiresApi(30)
    private object Api30Impl {
        @DoNotInline
        fun setUserAuthenticationParameters(
            builder: KeyGenParameterSpec.Builder,
            timeout: Int,
            type: Int,
        ) {
            builder.setUserAuthenticationParameters(timeout, type)
        }
    }

    companion object {
        const val KEYSTORE_PATH_URI = "android-keystore://"
        const val DEFAULT_MASTER_KEY_ALIAS = "_androidx_security_master_key_"
        const val DEFAULT_AES_GCM_MASTER_KEY_SIZE = 256
        private const val DEFAULT_AUTHENTICATION_VALIDITY_DURATION_SECONDS = 5 * 60

        @SuppressLint("MethodNameUnits")
        @JvmStatic
        val defaultAuthenticationValidityDurationSeconds: Int
            get() = DEFAULT_AUTHENTICATION_VALIDITY_DURATION_SECONDS
    }
}
