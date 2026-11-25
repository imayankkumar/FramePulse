package com.mayank.framepulse.internal.source

/**
 * Stage durations of one measured frame, in nanoseconds, as the platform reported them.
 *
 * Mutable and reused: a frame source fills one instance for each frame and passes it to
 * [FrameConsumer.onFrameMeasured]. The values are only valid during that call, so a consumer
 * that keeps them must copy them. This keeps the frame path free of allocations.
 *
 * A value is [NOT_REPORTED] when the platform does not report that stage.
 */
internal class FrameStageNanos {
    var unknownDelay: Long = NOT_REPORTED
    var inputHandling: Long = NOT_REPORTED
    var animation: Long = NOT_REPORTED
    var layoutMeasure: Long = NOT_REPORTED
    var draw: Long = NOT_REPORTED
    var sync: Long = NOT_REPORTED
    var commandIssue: Long = NOT_REPORTED
    var swapBuffers: Long = NOT_REPORTED
    var gpu: Long = NOT_REPORTED

    companion object {
        const val NOT_REPORTED: Long = -1L
    }
}
