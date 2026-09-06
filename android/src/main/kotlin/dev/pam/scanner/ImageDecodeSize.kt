package dev.pam.scanner

internal object ImageDecodeSize {
    fun sample(width: Int, height: Int): Int {
        require(width > 0 && height > 0) { "Invalid image dimensions" }
        var sample = 1
        while ((width.toLong() + sample - 1) / sample > 2048 || (height.toLong() + sample - 1) / sample > 2048) {
            sample *= 2
        }
        return sample
    }
}
