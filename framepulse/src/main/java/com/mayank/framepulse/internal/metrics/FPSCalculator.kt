package com.mayank.framepulse.internal.metrics

import com.mayank.framepulse.api.FramePulseConfig
import com.mayank.framepulse.model.FrameMetrics

/**
 * Computes frame-rate and timing statistics from consecutive frame timestamps.
 *
 * Responsibilities:
 * - Accept monotonic frame timestamps (e.g. `frameTimeNanos` from a frame source).
 * - Maintain a rolling window aligned with [FramePulseConfig.windowDurationMs].
 * - Produce immutable [FrameMetrics] snapshots for the API layer.
 *
 * Must not:
 * - Register frame callbacks or depend on platform frame sources directly.
 * - Manage session lifecycle or listener dispatch.
 *
 * Future role:
 * - Core of the metrics pipeline; may be composed with a dedicated jank classifier and
 *   ring-buffer aggregator in later versions. Internal, so it can change freely.
 */
internal interface FPSCalculator {

    /**
     * Records a new frame timestamp and returns updated metrics when a snapshot is ready.
     *
     * @param frameTimeNanos Monotonic frame time from the frame source.
     * @param config Active session configuration.
     * @return Latest [FrameMetrics], or null if insufficient data for a snapshot yet.
     */
    fun onFrame(
        frameTimeNanos: Long,
        config: FramePulseConfig,
    ): FrameMetrics?
}
