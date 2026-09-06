import Foundation

final class ScannerLifetime: @unchecked Sendable {
    private let lock = NSLock()
    private var released = false
    private var revision = 0

    var generation: Int {
        lock.lock()
        defer { lock.unlock() }
        return revision
    }

    func invalidate() {
        lock.lock()
        defer { lock.unlock() }
        revision += 1
    }

    func isCurrent(_ generation: Int) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return !released && generation == revision
    }

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
