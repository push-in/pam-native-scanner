import Foundation
import ImageIO
import Vision

enum QrImageDecoder {
    static func decode(_ url: URL) throws -> [String] {
        guard url.isFileURL else { throw DecodeError.invalid }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: 2048,
                kCGImageSourceShouldCacheImmediately: true
              ] as CFDictionary) else { throw DecodeError.invalid }
        let request = VNDetectBarcodesRequest()
        request.symbologies = [.qr]
        try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
        var codes: [String] = []
        for observation in request.results ?? [] {
            guard let value = observation.payloadStringValue, !value.isEmpty, !codes.contains(value) else { continue }
            codes.append(value)
            if codes.count == 16 { break }
        }
        return codes
    }

    private enum DecodeError: Error { case invalid }
}
