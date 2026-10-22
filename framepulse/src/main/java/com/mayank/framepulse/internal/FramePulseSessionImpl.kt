package com.mayank.framepulse.internal

import com.mayank.framepulse.api.FramePulseConfig
import com.mayank.framepulse.api.FramePulseListener
import com.mayank.framepulse.api.FramePulseSession
import com.mayank.framepulse.core.FrameMonitor
import com.mayank.framepulse.metrics.FPSCalculator

/**
 * Internal [FramePulseSession]: wires a [FrameMonitor] to an [FPSCalculator] and a listener.
 *
 * Each frame tick from the monitor goes to the calculator; when the calculator returns a
 * snapshot, the listener is called synchronously on the same (main) thread. Calling [stop]
 * from inside the listener is allowed and prevents any further calls.
 *
 * @param monitorFactory Builds the monitor from the per-frame consumer. Injected so tests can
 *   use a fake frame source.
 */
internal class FramePulseSessionImpl(
    private val config: FramePulseConfig,
    private val listener: FramePulseListener,
    private val calculator: FPSCalculator,
    monitorFactory: (frameConsumer: (Long) -> Unit) -> FrameMonitor,
) : FramePulseSession {

    private val monitor: FrameMonitor = monitorFactory(::onFrame)

    /** Starts frame delivery. Main thread only; at most once per session. */
    fun start() {
        monitor.start(config)
    }

    override fun stop() {
        monitor.stop()
    }

    override val isRunning: Boolean
        get() = monitor.isRunning

    private fun onFrame(frameTimeNanos: Long) {
        val metrics = calculator.onFrame(frameTimeNanos, config) ?: return
        listener.onMetrics(metrics)
    }
}
