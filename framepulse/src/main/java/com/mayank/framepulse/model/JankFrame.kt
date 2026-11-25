package com.mayank.framepulse.model

/**
 * One frame that was jank, delivered as soon as it is measured.
 *
 * A frame is jank under the same rule [FrameMetrics.jankCount] uses: it took longer than the
 * deadline the system gave it where the platform reports one (drawn frames on API 31+), and
 * otherwise longer than the configured jank threshold.
 *
 * @property frameTimeNanos When the frame happened, in the same time base as
 *   [FrameMetrics.lastFrameTimeNanos]: the start of the frame for drawn frames, the
 *   `Choreographer` callback's `frameTimeNanos` (the end of the long interval) otherwise.
 * @property durationMs How long the frame took, in milliseconds: the measured duration for drawn
 *   frames, the interval since the previous callback otherwise.
 * @property budgetMs The limit the frame went over, in milliseconds: the system's deadline where
 *   reported, otherwise [com.mayank.framepulse.api.FramePulseConfig.jankThresholdMs].
 * @property isFrozen Whether the frame took longer than
 *   [FrameMetrics.FROZEN_FRAME_THRESHOLD_MS].
 * @property stages Where the frame's time went. Null for `Choreographer` frames, which have no
 *   breakdown.
 * @property tags Tags set with [com.mayank.framepulse.api.FramePulseSession.setTag] at any time
 *   while the frame ran, by name. Empty when none were set.
 */
public data class JankFrame(
    val frameTimeNanos: Long,
    val durationMs: Double,
    val budgetMs: Double,
    val isFrozen: Boolean,
    val stages: FrameStages?,
    val tags: Map<String, String>,
)
