package com.usbstream.sender.util

import android.os.Process
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight performance monitor. Logs CPU, memory, and thread priority diagnostics.
 * Runs on a low-priority background thread so it never competes with codec threads.
 */
class PerformanceMonitor {

    private var executor: ScheduledExecutorService? = null
    private val frameCount = AtomicLong(0)
    private val bytesSent = AtomicLong(0)
    private val startNs = AtomicLong(System.nanoTime())

    fun start() {
        executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "perf-monitor").apply { priority = Thread.MIN_PRIORITY }
        }
        executor?.scheduleAtFixedRate(::report, 5, 5, TimeUnit.SECONDS)
        Timber.i("PerformanceMonitor started")
    }

    fun recordFrame() { frameCount.incrementAndGet() }
    fun recordBytes(n: Long) { bytesSent.addAndGet(n) }

    private fun report() {
        val elapsedMs = (System.nanoTime() - startNs.get()) / 1_000_000L
        val fps = frameCount.get() * 1000f / elapsedMs.coerceAtLeast(1)
        val mbSent = bytesSent.get() / (1024f * 1024f)
        val pid = Process.myPid()
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)

        Timber.d(
            "PERF | FPS=%.1f | MB_sent=%.1f | mem_used=%.1fMB | pid=$pid",
            fps, mbSent, usedMb
        )
    }

    fun stop() {
        executor?.shutdownNow()
        executor = null
    }
}
