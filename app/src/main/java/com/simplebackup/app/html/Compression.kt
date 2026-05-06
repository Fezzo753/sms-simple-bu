package com.simplebackup.app.html

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

object Compression {
    fun gzipBase64(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream(bytes.size / 4)
        GZIPOutputStream(out).use { it.write(bytes) }
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }
}
