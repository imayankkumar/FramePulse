package com.mayank.framepulse

/**
 * Totals for a whole FramePulse session, from
 * [FramePulseSession.summary].
 *
 * A frame counts here under the same rules as [FramePulseMetrics]: drawn frames for a session started
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
 *   [FramePulseMetrics.FROZEN_FRAME_THRESHOLD_MS].
 * @property p50FrameTimeMs Median frame time, in milliseconds.
 * @property p90FrameTimeMs 90th percentile frame time, in milliseconds.
 * @property p95FrameTimeMs 95th percentile frame time, in milliseconds.
 * @property p99FrameTimeMs 99th percentile frame time, in milliseconds.
 * @property maxFrameTimeMs Longest frame time, in milliseconds. Exact.
 * @property tags One entry per tag value that was set during the session, in the order they
 *   were first set. See [TagSummary].
 */
public class SessionSummary internal constructor(
    public val durationMs: Double,
    public val frameCount: Long,
    public val jankCount: Long,
    public val frozenFrameCount: Long,
    public val p50FrameTimeMs: Double,
    public val p90FrameTimeMs: Double,
    public val p95FrameTimeMs: Double,
    public val p99FrameTimeMs: Double,
    public val maxFrameTimeMs: Double,
    public val tags: List<TagSummary>,
) {
    /** Share of frames that were jank, from 0.0 to 1.0. 0.0 when no frame was observed. */
    public val jankRate: Double
        get() = if (frameCount == 0L) 0.0 else jankCount.toDouble() / frameCount

    override fun equals(other: Any?): Boolean =
        this === other || other is SessionSummary &&
            durationMs == other.durationMs &&
            frameCount == other.frameCount &&
            jankCount == other.jankCount &&
            frozenFrameCount == other.frozenFrameCount &&
            p50FrameTimeMs == other.p50FrameTimeMs &&
            p90FrameTimeMs == other.p90FrameTimeMs &&
            p95FrameTimeMs == other.p95FrameTimeMs &&
            p99FrameTimeMs == other.p99FrameTimeMs &&
            maxFrameTimeMs == other.maxFrameTimeMs &&
            tags == other.tags

    override fun hashCode(): Int {
        var result = durationMs.hashCode()
        result = 31 * result + frameCount.hashCode()
        result = 31 * result + jankCount.hashCode()
        result = 31 * result + frozenFrameCount.hashCode()
        result = 31 * result + p50FrameTimeMs.hashCode()
        result = 31 * result + p90FrameTimeMs.hashCode()
        result = 31 * result + p95FrameTimeMs.hashCode()
        result = 31 * result + p99FrameTimeMs.hashCode()
        result = 31 * result + maxFrameTimeMs.hashCode()
        result = 31 * result + tags.hashCode()
        return result
    }

    override fun toString(): String =
        "SessionSummary(durationMs=$durationMs, frameCount=$frameCount, jankCount=$jankCount, " +
            "frozenFrameCount=$frozenFrameCount, p50FrameTimeMs=$p50FrameTimeMs, " +
            "p90FrameTimeMs=$p90FrameTimeMs, p95FrameTimeMs=$p95FrameTimeMs, " +
            "p99FrameTimeMs=$p99FrameTimeMs, maxFrameTimeMs=$maxFrameTimeMs, tags=$tags)"
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
public class TagSummary internal constructor(
    public val name: String,
    public val value: String,
    public val frameCount: Long,
    public val jankCount: Long,
    public val frozenFrameCount: Long,
) {
    /** Share of this tag's frames that were jank, from 0.0 to 1.0. 0.0 without frames. */
    public val jankRate: Double
        get() = if (frameCount == 0L) 0.0 else jankCount.toDouble() / frameCount

    override fun equals(other: Any?): Boolean =
        this === other || other is TagSummary &&
            name == other.name &&
            value == other.value &&
            frameCount == other.frameCount &&
            jankCount == other.jankCount &&
            frozenFrameCount == other.frozenFrameCount

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + frameCount.hashCode()
        result = 31 * result + jankCount.hashCode()
        result = 31 * result + frozenFrameCount.hashCode()
        return result
    }

    override fun toString(): String =
        "TagSummary(name=$name, value=$value, frameCount=$frameCount, jankCount=$jankCount, " +
            "frozenFrameCount=$frozenFrameCount)"
}
