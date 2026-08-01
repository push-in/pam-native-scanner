package dev.pam.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dev.pam.nativeapp.protocol.WireMap
import dev.pam.nativeapp.protocol.WireValue
import dev.pam.nativeapp.views.NativeViewFactory
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@ExperimentalGetImage
class ScannerViewFactory(@Suppress("UNUSED_PARAMETER") context: Context) : NativeViewFactory {
    override fun create(context: Context, emit: (ByteArray) -> Unit): View = ScannerHost(context).apply { emitter = emit }
    override fun update(view: View, properties: Map<String, WireValue>) { (view as ScannerHost).update(properties) }
    override fun release(view: View) { (view as ScannerHost).release() }
}

@ExperimentalGetImage
private class ScannerHost(context: Context) : FrameLayout(context) {
    var emitter: ((ByteArray) -> Unit)? = null
    private val previewView=PreviewView(context);private val executor=Executors.newSingleThreadExecutor();private val processing=AtomicBoolean(false)
    private var provider:ProcessCameraProvider?=null;private var camera:Camera?=null;private var scanner:BarcodeScanner=BarcodeScanning.getClient();private var facing=1L;private var torch=false;private var enabled=true;private var duplicateMillis=1500L;private var formatSpec="1";private val seen=mutableMapOf<String,Long>()
    init{addView(previewView,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));post{bind()}}
    fun update(values:Map<String,WireValue>){val nextFacing=values.integer("facing",1);val nextFormats=values.text("formats","1");torch=values.flag("torch",false);enabled=values.flag("enabled",true);duplicateMillis=values.integer("duplicateIntervalMillis",1500).coerceIn(0,60_000);if(nextFormats!=formatSpec){formatSpec=nextFormats;scanner.close();scanner=BarcodeScanning.getClient(options(formatSpec));bind()};if(nextFacing!=facing){facing=nextFacing;bind()};camera?.cameraControl?.enableTorch(torch)}
    private fun bind(){if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){send(mapOf("event" to WireValue.Integer(2),"message" to WireValue.Text("Camera permission is required")));return};val owner=context as? LifecycleOwner?:run{send(mapOf("event" to WireValue.Integer(3),"message" to WireValue.Text("Scanner host has no lifecycle")));return};val future=ProcessCameraProvider.getInstance(context);future.addListener({runCatching{val p=future.get();provider=p;p.unbindAll();val preview=Preview.Builder().build().also{it.surfaceProvider=previewView.surfaceProvider};val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also{it.setAnalyzer(executor,::analyze)};val selector=if(facing==2L)CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA;camera=p.bindToLifecycle(owner,selector,preview,analysis);camera?.cameraControl?.enableTorch(torch)}.onFailure{send(mapOf("event" to WireValue.Integer(3),"message" to WireValue.Text(it.message.orEmpty()))) }},ContextCompat.getMainExecutor(context))}
    @ExperimentalGetImage private fun analyze(proxy:androidx.camera.core.ImageProxy){if(!enabled||!processing.compareAndSet(false,true)){proxy.close();return};val image=proxy.image;if(image==null){processing.set(false);proxy.close();return};scanner.process(InputImage.fromMediaImage(image,proxy.imageInfo.rotationDegrees)).addOnSuccessListener{barcodes->val now=android.os.SystemClock.elapsedRealtime();barcodes.forEach{barcode->val value=barcode.rawValue.orEmpty();val last=seen[value];if(value.isNotEmpty()&&(last==null||now-last>=duplicateMillis)){seen[value]=now;send(mapOf("event" to WireValue.Integer(1),"value" to WireValue.Text(value),"format" to WireValue.Integer(format(barcode.format)),"valueKind" to WireValue.Integer(valueKind(barcode.valueType))))}}}.addOnFailureListener{send(mapOf("event" to WireValue.Integer(3),"message" to WireValue.Text(it.message.orEmpty())))}.addOnCompleteListener{processing.set(false);proxy.close()}}
    private fun options(spec:String):BarcodeScannerOptions{val formats=spec.split(',').mapNotNull{it.toIntOrNull()?.let(::nativeFormat)}.distinct();val builder=BarcodeScannerOptions.Builder();if(formats.isNotEmpty())builder.setBarcodeFormats(formats.first(),*formats.drop(1).toIntArray());return builder.build()}
    private fun nativeFormat(value:Int)=when(value){1->Barcode.FORMAT_QR_CODE;2->Barcode.FORMAT_AZTEC;3->Barcode.FORMAT_DATA_MATRIX;4->Barcode.FORMAT_PDF417;5->Barcode.FORMAT_CODE_128;6->Barcode.FORMAT_CODE_39;7->Barcode.FORMAT_CODE_93;8->Barcode.FORMAT_CODABAR;9->Barcode.FORMAT_EAN_13;10->Barcode.FORMAT_EAN_8;11->Barcode.FORMAT_ITF;12->Barcode.FORMAT_UPC_A;13->Barcode.FORMAT_UPC_E;else->Barcode.FORMAT_QR_CODE}
    private fun format(value:Int)=when(value){Barcode.FORMAT_QR_CODE->1L;Barcode.FORMAT_AZTEC->2;Barcode.FORMAT_DATA_MATRIX->3;Barcode.FORMAT_PDF417->4;Barcode.FORMAT_CODE_128->5;Barcode.FORMAT_CODE_39->6;Barcode.FORMAT_CODE_93->7;Barcode.FORMAT_CODABAR->8;Barcode.FORMAT_EAN_13->9;Barcode.FORMAT_EAN_8->10;Barcode.FORMAT_ITF->11;Barcode.FORMAT_UPC_A->12;Barcode.FORMAT_UPC_E->13;else->14}
    private fun valueKind(value:Int)=when(value){Barcode.TYPE_TEXT->2L;Barcode.TYPE_URL->3;Barcode.TYPE_EMAIL->4;Barcode.TYPE_PHONE->5;Barcode.TYPE_SMS->6;Barcode.TYPE_WIFI->7;Barcode.TYPE_CONTACT_INFO->8;Barcode.TYPE_CALENDAR_EVENT->9;Barcode.TYPE_GEO->10;Barcode.TYPE_DRIVER_LICENSE->11;Barcode.TYPE_ISBN->12;Barcode.TYPE_PRODUCT->13;else->1}
    private fun send(values:Map<String,WireValue>)=post{emitter?.invoke(WireMap.encode(values))}
    fun release(){provider?.unbindAll();provider=null;camera=null;scanner.close();executor.shutdownNow();seen.clear()}
    private fun Map<String,WireValue>.text(key:String,fallback:String)=(get(key)as?WireValue.Text)?.value?:fallback
    private fun Map<String,WireValue>.integer(key:String,fallback:Long)=(get(key)as?WireValue.Integer)?.value?:fallback
    private fun Map<String,WireValue>.flag(key:String,fallback:Boolean)=(get(key)as?WireValue.Flag)?.value?:fallback
}
