package dev.pam.scanner

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageInputTest {
    @Test fun copiesExactlyAtTheLimit() {
        val bytes = ByteArray(128000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        ImageInput.copyBounded(ByteArrayInputStream(bytes), output, bytes.size.toLong())
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test fun rejectsOversizedStreamsBeforeWritingPastTheLimit() {
        val output = ByteArrayOutputStream()
        try {
            ImageInput.copyBounded(ByteArrayInputStream(ByteArray(128001)), output, 128000)
            throw AssertionError("Oversized input accepted")
        } catch (_: IOException) {
            assertTrue(output.size() <= 128000)
        }
    }

    @Test(expected = IOException::class) fun rejectsStreamsThatMakeNoProgress() {
        val input = object : InputStream() {
            override fun read(): Int = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
        }
        ImageInput.copyBounded(input, ByteArrayOutputStream())
    }
}
