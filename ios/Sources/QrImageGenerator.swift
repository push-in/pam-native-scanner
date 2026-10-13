import Foundation
import CoreImage
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

internal enum QrImageGenerator {
    enum GenerationError: Error { case invalid, encoding }

    static func generate(text: String, pixels: Int) throws -> String {
        guard !text.isEmpty, text.utf8.count <= 2048, !text.contains("\0"), (128...1024).contains(pixels),
              let filter = CIFilter(name: "CIQRCodeGenerator") else { throw GenerationError.invalid }
        filter.setValue(Data(text.utf8), forKey: "inputMessage")
        filter.setValue("M", forKey: "inputCorrectionLevel")
        guard let qr = filter.outputImage else { throw GenerationError.encoding }
        let modules = Int(qr.extent.width)
        let scale = pixels / (modules + 8)
        guard scale >= 1 else { throw GenerationError.invalid }
        let context = CIContext(options: [.cacheIntermediates: false])
        guard let code = context.createCGImage(qr, from: qr.extent),
              let canvas = CGContext(data: nil, width: pixels, height: pixels, bitsPerComponent: 8, bytesPerRow: pixels * 4,
                 space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { throw GenerationError.encoding }
        canvas.setFillColor(CGColor(gray: 1, alpha: 1))
        canvas.fill(CGRect(x: 0, y: 0, width: pixels, height: pixels))
        canvas.interpolationQuality = .none
        let edge = modules * scale
        let offset = (pixels - edge) / 2
        canvas.draw(code, in: CGRect(x: offset, y: offset, width: edge, height: edge))
        guard let image = canvas.makeImage() else { throw GenerationError.encoding }
        let root = try directory()
        let files = (try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: [.contentModificationDateKey])) ?? []
        let owned = files.filter { validName($0.lastPathComponent) }.sorted {
            ((try? $0.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast) >
            ((try? $1.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast)
        }
        for (index, file) in owned.enumerated() {
            let date = (try? file.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            if index >= 15 || Date().timeIntervalSince(date) > 86400 { try? FileManager.default.removeItem(at: file) }
        }
        let url = root.appendingPathComponent("qr-\(UUID().uuidString.lowercased()).png")
        do {
            guard let target = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else { throw GenerationError.encoding }
            CGImageDestinationAddImage(target, image, nil)
            guard CGImageDestinationFinalize(target) else { throw GenerationError.encoding }
            try FileManager.default.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: url.path)
            return url.absoluteString
        } catch { try? FileManager.default.removeItem(at: url); throw error }
    }

    static func release(_ source: String) throws {
        guard let url = URL(string: source), url.isFileURL, url.query == nil, url.fragment == nil,
              validName(url.lastPathComponent), url.resolvingSymlinksInPath().deletingLastPathComponent() == (try directory()).resolvingSymlinksInPath()
        else { throw GenerationError.invalid }
        if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
    }
    private static func validName(_ name: String) -> Bool { name.range(of: "^qr-[a-f0-9-]+\\.png$", options: .regularExpression) != nil }
    private static func directory() throws -> URL {
        let root = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("pam-qr-generated", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        return root
    }
}
