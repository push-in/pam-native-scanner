package dev.pam.scanner

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.util.UUID

internal object QrImageGenerator {
    fun generate(cache: File, text: String, pixels: Int): String {
        require(text.isNotEmpty() && text.toByteArray(Charsets.UTF_8).size <= 2048 && '\u0000' !in text)
        require(pixels in 128..1024)
        val root = File(cache, "pam-qr-generated").apply { mkdirs() }
        // Bound leftovers from interrupted processes; active owners release promptly.
        root.listFiles()?.filter { it.name.matches(Regex("qr-[a-f0-9-]+\\.png")) }
            ?.sortedByDescending { it.lastModified() }?.forEachIndexed { index, file ->
                if (index >= 15 || System.currentTimeMillis() - file.lastModified() > 86_400_000) file.delete()
            }
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, pixels, pixels, mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4,
        ))
        require(matrix.width == pixels && matrix.height == pixels) { "Image too small for payload" }
        val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
        val row = IntArray(pixels)
        val file = File(root, "qr-${UUID.randomUUID()}.png")
        try {
            for (y in 0 until pixels) {
                for (x in 0 until pixels) row[x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                bitmap.setPixels(row, 0, pixels, 0, y, pixels, 1)
            }
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return Uri.fromFile(file).toString()
        } catch (error: Exception) {
            file.delete()
            throw error
        } finally { bitmap.recycle() }
    }

    fun release(cache: File, source: String) {
        val uri = Uri.parse(source)
        require(uri.scheme == "file" && uri.authority.isNullOrEmpty() && uri.query == null && uri.fragment == null)
        val root = File(cache, "pam-qr-generated").canonicalFile
        val file = File(requireNotNull(uri.path)).canonicalFile
        require(file.parentFile == root && file.name.matches(Regex("qr-[a-f0-9-]+\\.png")))
        file.delete()
    }
}
