package dev.pam.scanner

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One transient worker and at most eight waiting jobs; never execute image work on the caller. */
internal class QrWorkQueue : AutoCloseable {
    private val executor = ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(8))
    fun submit(work: () -> Unit): Boolean = try {
        executor.execute(work)
        true
    } catch (_: RejectedExecutionException) { false }
    override fun close() { executor.shutdownNow() }
}
