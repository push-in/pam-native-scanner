package dev.pam.scanner

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class QrWorkQueueTest {
    @Test fun saturationRejectsWithoutRunningOnCallerAndRecoversAfterDrain() {
        val queue = QrWorkQueue()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val drained = CountDownLatch(8)
        val runs = AtomicInteger()
        try {
            assertTrue(queue.submit { entered.countDown(); release.await(5, TimeUnit.SECONDS) })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            repeat(8) { assertTrue(queue.submit { runs.incrementAndGet(); drained.countDown() }) }
            assertFalse(queue.submit { fail("Rejected task ran") })
            assertEquals(0, runs.get())
            release.countDown()
            assertTrue(drained.await(5, TimeUnit.SECONDS))
            assertEquals(8, runs.get())
            val recovered = CountDownLatch(1)
            assertTrue(queue.submit { recovered.countDown() })
            assertTrue(recovered.await(5, TimeUnit.SECONDS))
        } finally { release.countDown(); queue.close() }
        assertFalse(queue.submit { fail("Closed queue ran task") })
    }
}
