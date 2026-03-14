package com.perfecttranscribe.debug

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object PipelineLogger {
    private val nextOperationId = AtomicInteger(0)

    fun newOperationId(prefix: String): String = "$prefix-${nextOperationId.incrementAndGet()}"

    fun now(): Long = System.nanoTime()

    fun elapsedMs(startTimeNs: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNs)

    fun log(operationId: String, stage: String, details: String? = null) {
        val suffix = details
            ?.takeIf(String::isNotBlank)
            ?.let { " $it" }
            .orEmpty()
        println("PerfectTranscribe[$operationId] $stage$suffix")
    }

    fun logDuration(
        operationId: String,
        stage: String,
        startTimeNs: Long,
        details: String? = null,
    ): Long {
        val elapsedMs = elapsedMs(startTimeNs)
        val timing = "t=${elapsedMs}ms"
        val message = listOfNotNull(timing, details).joinToString(" ")
        log(operationId, stage, message)
        return elapsedMs
    }
}
