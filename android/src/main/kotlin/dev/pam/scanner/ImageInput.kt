package dev.pam.scanner

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal object ImageInput {
    fun copyBounded(input: InputStream, output: OutputStream, limitBytes: Long = 32L * 1024 * 1024) {
        require(limitBytes > 0)
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return
            if (count == 0 || count.toLong() > limitBytes - total) throw IOException("Image input exceeds its read limit")
            output.write(buffer, 0, count)
            total += count
        }
    }
}
