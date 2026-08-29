package dev.stan.yotsuba.core.dedup

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64

/** MD5 in 4chan's format: base64 of the 16 raw digest bytes. */
object Md5 {
    private const val BUFFER = 64 * 1024

    fun of(file: File): String = file.inputStream().use { of(it) }

    fun of(input: InputStream): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(BUFFER)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return Base64.getEncoder().encodeToString(digest.digest())
    }
}
