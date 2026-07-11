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
 * Future role:
 * - Implemented in [com.mayank.framepulse.internal] using a pluggable frame source and forwarding
 *   `frameTimeNanos` to the metrics layer on each frame tick.
 */
interface FrameMonitor {

    /**
     * Begins monitoring on the looper implied by [config] (main looper when unspecified).
     */
    fun start(config: FramePulseConfig)

    /**
     * Stops monitoring and unregisters frame callbacks.
     */
    fun stop()

    /** Whether frame callbacks are currently registered. */
    val isRunning: Boolean
}
