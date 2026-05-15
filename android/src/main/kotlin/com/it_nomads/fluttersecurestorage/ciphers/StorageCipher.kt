package com.it_nomads.fluttersecurestorage.ciphers

interface StorageCipher {
    @Throws(Exception::class)
    fun encrypt(input: ByteArray): ByteArray

    @Throws(Exception::class)
    fun decrypt(input: ByteArray): ByteArray
}
