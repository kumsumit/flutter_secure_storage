package com.it_nomads.fluttersecurestorage

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import java.util.concurrent.atomic.AtomicBoolean

internal class BiometricAuthenticator(
    context: Context,
) {
    private val context = context.applicationContext

    fun isDeviceSecure(): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun isAvailable(config: AndroidStorageConfig): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !isDeviceSecure()) {
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Api30.isAvailable(context, config.strongBiometricOnly)
        } else {
            true
        }
    }

    fun authenticate(
        config: AndroidStorageConfig,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onError(
                IllegalStateException(
                    "Biometric authentication requires Android 9 (API 28) or newer.",
                ),
            )
            return
        }
        if (!isDeviceSecure()) {
            onError(
                IllegalStateException(
                    "Biometric authentication requires an enrolled biometric or device credential.",
                ),
            )
            return
        }

        Api28.authenticate(context, config, onSuccess, onError)
    }

    @RequiresApi(28)
    private object Api28 {
        @DoNotInline
        fun authenticate(
            context: Context,
            config: AndroidStorageConfig,
            onSuccess: () -> Unit,
            onError: (Exception) -> Unit,
        ) {
            val executor = context.mainExecutor
            val cancellationSignal = CancellationSignal()
            val completed = AtomicBoolean(false)
            val succeed = {
                if (completed.compareAndSet(false, true)) {
                    onSuccess()
                }
            }
            val fail = { exception: Exception ->
                if (completed.compareAndSet(false, true)) {
                    onError(exception)
                }
            }
            val builder = BiometricPrompt.Builder(context)
                .setTitle(config.biometricPromptTitle)

            if (config.biometricPromptSubtitle.isNotEmpty()) {
                builder.setSubtitle(config.biometricPromptSubtitle)
            }

            val deviceCredentialEnabled =
                !config.strongBiometricOnly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Api30.configureAuthenticators(builder, config.strongBiometricOnly)
            } else if (deviceCredentialEnabled) {
                Api29.enableDeviceCredential(builder)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Api29.setConfirmationRequired(
                    builder,
                    config.requireBiometricConfirmation,
                )
            }

            if (!deviceCredentialEnabled) {
                builder.setNegativeButton(
                    config.biometricPromptNegativeButton,
                    executor,
                ) { _, _ ->
                    cancellationSignal.cancel()
                    fail(AuthenticationCancelledException())
                }
            }

            builder.build().authenticate(
                cancellationSignal,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult,
                    ) {
                        succeed()
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        fail(
                            BiometricAuthenticationException(
                                errorCode,
                                errString.toString(),
                            ),
                        )
                    }
                },
            )
        }
    }

    @RequiresApi(29)
    private object Api29 {
        @Suppress("DEPRECATION")
        @DoNotInline
        fun enableDeviceCredential(builder: BiometricPrompt.Builder) {
            builder.setDeviceCredentialAllowed(true)
        }

        @DoNotInline
        fun setConfirmationRequired(
            builder: BiometricPrompt.Builder,
            required: Boolean,
        ) {
            builder.setConfirmationRequired(required)
        }
    }

    @RequiresApi(30)
    private object Api30 {
        @DoNotInline
        fun configureAuthenticators(
            builder: BiometricPrompt.Builder,
            strongBiometricOnly: Boolean,
        ) {
            val authenticators = if (strongBiometricOnly) {
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            } else {
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            }
            builder.setAllowedAuthenticators(authenticators)
        }

        @DoNotInline
        fun isAvailable(context: Context, strongBiometricOnly: Boolean): Boolean {
            val authenticators = if (strongBiometricOnly) {
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            } else {
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            }
            return context.getSystemService(BiometricManager::class.java)
                ?.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        }
    }
}

internal class BiometricAuthenticationException(
    val errorCode: Int,
    message: String,
) : Exception(message)

internal class AuthenticationCancelledException :
    Exception("Biometric authentication was cancelled.")
