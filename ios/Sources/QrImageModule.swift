import Foundation
import PamNative

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
                guard case let .text(uri)? = values["uri"], uri.utf8.count <= 8192 else {
                    throw ImageError.invalid
                }
                let url = try Self.sourceURL(uri)
                let codes = try QrImageDecoder.decode(url)
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

    private static func sourceURL(_ source: String) throws -> URL {
        guard let uri = URLComponents(string: source), uri.query == nil, uri.fragment == nil else {
            throw ImageError.invalid
        }
        if uri.scheme?.lowercased() == "file", let url = uri.url, url.isFileURL { return url }
        guard uri.scheme?.lowercased() == "pam-file", uri.host?.isEmpty ?? true else {
            throw ImageError.invalid
        }
        let path = uri.percentEncodedPath.removingPercentEncoding ?? ""
        let segments = path.split(separator: "/", omittingEmptySubsequences: true).map(String.init)
        guard !segments.isEmpty, segments.allSatisfy({ segment in
            !segment.isEmpty && segment != "." && segment != ".." &&
                !segment.contains("/") && !segment.contains("\\") &&
                !segment.unicodeScalars.contains(where: { $0.value < 0x20 || $0.value == 0x7f })
        }) else { throw ImageError.invalid }
        let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("pam-files", isDirectory: true).resolvingSymlinksInPath()
        let url = segments.reduce(root) { $0.appendingPathComponent($1) }.resolvingSymlinksInPath()
        let values = try? url.resourceValues(forKeys: [.isRegularFileKey])
        guard url.path.hasPrefix(root.path + "/"), values?.isRegularFile == true else {
            throw ImageError.invalid
        }
        return url
    }
}

private enum ImageError: Error { case invalid }
