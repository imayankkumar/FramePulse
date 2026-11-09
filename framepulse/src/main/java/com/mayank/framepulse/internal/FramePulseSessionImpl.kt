package com.mayank.framepulse.internal

import com.mayank.framepulse.api.FramePulseConfig
import com.mayank.framepulse.api.FramePulseListener
import com.mayank.framepulse.api.FramePulseSession
import com.mayank.framepulse.core.FrameMonitor
import com.mayank.framepulse.internal.source.RefreshRateSource
import com.mayank.framepulse.metrics.FPSCalculator

/**
 * Internal [FramePulseSession]: wires a [FrameMonitor] to an [FPSCalculator] and a listener.
 *
 * Each frame tick from the monitor goes to the calculator; when the calculator returns a
 * snapshot, the listener is called synchronously on the same (main) thread. Calling [stop]
 * from inside the listener is allowed and prevents any further calls.
 *
 * When a [refreshRateSource] is given, the frame budget follows the display: the rate read at
 * [start] replaces [FramePulseConfig.expectedFramePeriodMs], and every later change replaces it
 * again. Each display change event allocates one [FramePulseConfig]; frames do not allocate.
 *
 * @param monitorFactory Builds the monitor from the per-frame consumer. Injected so tests can
 *   use a fake frame source.
 * @param refreshRateSource Optional display refresh-rate tracking. Null keeps the budget fixed.
 */
internal class FramePulseSessionImpl(
    config: FramePulseConfig,
    private val listener: FramePulseListener,
    private val calculator: FPSCalculator,
    monitorFactory: (frameConsumer: (Long) -> Unit) -> FrameMonitor,
    private val refreshRateSource: RefreshRateSource? = null,
) : FramePulseSession {

    private val monitor: FrameMonitor = monitorFactory(::onFrame)

    /** Config used for the next frame. Replaced only when the display refresh rate changes. */
    private var activeConfig: FramePulseConfig = config

    /**
     * Starts frame delivery. Main thread only; at most once per session.
     *
     * The monitor starts first, so a call off the main thread throws before anything is
     * registered. If the refresh-rate source then fails, the monitor is stopped and the failure
     * is rethrown. No frame can arrive in between because both run on the main thread.
     */
    fun start() {
        monitor.start(activeConfig)
        val source = refreshRateSource ?: return
        try {
            applyRefreshRate(source.start(::onRefreshRateChanged))
        } catch (exception: Exception) {
            monitor.stop()
            source.stop()
            throw exception
        }
    }

    override fun stop() {
        val wasRunning = monitor.isRunning
        // Throws off the main thread before changing anything (see FrameMonitorImpl.stop).
        monitor.stop()
        if (wasRunning) refreshRateSource?.stop()
    }

    override val isRunning: Boolean
        get() = monitor.isRunning

    private fun onFrame(frameTimeNanos: Long) {
        val metrics = calculator.onFrame(frameTimeNanos, activeConfig) ?: return
        listener.onMetrics(metrics)
    }

    private fun onRefreshRateChanged(refreshRateHz: Float) {
        if (!monitor.isRunning) return
        applyRefreshRate(refreshRateHz)
    }

    private fun applyRefreshRate(refreshRateHz: Float) {
        // Display.getRefreshRate() should always be positive; ignore anything else.
        if (!(refreshRateHz > 0f) || refreshRateHz.isInfinite()) return
        activeConfig = activeConfig.withRefreshRate(refreshRateHz)
    }
}
