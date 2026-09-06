package dev.pam.scanner

internal class RecentScans(private val capacity: Int = 128) {
    private val seen = linkedMapOf<String, Long>()

    init {
        require(capacity > 0)
    }

    fun accept(value: String, now: Long, interval: Long): Boolean {
        if (value.isEmpty()) return false
        seen.entries.removeAll { now - it.value >= interval }
        if (seen.containsKey(value)) return false
        if (seen.size >= capacity) seen.remove(seen.keys.first())
        seen[value] = now
        return true
    }

    fun clear() = seen.clear()
}
