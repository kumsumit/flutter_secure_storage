package com.it_nomads.fluttersecurestorage

import com.it_nomads.fluttersecurestorage.crypto.MasterKey

internal data class AndroidStorageConfig(
    val sharedPreferencesName: String,
    val preferencesKeyPrefix: String,
    val keystoreAlias: String,
    val resetOnError: Boolean,
    val enforceBiometrics: Boolean,
    val strongBiometricOnly: Boolean,
    val requireBiometricConfirmation: Boolean,
    val biometricPromptTitle: String,
    val biometricPromptSubtitle: String,
    val biometricPromptNegativeButton: String,
    val storageSecurityLevel: String,
    val userAuthenticationRequired: Boolean,
    val userAuthenticationValidityDurationSeconds: Int,
) {
    companion object {
        private const val DEFAULT_PREF_NAME = "FlutterSecureStorage"
        private const val DEFAULT_KEY_PREFIX =
            "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg"

        fun from(options: Map<String, Any?>): AndroidStorageConfig {
            val storageNamespace = options.nonEmptyString("storageNamespace")
            val preferencesName = storageNamespace ?: DEFAULT_PREF_NAME
            val alias = storageNamespace
                ?.let { "${MasterKey.DEFAULT_MASTER_KEY_ALIAS}.$it" }
                ?: MasterKey.DEFAULT_MASTER_KEY_ALIAS

            val biometricType = options.nonEmptyString("biometricType")
                ?: "biometricOrDeviceCredential"
            require(
                biometricType == "strongBiometricOnly" ||
                    biometricType == "biometricOrDeviceCredential",
            ) {
                "Unknown biometricType '$biometricType'."
            }

            val enforceBiometrics = options.booleanOption("enforceBiometrics", false)

            return AndroidStorageConfig(
                sharedPreferencesName = preferencesName,
                preferencesKeyPrefix =
                    options.nonEmptyString("preferencesKeyPrefix") ?: DEFAULT_KEY_PREFIX,
                keystoreAlias = alias,
                resetOnError = options.booleanOption("resetOnError", true),
                enforceBiometrics = enforceBiometrics,
                strongBiometricOnly = biometricType == "strongBiometricOnly",
                requireBiometricConfirmation =
                    options.booleanOption("requireBiometricConfirmation", true),
                biometricPromptTitle =
                    options.nonEmptyString("biometricPromptTitle")
                        ?: "Authenticate to access secure storage",
                biometricPromptSubtitle =
                    options.nonEmptyString("biometricPromptSubtitle")
                        ?: "Use biometrics or device credentials",
                biometricPromptNegativeButton =
                    options.nonEmptyString("biometricPromptNegativeButton") ?: "Cancel",
                storageSecurityLevel =
                    options.nonEmptyString("storageSecurityLevel") ?: "automatic",
                userAuthenticationRequired =
                    enforceBiometrics,
                userAuthenticationValidityDurationSeconds =
                    options.intOption("userAuthenticationValidityDurationSeconds", 300)
                        .coerceAtLeast(1),
            )
        }

        private fun Map<String, Any?>.nonEmptyString(key: String): String? =
            (this[key] as? String)?.takeIf(String::isNotEmpty)

        private fun Map<String, Any?>.booleanOption(key: String, defaultValue: Boolean): Boolean =
            when (val value = this[key]) {
                is Boolean -> value
                is String -> value.toBooleanStrictOrNull() ?: defaultValue
                else -> defaultValue
            }

        private fun Map<String, Any?>.intOption(key: String, defaultValue: Int): Int =
            when (val value = this[key]) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: defaultValue
                else -> defaultValue
            }
    }
}
