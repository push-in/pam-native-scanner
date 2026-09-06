package dev.pam.scanner

import android.content.Context
import android.graphics.BitmapFactory
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class QrImageModule(private val context: Context) : NativeModule {
    private val executor = Executors.newSingleThreadExecutor()
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
                require(uri.scheme == "content" || uri.scheme == "file")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri).use { stream ->
                    requireNotNull(stream)
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = ImageDecodeSize.sample(bounds.outWidth, bounds.outHeight)
                }
                val bitmap = context.contentResolver.openInputStream(uri).use { stream ->
                    requireNotNull(stream)
                    requireNotNull(BitmapFactory.decodeStream(stream, null, options))
                }
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
}
