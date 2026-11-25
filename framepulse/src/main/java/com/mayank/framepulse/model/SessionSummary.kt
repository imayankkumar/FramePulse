package com.mayank.framepulse.model

/**
 * Totals for a whole FramePulse session, from
 * [com.mayank.framepulse.api.FramePulseSession.summary].
 *
 * A frame counts here under the same rules as [FrameMetrics]: drawn frames for a session started
 * with an `Activity` on API 24+, `Choreographer` intervals otherwise.
 *
 * Percentiles cover every frame of the session. To keep memory constant they are computed from a
 * histogram, so they are rounded up to a multiple of 0.1 ms up to 50 ms and of 1 ms up to 1 s,
 * and never exceed [maxFrameTimeMs]. Above 1 s they report [maxFrameTimeMs].
 *
 * @property durationMs How long the session has run, in milliseconds: from start until now, or
 *   until it was stopped.
 * @property frameCount Frames observed.
 * @property jankCount Jank frames observed.
 * @property frozenFrameCount Frames that took longer than
 *   [FrameMetrics.FROZEN_FRAME_THRESHOLD_MS].
 * @property p50FrameTimeMs Median frame time, in milliseconds.
 * @property p90FrameTimeMs 90th percentile frame time, in milliseconds.
 * @property p95FrameTimeMs 95th percentile frame time, in milliseconds.
 * @property p99FrameTimeMs 99th percentile frame time, in milliseconds.
 * @property maxFrameTimeMs Longest frame time, in milliseconds. Exact.
 * @property tags One entry per tag value that was set during the session, in the order they
 *   were first set. See [TagSummary].
 */
public data class SessionSummary(
    val durationMs: Double,
    val frameCount: Long,
    val jankCount: Long,
    val frozenFrameCount: Long,
    val p50FrameTimeMs: Double,
    val p90FrameTimeMs: Double,
    val p95FrameTimeMs: Double,
    val p99FrameTimeMs: Double,
    val maxFrameTimeMs: Double,
    val tags: List<TagSummary>,
) {
    /** Share of frames that were jank, from 0.0 to 1.0. 0.0 when no frame was observed. */
    public val jankRate: Double
        get() = if (frameCount == 0L) 0.0 else jankCount.toDouble() / frameCount
}

/**
 * Frames that ran while one tag had one value, for example `screen = Feed`.
 *
 * A frame is counted for every tag value that was set at any time while it ran, the same rule
 * [JankFrame.tags] uses. One frame can therefore count for several tags, and for two values of
 * the same tag when the value changed during the frame.
 *
 * @property name The tag's name.
 * @property value The tag's value.
 * @property frameCount Frames that ran while the tag had this value.
 * @property jankCount Of those, the jank frames.
 * @property frozenFrameCount Of those, the frozen frames.
 */
public data class TagSummary(
    val name: String,
    val value: String,
    val frameCount: Long,
    val jankCount: Long,
    val frozenFrameCount: Long,
) {
    /** Share of this tag's frames that were jank, from 0.0 to 1.0. 0.0 without frames. */
    public val jankRate: Double
        get() = if (frameCount == 0L) 0.0 else jankCount.toDouble() / frameCount
}
