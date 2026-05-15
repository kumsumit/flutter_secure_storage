package com.it_nomads.fluttersecurestorage.ciphers

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.MGF1ParameterSpec
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.security.auth.x500.X500Principal

internal class RSACipherOAEPImplementation(context: Context) : RSACipher18Implementation(context) {
    override fun createKeyAlias(): String =
        "${context.packageName}.FlutterSecureStoragePluginKeyOAEP"

    override fun makeAlgorithmParameterSpec(
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
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setCertificateSerialNumber(BigInteger.valueOf(1))
            .setCertificateNotBefore(start.time)
            .setCertificateNotAfter(end.time)
            .build()

    override fun getRSACipher(): Cipher =
        Cipher.getInstance("RSA/ECB/OAEPPadding", "AndroidKeyStoreBCWorkaround")

    override val algorithmParameterSpec: AlgorithmParameterSpec
        get() = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,
            PSource.PSpecified.DEFAULT,
        )
}
