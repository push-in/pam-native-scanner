import Foundation

let lifetime = ScannerLifetime()
precondition(lifetime.isActive)
let lock = NSLock()
var releases = 0
DispatchQueue.concurrentPerform(iterations: 100) { _ in
    if lifetime.release() {
        lock.lock()
        releases += 1
        lock.unlock()
    }
}
precondition(releases == 1, "Concurrent disposal must run only once")
precondition(!lifetime.isActive, "Late callbacks must observe disposal")
precondition(!lifetime.release(), "Repeated disposal must be harmless")
print("Scanner lifetime: concurrent release and late-callback guard passed")

let pendingScan = ScannerLifetime()
let generation = pendingScan.generation
precondition(pendingScan.isCurrent(generation))
pendingScan.invalidate()
precondition(!pendingScan.isCurrent(generation), "An old frame must not emit after a scanner update")
precondition(pendingScan.isCurrent(pendingScan.generation))

let scans = RecentScans(capacity: 2)
precondition(scans.accept("a", now: 100, interval: 1500))
precondition(!scans.accept("a", now: 1599, interval: 1500))
precondition(scans.accept("a", now: 1600, interval: 1500))
precondition(scans.accept("b", now: 1601, interval: 1500))
precondition(scans.accept("c", now: 1602, interval: 1500))
precondition(!scans.accept("b", now: 1603, interval: 1500))
precondition(scans.accept("a", now: 1603, interval: 1500))
scans.clear()
precondition(scans.accept("a", now: 1604, interval: 1500))
precondition(!scans.accept("", now: 1604, interval: 0))
precondition(scans.accept("a", now: 1604, interval: 0))
precondition(scans.accept("a", now: 1604, interval: 0))
print("Scanner duplicates: expiry, capacity, clear and disabled suppression passed")
