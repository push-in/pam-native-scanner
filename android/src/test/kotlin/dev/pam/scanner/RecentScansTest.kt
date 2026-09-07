package dev.pam.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentScansTest {
    @Test
    fun duplicateExpiresAtTheConfiguredBoundary() {
        val scans = RecentScans()
        assertTrue(scans.accept("pix-a", 100, 1500))
        assertFalse(scans.accept("pix-a", 1599, 1500))
        assertTrue(scans.accept("pix-a", 1600, 1500))
        assertTrue(scans.accept("pix-b", 1600, 1500))
    }

    @Test
    fun capacityEvictsOldestInsteadOfGrowingWithEveryCode() {
        val scans = RecentScans(capacity = 2)
        assertTrue(scans.accept("a", 0, 1500))
        assertTrue(scans.accept("b", 1, 1500))
        assertTrue(scans.accept("c", 2, 1500))
        assertFalse(scans.accept("b", 3, 1500))
        assertTrue(scans.accept("a", 3, 1500))
    }

    @Test
    fun disabledSuppressionAndClearAllowAnotherScan() {
        val scans = RecentScans()
        assertFalse(scans.accept("", 0, 0))
        assertTrue(scans.accept("a", 0, 0))
        assertTrue(scans.accept("a", 0, 0))
        assertTrue(scans.accept("b", 0, 1500))
        scans.clear()
        assertTrue(scans.accept("b", 1, 1500))
    }
}
