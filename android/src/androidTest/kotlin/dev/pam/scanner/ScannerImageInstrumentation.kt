package dev.pam.scanner

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import dev.pam.nativeapp.modules.ModuleCompletion
import dev.pam.nativeapp.modules.ModuleResultStatus
import dev.pam.nativeapp.protocol.WireMap
import dev.pam.nativeapp.protocol.WireValue
import org.json.JSONArray
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScannerImageInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val folder = File(targetContext.cacheDir, "scanner-image-contracts").apply { mkdirs() }
        try {
            val module = QrImageModule(targetContext)
            for ((name, expected) in listOf("single.png" to setOf("pam-image-one"), "multiple.png" to setOf("pam-first", "pam-second"))) {
                val file = File(folder, name)
                context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
                check(decode(module, Uri.fromFile(file).toString()) == expected) { "Unexpected codes in $name" }
            }
            val blank = File(folder, "blank.png")
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            blank.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            check(decode(module, Uri.fromFile(blank).toString()).isEmpty()) { "Blank image contained a QR" }
            check(decode(module, "https://example.test/remote.png", failureExpected = true).isEmpty())
            val corrupt = File(folder, "corrupt.png").apply { writeText("not an image") }
            check(decode(module, Uri.fromFile(corrupt).toString(), failureExpected = true).isEmpty())
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS scanner image contracts: ML Kit single, multiple, blank, remote and corrupt images\n") })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL scanner image contracts: ${error.javaClass.simpleName}: ${error.message}\n") })
        } finally {
            folder.deleteRecursively()
        }
    }

    private fun decode(module: QrImageModule, uri: String, failureExpected: Boolean = false): Set<String> {
        val latch = CountDownLatch(1)
        var resultStatus: ModuleResultStatus? = null
        var resultPayload = byteArrayOf()
        module.invoke("decodeQrImage", WireMap.encode(mapOf("uri" to WireValue.Text(uri))), ModuleCompletion { status, payload ->
            resultStatus = status
            resultPayload = payload
            latch.countDown()
        })
        check(latch.await(30, TimeUnit.SECONDS)) { "QR image callback timed out" }
        check(resultStatus == if (failureExpected) ModuleResultStatus.FAILURE else ModuleResultStatus.SUCCESS) { "Unexpected native result" }
        if (failureExpected) return emptySet()
        val json = (WireMap.decode(resultPayload)["values"] as WireValue.Text).value
        val values = JSONArray(json)
        return (0 until values.length()).map { values.getString(it) }.toSet()
    }
}
