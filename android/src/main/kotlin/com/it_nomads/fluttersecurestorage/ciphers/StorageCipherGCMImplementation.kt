package com.it_nomads.fluttersecurestorage.ciphers

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

internal class StorageCipherGCMImplementation(
    context: Context,
    keyCipher: KeyCipher,
) : StorageCipher18Implementation(context, keyCipher) {
    override val aesPreferencesKey: String
        get() = "VGhpcyBpcyB0aGUga2V5IGZvcihBIHNlY3XyZZBzdG9yYWdlIEFFUyBLZXkK"

    override fun getCipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    override val ivSize: Int
        get() = 12

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun parameterSpec(iv: ByteArray): AlgorithmParameterSpec =
        GCMParameterSpec(AUTHENTICATION_TAG_SIZE, iv)

    private companion object {
        const val AUTHENTICATION_TAG_SIZE = 128
    }
}
