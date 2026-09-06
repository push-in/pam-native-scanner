import Foundation

final class ScannerLifetime: @unchecked Sendable {
    private let lock = NSLock()
    private var released = false

    var isActive: Bool {
        lock.lock()
        defer { lock.unlock() }
        return !released
    }

    func release() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard !released else { return false }
        released = true
        return true
    }
}
