package com.mayank.framepulse.internal

import com.mayank.framepulse.api.FramePulseConfig
import com.mayank.framepulse.api.FramePulseListener
import com.mayank.framepulse.api.FramePulseSession
import com.mayank.framepulse.api.JankFrameListener
import com.mayank.framepulse.internal.metrics.FPSCalculator
import com.mayank.framepulse.internal.source.FrameConsumer
import com.mayank.framepulse.internal.source.FrameStageNanos
import com.mayank.framepulse.internal.source.RefreshRateSource
import com.mayank.framepulse.model.FrameMetrics
import com.mayank.framepulse.model.FrameStages
import com.mayank.framepulse.model.JankFrame

/**
 * Internal [FramePulseSession]: wires a [FrameMonitor] to an [FPSCalculator] and the listeners.
 *
 * Each frame from the monitor (a tick or a measured frame, depending on the frame source) goes
 * to the calculator. Then, on the same (main) thread:
 * 1. if the frame was jank, [jankFrameListener] is called with a [JankFrame];
 * 2. if the calculator returned a snapshot, [listener] is called with it.
 *
 * Calling [stop] from inside either listener is allowed and prevents any further calls, including
 * the second call for the same frame.
 *
 * When a [refreshRateSource] is given, the frame budget follows the display: the rate read at
 * [start] replaces [FramePulseConfig.expectedFramePeriodMs], and every later change replaces it
 * again. Each display change event allocates one [FramePulseConfig]. Frames allocate only when
 * a snapshot or a jank frame is reported.
 *
 * @param jankFrameListener Optional; null skips building [JankFrame]s entirely.
 * @param monitorFactory Builds the monitor from the per-frame consumer. Injected so tests can
 *   use a fake frame source.
 * @param refreshRateSource Optional display refresh-rate tracking. Null keeps the budget fixed.
 */
internal class FramePulseSessionImpl(
    config: FramePulseConfig,
    private val listener: FramePulseListener,
    private val calculator: FPSCalculator,
    monitorFactory: (frameConsumer: FrameConsumer) -> FrameMonitor,
    private val refreshRateSource: RefreshRateSource? = null,
    private val jankFrameListener: JankFrameListener? = null,
) : FramePulseSession {

    private val frameConsumer = object : FrameConsumer {
        override fun onFrameTick(frameTimeNanos: Long) {
            val metrics = calculator.onFrame(frameTimeNanos, activeConfig)
            report(frameTimeNanos, stages = null, metrics)
        }

        override fun onFrameMeasured(
            frameTimeNanos: Long,
            durationNanos: Long,
            deadlineNanos: Long,
            stages: FrameStageNanos?,
        ) {
            val metrics = calculator.onMeasuredFrame(
                frameTimeNanos,
                durationNanos,
                deadlineNanos,
                activeConfig,
            )
            report(frameTimeNanos, stages, metrics)
        }
    }

    private val monitor: FrameMonitor = monitorFactory(frameConsumer)

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

    /** Calls the listeners for the frame the calculator has just recorded. */
    private fun report(frameTimeNanos: Long, stages: FrameStageNanos?, metrics: FrameMetrics?) {
        val jankListener = jankFrameListener
        if (jankListener != null && calculator.lastFrameWasJank) {
            jankListener.onJankFrame(jankFrame(frameTimeNanos, stages))
            // The jank listener may have stopped the session.
            if (!monitor.isRunning) return
        }
        if (metrics != null) listener.onMetrics(metrics)
    }

    private fun jankFrame(frameTimeNanos: Long, stages: FrameStageNanos?): JankFrame {
        val durationNanos = calculator.lastFrameDurationNanos
        return JankFrame(
            frameTimeNanos = frameTimeNanos,
            durationMs = durationNanos.toDouble() / NANOS_PER_MILLI,
            budgetMs = calculator.lastFrameBudgetNanos / NANOS_PER_MILLI,
            isFrozen = durationNanos > FrameMetrics.FROZEN_FRAME_THRESHOLD_MS * NANOS_PER_MILLI,
            stages = stages?.toFrameStages(),
        )
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

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

/**
 * Copies the reused holder into an immutable [FrameStages]. A stage the platform did not report
 * becomes 0, except the GPU stage, which becomes null.
 */
private fun FrameStageNanos.toFrameStages(): FrameStages = FrameStages(
    unknownDelayMs = stageMillis(unknownDelay),
    inputHandlingMs = stageMillis(inputHandling),
    animationMs = stageMillis(animation),
    layoutMeasureMs = stageMillis(layoutMeasure),
    drawMs = stageMillis(draw),
    syncMs = stageMillis(sync),
    commandIssueMs = stageMillis(commandIssue),
    swapBuffersMs = stageMillis(swapBuffers),
    gpuMs = if (gpu < 0L) null else stageMillis(gpu),
)

private fun stageMillis(nanos: Long): Double =
    if (nanos < 0L) 0.0 else nanos.toDouble() / 1_000_000L
