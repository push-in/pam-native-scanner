import Foundation
import ImageIO
import Vision

enum QrImageDecoder {
    static let maximumBytes = 32 * 1024 * 1024

    static func decode(_ url: URL) throws -> [String] {
        guard url.isFileURL else { throw DecodeError.invalid }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let data = try boundedData(url)
        guard let source = CGImageSourceCreateWithData(data as CFData, [kCGImageSourceShouldCache: false] as CFDictionary),
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

    private static func boundedData(_ url: URL) throws -> Data {
        let attributes = try url.resourceValues(forKeys: [.isRegularFileKey, .fileSizeKey])
        guard attributes.isRegularFile == true, let size = attributes.fileSize,
              size > 0, size <= maximumBytes, let input = InputStream(url: url) else {
            throw DecodeError.invalid
        }
        input.open()
        defer { input.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 64 * 1024)
        while true {
            let count = input.read(&buffer, maxLength: buffer.count)
            guard count >= 0 else { throw DecodeError.invalid }
            if count == 0 { break }
            guard count <= maximumBytes - data.count else { throw DecodeError.invalid }
            data.append(contentsOf: buffer.prefix(count))
        }
        guard !data.isEmpty else { throw DecodeError.invalid }
        return data
    }

    private enum DecodeError: Error { case invalid }
}
