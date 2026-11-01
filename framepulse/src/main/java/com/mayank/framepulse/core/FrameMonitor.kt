package com.mayank.framepulse.core

import com.mayank.framepulse.api.FramePulseConfig

/**
 * Orchestrates frame signal acquisition for a single Android UI event loop (Looper).
 *
 * Responsibilities:
 * - Own monitoring lifecycle: start, stop, and [isRunning] state.
 * - Bridge frame timestamps from a frame source into the metrics layer.
 * - Ensure at most one active callback chain per session (no duplicate registration).
 *
 * Must not:
 * - Calculate FPS or classify jank (delegated to [com.mayank.framepulse.metrics.FPSCalculator]).
 * - Expose public listener types or publish [com.mayank.framepulse.model.FrameMetrics] directly.
 *
 * The default implementation lives in [com.mayank.framepulse.internal]. It uses a pluggable
 * frame source and forwards `frameTimeNanos` to the metrics layer on each frame tick.
 *
 * Threading: main-thread-confined and not thread-safe. A monitor runs once: IDLE, then
 * STARTED, then STOPPED. It cannot be restarted.
 */
interface FrameMonitor {

    /**
     * Begins monitoring on the main thread's Choreographer.
     *
     * [config] does not select a thread or looper; monitoring always runs on the main thread.
     *
     * @throws IllegalStateException if called off the main thread, if already started, or
     *   after [stop].
     */
    fun start(config: FramePulseConfig)

    /**
     * Stops monitoring and unregisters frame callbacks. Does nothing if not started.
     *
     * @throws IllegalStateException if called off the main thread while started. The monitor
     *   then stays started; call [stop] again on the main thread.
     */
    fun stop()

    /** Whether frame callbacks are currently registered. */
    val isRunning: Boolean
}
