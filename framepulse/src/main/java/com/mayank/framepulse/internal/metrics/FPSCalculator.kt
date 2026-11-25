package com.mayank.framepulse.internal.metrics

import com.mayank.framepulse.api.FramePulseConfig
import com.mayank.framepulse.model.FrameMetrics

/**
 * Computes frame-rate and timing statistics from frame ticks or from measured frames.
 *
 * Responsibilities:
 * - Accept monotonic frame timestamps (e.g. `frameTimeNanos` from a frame source), or frames
 *   with a measured duration.
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

    /**
     * Records a frame whose duration the platform measured and returns updated metrics when a
     * snapshot is ready. Frames with a non-positive duration are ignored.
     *
     * @param frameTimeNanos When the frame started.
     * @param durationNanos How long the frame took.
     * @param deadlineNanos Time the system allotted to the frame; not positive when unknown.
     * @param config Active session configuration.
     * @return Latest [FrameMetrics], or null if no snapshot is due.
     */
    fun onMeasuredFrame(
        frameTimeNanos: Long,
        durationNanos: Long,
        deadlineNanos: Long,
        config: FramePulseConfig,
    ): FrameMetrics?

    /**
     * Whether the most recent [onFrame] or [onMeasuredFrame] call recorded a frame. False when
     * it ignored it (a first tick, a time that did not move forward, or no positive duration).
     */
    val lastFrameWasRecorded: Boolean

    /**
     * Whether the frame passed to the most recent [onFrame] or [onMeasuredFrame] call was
     * recorded as jank. False when that call ignored the frame.
     */
    val lastFrameWasJank: Boolean

    /**
     * Frame time of the most recently recorded frame, in nanoseconds: its measured duration, or
     * the interval since the previous tick.
     */
    val lastFrameDurationNanos: Long

    /**
     * The limit the most recently recorded frame was judged against, in nanoseconds: its
     * deadline where one was given, otherwise [FramePulseConfig.jankThresholdMs].
     */
    val lastFrameBudgetNanos: Double
}
