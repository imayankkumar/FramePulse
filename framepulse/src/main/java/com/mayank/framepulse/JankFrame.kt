package com.mayank.framepulse

/**
 * One frame that was jank, delivered as soon as it is measured.
 *
 * A frame is jank under the same rule [FramePulseMetrics.jankCount] uses: it took longer than the
 * deadline the system gave it where the platform reports one (drawn frames on API 31+), and
 * otherwise longer than the configured jank threshold.
 *
 * @property frameTimeNanos When the frame happened, in the same time base as
 *   [FramePulseMetrics.lastFrameTimeNanos]: the start of the frame for drawn frames, the
 *   `Choreographer` callback's `frameTimeNanos` (the end of the long interval) otherwise.
 * @property durationMs How long the frame took, in milliseconds: the measured duration for drawn
 *   frames, the interval since the previous callback otherwise.
 * @property budgetMs The limit the frame went over, in milliseconds: the system's deadline where
 *   reported, otherwise [FramePulseConfig.jankThresholdMs].
 * @property isFrozen Whether the frame took longer than
 *   [FramePulseMetrics.FROZEN_FRAME_THRESHOLD_MS].
 * @property stages Where the frame's time went. Null for `Choreographer` frames, which have no
 *   breakdown.
 * @property tags Tags set with [FramePulseSession.setTag] at any time
 *   while the frame ran, by name. Empty when none were set.
 */
public class JankFrame internal constructor(
    public val frameTimeNanos: Long,
    public val durationMs: Double,
    public val budgetMs: Double,
    public val isFrozen: Boolean,
    public val stages: FrameStages?,
    public val tags: Map<String, String>,
) {
    override fun equals(other: Any?): Boolean =
        this === other || other is JankFrame &&
            frameTimeNanos == other.frameTimeNanos &&
            durationMs == other.durationMs &&
            budgetMs == other.budgetMs &&
            isFrozen == other.isFrozen &&
            stages == other.stages &&
            tags == other.tags

    override fun hashCode(): Int {
        var result = frameTimeNanos.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + budgetMs.hashCode()
        result = 31 * result + isFrozen.hashCode()
        result = 31 * result + (stages?.hashCode() ?: 0)
        result = 31 * result + tags.hashCode()
        return result
    }

    override fun toString(): String =
        "JankFrame(frameTimeNanos=$frameTimeNanos, durationMs=$durationMs, budgetMs=$budgetMs, " +
            "isFrozen=$isFrozen, stages=$stages, tags=$tags)"
}
