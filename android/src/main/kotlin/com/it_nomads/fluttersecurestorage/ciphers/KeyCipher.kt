package com.it_nomads.fluttersecurestorage.ciphers

import java.security.Key

interface KeyCipher {
    @Throws(Exception::class)
    fun wrap(key: Key): ByteArray

    @Throws(Exception::class)
    fun unwrap(wrappedKey: ByteArray, algorithm: String): Key
}
