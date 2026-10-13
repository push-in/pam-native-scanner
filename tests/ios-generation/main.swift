import Foundation
import ImageIO

@main
struct QrGenerationContracts {
    static func main() throws {
        for text in ["https://zechat.com.br/@teste", "otpauth://totp/Zé?secret=JBSWY3DPEHPK3PXP&issuer=ZeChat", "Olá 👋"] {
            let uri = try QrImageGenerator.generate(text: text, pixels: 440)
            let url = URL(string: uri)!
            let source = CGImageSourceCreateWithURL(url as CFURL, nil)!
            let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil)! as NSDictionary
            precondition(properties[kCGImagePropertyPixelWidth] as? Int == 440)
            precondition(properties[kCGImagePropertyPixelHeight] as? Int == 440)
            let decoded = try QrImageDecoder.decode(url)
            precondition(decoded == [text])
            try QrImageGenerator.release(uri)
            precondition(!FileManager.default.fileExists(atPath: url.path))
        }
        for (text, size) in [("", 256), (String(repeating: "x", count: 2049), 256), ("valid", 127), ("valid", 1025)] {
            do { _ = try QrImageGenerator.generate(text: text, pixels: size); fatalError("Invalid QR accepted") }
            catch QrImageGenerator.GenerationError.invalid {}
        }
        print("PASS QR generation: Unicode roundtrip, dimensions, bounds and cleanup")
    }
}
