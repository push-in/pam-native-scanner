import CoreImage
import Foundation
import ImageIO

let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
defer { try? FileManager.default.removeItem(at: folder) }

func image(_ values: [String], name: String, orientation: Int = 1) throws -> URL {
    let width = max(1, values.count) * 360
    let context = CGContext(data: nil, width: width, height: 360, bitsPerComponent: 8,
                            bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    context.setFillColor(CGColor(gray: 1, alpha: 1))
    context.fill(CGRect(x: 0, y: 0, width: CGFloat(width), height: 360))
    context.interpolationQuality = .none
    for (index, value) in values.enumerated() {
        let filter = CIFilter(name: "CIQRCodeGenerator")!
        filter.setValue(Data(value.utf8), forKey: "inputMessage")
        filter.setValue("M", forKey: "inputCorrectionLevel")
        let code = filter.outputImage!
        let raster = CIContext().createCGImage(code, from: code.extent)!
        context.draw(raster, in: CGRect(x: CGFloat(index * 360 + 40), y: 40, width: 280, height: 280))
    }
    let url = folder.appendingPathComponent(name + ".png")
    let destination = CGImageDestinationCreateWithURL(url as CFURL, "public.png" as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, context.makeImage()!, [kCGImagePropertyOrientation: orientation] as CFDictionary)
    precondition(CGImageDestinationFinalize(destination))
    return url
}

let single = try QrImageDecoder.decode(image(["pam-image-one"], name: "single"))
precondition(single == ["pam-image-one"], "Single QR content was not decoded")
let multiple = try QrImageDecoder.decode(image(["pam-first", "pam-second"], name: "multiple"))
precondition(Set(multiple) == Set(["pam-first", "pam-second"]), "Multiple QR values must remain selectable")
let rotated = try QrImageDecoder.decode(image(["pam-rotated"], name: "rotated", orientation: 6))
precondition(rotated == ["pam-rotated"], "Image orientation must be handled")
let blank = try QrImageDecoder.decode(image([], name: "blank"))
precondition(blank.isEmpty, "A blank image must produce no QR values")
let corrupt = folder.appendingPathComponent("corrupt.png")
try Data("not an image".utf8).write(to: corrupt)
do {
    _ = try QrImageDecoder.decode(corrupt)
    fatalError("Corrupt image must fail")
} catch {}
do {
    _ = try QrImageDecoder.decode(URL(string: "https://example.test/image.png")!)
    fatalError("Remote URL must fail without network access")
} catch {}
print("PASS: real Vision decoding for single, multiple, oriented, blank and invalid images")

let boundary = try image(["pam-bounded-image"], name: "boundary")
let handle = try FileHandle(forWritingTo: boundary)
try handle.truncate(atOffset: UInt64(QrImageDecoder.maximumBytes))
try handle.close()
let boundedCodes = try QrImageDecoder.decode(boundary)
precondition(boundedCodes == ["pam-bounded-image"], "Valid image at byte limit must remain readable")
let oversized = try FileHandle(forWritingTo: boundary)
try oversized.truncate(atOffset: UInt64(QrImageDecoder.maximumBytes + 1))
try oversized.close()
do {
    _ = try QrImageDecoder.decode(boundary)
    fatalError("Encoded image above 32 MiB must fail")
} catch {}
print("PASS: encoded QR image byte limit")
