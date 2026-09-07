import Foundation

final class RecentScans {
    private let capacity: Int
    private var entries: [(value: String, timestamp: Int64)] = []

    init(capacity: Int = 128) {
        precondition(capacity > 0)
        self.capacity = capacity
    }

    func accept(_ value: String, now: Int64, interval: Int64) -> Bool {
        guard !value.isEmpty else { return false }
        entries.removeAll { now - $0.timestamp >= interval }
        guard !entries.contains(where: { $0.value == value }) else { return false }
        if entries.count >= capacity { entries.removeFirst() }
        entries.append((value, now))
        return true
    }

    func clear() { entries.removeAll() }
}
