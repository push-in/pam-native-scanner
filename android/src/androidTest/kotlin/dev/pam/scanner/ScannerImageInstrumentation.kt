package dev.pam.scanner

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.view.PreviewView
import dev.pam.nativeapp.modules.ModuleCompletion
import dev.pam.nativeapp.modules.ModuleResultStatus
import dev.pam.nativeapp.protocol.WireMap
import dev.pam.nativeapp.protocol.WireValue
import org.json.JSONArray
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScannerImageInstrumentation : Instrumentation() {
    private var generationOnly = false
    override fun onCreate(arguments: Bundle?) {
        generationOnly = arguments?.getString("generationOnly") == "true"
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val folder = File(targetContext.cacheDir, "scanner-image-contracts").apply { mkdirs() }
        val previousSnapshots = targetContext.cacheDir.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("pam-qr-") }.map { it.name }.toSet()
        try {
            val module = QrImageModule(targetContext)
            verifyGeneration(module)
            if (generationOnly) {
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS scanner QR generation: roundtrip bounds cleanup\n") })
                return
            }
            for ((name, expected) in listOf("single.png" to setOf("pam-image-one"), "multiple.png" to setOf("pam-first", "pam-second"))) {
                val file = File(folder, name)
                context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
                check(decode(module, Uri.fromFile(file).toString()) == expected) { "Unexpected codes in $name" }
            }
            val privateFolder = File(targetContext.filesDir, "pam-files/imports").apply { mkdirs() }
            val privateQr = File(privateFolder, "qr pix.png")
            context.assets.open("single.png").use { input -> privateQr.outputStream().use { input.copyTo(it) } }
            check(decode(module, "pam-file:///imports/qr%20pix.png") == setOf("pam-image-one"))
            check(decode(module, "pam-file:///../secret.png", failureExpected = true).isEmpty())
            val blank = File(folder, "blank.png")
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            blank.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            check(decode(module, Uri.fromFile(blank).toString()).isEmpty()) { "Blank image contained a QR" }
            check(decode(module, "https://example.test/remote.png", failureExpected = true).isEmpty())
            val corrupt = File(folder, "corrupt.png").apply { writeText("not an image") }
            check(decode(module, Uri.fromFile(corrupt).toString(), failureExpected = true).isEmpty())
            val oversized = File(folder, "oversized.image")
            RandomAccessFile(oversized, "rw").use { it.setLength(32L * 1024 * 1024 + 1) }
            check(decode(module, Uri.fromFile(oversized).toString(), failureExpected = true).isEmpty())
            val remainingSnapshots = targetContext.cacheDir.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("pam-qr-") }.map { it.name }.toSet()
            check(remainingSnapshots == previousSnapshots) { "Image snapshots were not removed" }
            verifyPhysicalCameraLifecycle()
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS scanner contracts: ML Kit images and physical camera lifecycle\n") })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL scanner image contracts: ${error.javaClass.simpleName}: ${error.message}\n") })
        } finally {
            folder.deleteRecursively()
            File(targetContext.filesDir, "pam-files/imports/qr pix.png").delete()
        }
    }

    private fun verifyGeneration(module: QrImageModule) {
        for (text in listOf("https://zechat.com.br/@teste", "otpauth://totp/Zé?secret=JBSWY3DPEHPK3PXP&issuer=ZeChat", "Olá 👋")) {
            val uri = QrImageGenerator.generate(targetContext.cacheDir, text, 440)
            val file = File(requireNotNull(Uri.parse(uri).path))
            check(file.isFile)
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.path, options)
            check(options.outWidth == 440 && options.outHeight == 440)
            check(decode(module, uri) == setOf(text)) { "Generated QR did not roundtrip" }
            QrImageGenerator.release(targetContext.cacheDir, uri)
            check(!file.exists())
        }
        for ((text, pixels) in listOf("" to 256, "x".repeat(2049) to 256, "ok" to 127, "ok" to 1025)) {
            var rejected = false
            try { QrImageGenerator.generate(targetContext.cacheDir, text, pixels) } catch (_: IllegalArgumentException) { rejected = true }
            check(rejected)
        }
        val cache = File(targetContext.cacheDir, "pam-qr-generated")
        val generated = (1..20).map { QrImageGenerator.generate(targetContext.cacheDir, "bounded-$it", 128) }
        check(cache.listFiles().orEmpty().count { it.isFile } <= 16) { "QR cache grew without bound" }
        generated.forEach { QrImageGenerator.release(targetContext.cacheDir, it) }
        check(cache.listFiles().orEmpty().isEmpty())
        val large = QrImageGenerator.generate(targetContext.cacheDir, "maximum", 1024)
        QrImageGenerator.release(targetContext.cacheDir, large)
        val foreign = File(targetContext.cacheDir, "qr-foreign.png").apply { writeText("keep") }
        try {
            var rejected = false
            try { QrImageGenerator.release(targetContext.cacheDir, Uri.fromFile(foreign).toString()) } catch (_: IllegalArgumentException) { rejected = true }
            check(rejected && foreign.exists())
        } finally { foreign.delete() }
    }

    @ExperimentalGetImage
    private fun verifyPhysicalCameraLifecycle() {
        val activity = startActivitySync(Intent(targetContext, ScannerCameraActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ScannerCameraActivity
        val events = mutableListOf<Map<String, WireValue>>()
        val factory = ScannerViewFactory(activity)
        lateinit var host: ViewGroup
        runOnMainSync {
            host = factory.create(activity) { payload -> synchronized(events) { events += WireMap.decode(payload) } } as ViewGroup
            activity.setContentView(host)
            factory.update(host, scannerProperties(enabled = true))
        }
        val preview = host.getChildAt(0) as PreviewView
        awaitStream(preview, PreviewView.StreamState.STREAMING, "Camera preview did not start")

        runOnMainSync { factory.update(host, scannerProperties(enabled = false)) }
        awaitStream(preview, PreviewView.StreamState.IDLE, "Disabled scanner kept the camera stream")
        runOnMainSync { factory.update(host, scannerProperties(enabled = true)) }
        awaitStream(preview, PreviewView.StreamState.STREAMING, "Re-enabled scanner did not restart")

        runOnMainSync { activity.moveTaskToBack(true) }
        awaitStream(preview, PreviewView.StreamState.IDLE, "Background scanner kept the camera stream")
        runOnMainSync {
            activity.startActivity(Intent(activity, ScannerCameraActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        awaitStream(preview, PreviewView.StreamState.STREAMING, "Foreground scanner did not resume")

        runOnMainSync { factory.release(host); activity.finish() }
        awaitStream(preview, PreviewView.StreamState.IDLE, "Released scanner kept the camera stream")
        val failures = synchronized(events) { events.filter { (it["event"] as? WireValue.Integer)?.value == 3L } }
        check(failures.isEmpty()) { "Camera emitted a native failure" }
    }

    private fun scannerProperties(enabled: Boolean): Map<String, WireValue> = mapOf(
        "formats" to WireValue.Text("1"),
        "facing" to WireValue.Integer(1),
        "enabled" to WireValue.Flag(enabled),
        "torch" to WireValue.Flag(false),
        "duplicateIntervalMillis" to WireValue.Integer(1_500),
    )

    private fun awaitStream(preview: PreviewView, expected: PreviewView.StreamState, message: String) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 15_000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (preview.previewStreamState.value == expected) return
            Thread.sleep(100)
        }
        error("$message; state=${preview.previewStreamState.value}")
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
