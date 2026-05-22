package com.usbstream.receiver.util

import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class PerformanceMonitor {

    private var executor: ScheduledExecutorService? = null
    private val frameCount = AtomicLong(0)
    private val bytesReceived = AtomicLong(0)
    private val startNs = AtomicLong(System.nanoTime())

    fun start() {
        executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "perf-monitor").apply { priority = Thread.MIN_PRIORITY }
        }
        executor?.scheduleAtFixedRate(::report, 5, 5, TimeUnit.SECONDS)
    }

    fun recordFrame() { frameCount.incrementAndGet() }
    fun recordBytes(n: Long) { bytesReceived.addAndGet(n) }

    private fun report() {
        val elapsedMs = (System.nanoTime() - startNs.get()) / 1_000_000L
        val fps = frameCount.get() * 1000f / elapsedMs.coerceAtLeast(1)
        val mbReceived = bytesReceived.get() / (1024f * 1024f)
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
        Timber.d("PERF | FPS=%.1f | MB_recv=%.1f | mem=%.1fMB", fps, mbReceived, usedMb)
    }

    fun stop() {
        executor?.shutdownNow()
        executor = null
    }
}
