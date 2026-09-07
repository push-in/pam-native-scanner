package dev.pam.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageDecodeSizeTest {
    @Test fun preservesSmallImagesAndBoundsLargeAllocations() {
        assertEquals(1, ImageDecodeSize.sample(640, 480))
        assertEquals(2, ImageDecodeSize.sample(2049, 2048))
        for ((width, height) in listOf(12000 to 9000, 1 to Int.MAX_VALUE, Int.MAX_VALUE to Int.MAX_VALUE)) {
            val sample = ImageDecodeSize.sample(width, height)
            assertTrue((width.toLong() + sample - 1) / sample <= 2048)
            assertTrue((height.toLong() + sample - 1) / sample <= 2048)
        }
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsMissingBounds() { ImageDecodeSize.sample(-1, 500) }
}
