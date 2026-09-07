import AVFoundation
import Foundation
import PamNative
import UIKit
import Vision

public final class ScannerViewFactory:NativeViewFactory,@unchecked Sendable{
    public init(){}
    public func create(context:AnyObject?,emit:@escaping(Data)->Void)->UIView{ScannerPreview(emit:emit)}
    public func update(view:UIView,properties:[String:WireValue]){(view as? ScannerPreview)?.update(properties)}
    public func release(view:UIView){(view as? ScannerPreview)?.releaseScanner()}
}

private final class ScannerPreview:UIView,AVCaptureVideoDataOutputSampleBufferDelegate,@unchecked Sendable{
    private let emit:(Data)->Void;private let session=AVCaptureSession();private let queue=DispatchQueue(label:"dev.pam.scanner.capture",qos:.userInitiated);private lazy var preview=AVCaptureVideoPreviewLayer(session:session)
    private let lifetime = ScannerLifetime()
    private var facing:Int64=1;private var torch=false;private var enabled=true;private var duplicateMillis:Int64=1500;private var formats="1";private let seen=RecentScans();private var configured=false
    init(emit:@escaping(Data)->Void){self.emit=emit;super.init(frame:.zero);preview.videoGravity = .resizeAspectFill;layer.addSublayer(preview);authorize()}
    required init?(coder:NSCoder){nil}
    override func layoutSubviews(){super.layoutSubviews();preview.frame=bounds}
    func update(_ values: [String: WireValue]) {
        lifetime.invalidate()
        queue.async { [weak self] in
            guard let self, self.lifetime.isActive else { return }
            let nextFacing = values.integer("facing", 1)
            let changedFacing = nextFacing != self.facing
            self.facing = nextFacing
            self.torch = values.flag("torch", false)
            self.enabled = values.flag("enabled", true)
            self.duplicateMillis = max(0, min(60_000, values.integer("duplicateIntervalMillis", 1500)))
            self.formats = values.text("formats", "1")
            if !self.enabled {
                if self.session.isRunning { self.session.stopRunning() }
            } else if changedFacing || !self.configured {
                self.configure()
            } else {
                if !self.session.isRunning && self.lifetime.isActive { self.session.startRunning() }
                self.applyTorch()
            }
        }
    }

    private func authorize() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            queue.async { [weak self] in self?.configure() }
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                guard let self, self.lifetime.isActive else { return }
                if granted { self.queue.async { [weak self] in self?.configure() } }
                else { self.send(["event": .integer(2), "message": .text("Camera permission was denied")]) }
            }
        default:
            send(["event": .integer(2), "message": .text("Camera permission is required")])
        }
    }
    private func configure(){guard lifetime.isActive && enabled else{return};session.beginConfiguration();session.inputs.forEach(session.removeInput);session.outputs.forEach(session.removeOutput);session.sessionPreset = .high;let position:AVCaptureDevice.Position=facing==2 ? .front:.back;guard let device=AVCaptureDevice.default(.builtInWideAngleCamera,for:.video,position:position)else{session.commitConfiguration();failure("Requested camera is unavailable");return};do{let input=try AVCaptureDeviceInput(device:device);guard session.canAddInput(input)else{throw ScannerError.configuration};session.addInput(input);let output=AVCaptureVideoDataOutput();output.alwaysDiscardsLateVideoFrames=true;output.videoSettings=[kCVPixelBufferPixelFormatTypeKey as String:kCVPixelFormatType_420YpCbCr8BiPlanarFullRange];output.setSampleBufferDelegate(self,queue:queue);guard session.canAddOutput(output)else{throw ScannerError.configuration};session.addOutput(output);if let connection=output.connection(with:.video),connection.isVideoOrientationSupported{connection.videoOrientation = .portrait};session.commitConfiguration();configured=true;if lifetime.isActive && enabled{session.startRunning();applyTorch()}}catch{session.commitConfiguration();failure(String(describing:error))}}
    func captureOutput(_ output:AVCaptureOutput,didOutput sampleBuffer:CMSampleBuffer,from connection:AVCaptureConnection){guard lifetime.isActive && enabled else{return};let generation=lifetime.generation;let request=VNDetectBarcodesRequest{[weak self] request,error in guard let self, self.lifetime.isCurrent(generation) else{return};if let error{self.failure(error.localizedDescription);return};let allowed=Set(self.formats.split(separator:",").compactMap{Int($0)});let now=Int64(ProcessInfo.processInfo.systemUptime*1000);for observation in request.results as? [VNBarcodeObservation] ?? []{guard let value=observation.payloadStringValue,!value.isEmpty else{continue};let format=self.format(observation.symbology);guard allowed.isEmpty||allowed.contains(Int(format))else{continue};guard self.seen.accept(value,now:now,interval:self.duplicateMillis)else{continue};self.send(["event":.integer(1),"value":.text(value),"format":.integer(format),"valueKind":.integer(self.valueKind(value))], generation:generation)}};request.symbologies=symbologies();do{try VNImageRequestHandler(cmSampleBuffer:sampleBuffer,orientation:.right,options:[:]).perform([request])}catch{failure(String(describing:error))}}
    private func symbologies()->[VNBarcodeSymbology]{formats.split(separator:",").compactMap{Int($0)}.compactMap{switch $0{case 1:return.qr;case 2:return.aztec;case 3:return.dataMatrix;case 4:return.pdf417;case 5:return.code128;case 6:return.code39;case 7:return.code93;case 8:return.codabar;case 9:return.ean13;case 10:return.ean8;case 11:return.itf14;case 12:return.upce;case 13:return.upce;default:return nil}}}
    private func format(_ value:VNBarcodeSymbology)->Int64{switch value{case .qr:return 1;case .aztec:return 2;case .dataMatrix:return 3;case .pdf417:return 4;case .code128:return 5;case .code39:return 6;case .code93:return 7;case .codabar:return 8;case .ean13:return 9;case .ean8:return 10;case .itf14:return 11;case .upce:return 13;default:return 14}}
    private func valueKind(_ value:String)->Int64{let lower=value.lowercased();if lower.hasPrefix("http://")||lower.hasPrefix("https://"){return 3};if lower.hasPrefix("mailto:"){return 4};if lower.hasPrefix("tel:"){return 5};if lower.hasPrefix("sms:"){return 6};if lower.hasPrefix("wifi:"){return 7};if lower.hasPrefix("geo:"){return 10};return 2}
    private func applyTorch(){guard lifetime.isActive else{return};guard let device=(session.inputs.first as? AVCaptureDeviceInput)?.device,device.hasTorch else{return};do{try device.lockForConfiguration();device.torchMode=torch ? .on:.off;device.unlockForConfiguration()}catch{failure(error.localizedDescription)}}
    private func failure(_ message:String){send(["event":.integer(3),"message":.text(message)])}
    private func send(_ values: [String: WireValue], generation: Int? = nil) {
        let generation = generation ?? lifetime.generation
        guard lifetime.isCurrent(generation), let data = try? WireMap.encode(values) else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self, self.lifetime.isCurrent(generation) else { return }
            self.emit(data)
        }
    }

    func releaseScanner() {
        guard lifetime.release() else { return }
        let captureSession = session
        queue.async { [weak self, captureSession] in
            if captureSession.isRunning { captureSession.stopRunning() }
            captureSession.inputs.forEach(captureSession.removeInput)
            captureSession.outputs.forEach(captureSession.removeOutput)
            self?.seen.clear()
        }
    }
    deinit{releaseScanner()}
}
private extension Dictionary where Key==String,Value==WireValue{func text(_ key:String,_ fallback:String)->String{if case let .text(v)?=self[key]{return v};return fallback};func integer(_ key:String,_ fallback:Int64)->Int64{if case let .integer(v)?=self[key]{return v};return fallback};func flag(_ key:String,_ fallback:Bool)->Bool{if case let .flag(v)?=self[key]{return v};return fallback}}
private enum ScannerError:Error{case configuration}
