package dev.pam.scanner

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dev.pam.nativeapp.modules.ModuleCompletion
import dev.pam.nativeapp.modules.ModuleResultStatus
import dev.pam.nativeapp.modules.NativeModule
import dev.pam.nativeapp.protocol.WireMap
import dev.pam.nativeapp.protocol.WireValue
import org.json.JSONArray
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class QrImageModule(context: Context) : NativeModule {
    private val context = context.applicationContext
    private val executor = ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, LinkedBlockingQueue())
    private val main = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)

    override fun invoke(method: String, payload: ByteArray, completion: ModuleCompletion) {
        if (method != "decodeQrImage" || !busy.compareAndSet(false, true)) {
            fail(completion, "QR image decoder is unavailable or busy.")
            return
        }
        executor.execute {
            try {
                val values = WireMap.decode(payload)
                val source = (values["uri"] as? WireValue.Text)?.value ?: error("Missing image URI")
                require(source.length <= 8192)
                val uri = Uri.parse(source)
                require(uri.scheme == "content" || uri.scheme == "file" || uri.scheme == "pam-file")
                val bitmap = readBitmap(uri)
                val scanner = try {
                    BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
                } catch (error: Exception) {
                    bitmap.recycle()
                    throw error
                }
                try {
                    scanner.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnCompleteListener { task ->
                            val codes = if (task.isSuccessful) task.result.mapNotNull { it.rawValue }.filter { it.isNotEmpty() }.distinct().take(16) else null
                            scanner.close()
                            bitmap.recycle()
                            busy.set(false)
                            if (codes == null) {
                                fail(completion, "Unable to decode the selected image.")
                            } else {
                                completion.complete(ModuleResultStatus.SUCCESS, WireMap.encode(mapOf("values" to WireValue.Text(JSONArray(codes).toString()))))
                            }
                        }
                } catch (error: Exception) {
                    scanner.close()
                    bitmap.recycle()
                    throw error
                }
            } catch (_: Exception) {
                busy.set(false)
                fail(completion, "Unable to decode the selected image.")
            }
        }
    }

    private fun fail(completion: ModuleCompletion, message: String) {
        main.post { completion.complete(ModuleResultStatus.FAILURE, message.toByteArray()) }
    }

    private fun readBitmap(uri: Uri): Bitmap {
        val snapshot = File.createTempFile("pam-qr-", ".image", context.cacheDir)
        try {
            val source = if (uri.scheme == "pam-file") resolvePamFile(context.filesDir, uri) else uri
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input)
                snapshot.outputStream().use { ImageInput.copyBounded(input, it) }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(snapshot.path, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = ImageDecodeSize.sample(bounds.outWidth, bounds.outHeight)
            }
            return requireNotNull(BitmapFactory.decodeFile(snapshot.path, options))
        } finally {
            snapshot.delete()
        }
    }
}

internal fun resolvePamFile(filesDirectory: File, uri: Uri): Uri {
    require(uri.scheme.equals("pam-file", ignoreCase = true) && uri.authority.isNullOrEmpty())
    require(uri.query == null && uri.fragment == null)
    val segments = uri.pathSegments
    require(segments.isNotEmpty() && segments.all {
        it.isNotEmpty() && it != "." && it != ".." && '/' !in it && '\\' !in it &&
            it.none { character -> character.code < 0x20 || character.code == 0x7f }
    })
    val root = File(filesDirectory, "pam-files").canonicalFile
    val source = File(root, segments.joinToString(File.separator)).canonicalFile
    require(source.toPath().startsWith(root.toPath()) && source.isFile)
    return Uri.fromFile(source)
}
