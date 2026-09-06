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
