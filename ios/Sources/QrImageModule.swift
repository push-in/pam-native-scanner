import Foundation
import ImageIO
import PamNative
import Vision

public final class QrImageModule: NativeModule, @unchecked Sendable {
    private let queue = DispatchQueue(label: "pam.scanner.image", qos: .userInitiated)
    private let available = DispatchSemaphore(value: 1)

    public init() {}

    public func invoke(method: String, payload: Data, completion: @escaping ModuleCompletion) {
        guard method == "decodeQrImage", available.wait(timeout: .now()) == .success else {
            completion(.failure, Data("QR image decoder is unavailable or busy.".utf8))
            return
        }
        queue.async {
            let result: Result<Data, Error> = Result {
                let values = try WireMap.decode(payload)
                guard case let .text(uri)? = values["uri"], uri.utf8.count <= 8192,
                      let url = URL(string: uri), url.isFileURL else { throw ImageError.invalid }
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
                      let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                        kCGImageSourceCreateThumbnailFromImageAlways: true,
                        kCGImageSourceCreateThumbnailWithTransform: true,
                        kCGImageSourceThumbnailMaxPixelSize: 2048,
                        kCGImageSourceShouldCacheImmediately: true
                      ] as CFDictionary) else { throw ImageError.invalid }
                let request = VNDetectBarcodesRequest()
                request.symbologies = [.qr]
                try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
                var codes: [String] = []
                for observation in request.results ?? [] {
                    guard let value = observation.payloadStringValue, !value.isEmpty, !codes.contains(value) else { continue }
                    codes.append(value)
                    if codes.count == 16 { break }
                }
                let json = try JSONSerialization.data(withJSONObject: codes)
                return try WireMap.encode(["values": .text(String(decoding: json, as: UTF8.self))])
            }
            self.available.signal()
            DispatchQueue.main.async {
                switch result {
                case .success(let data): completion(.success, data)
                case .failure: completion(.failure, Data("Unable to decode the selected image.".utf8))
                }
            }
        }
    }
}

private enum ImageError: Error { case invalid }
