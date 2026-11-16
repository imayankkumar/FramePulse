package com.mayank.framepulse.internal.source

/**
 * Turns the raw values of one platform frame report into at most one
 * [FrameConsumer.onFrameMeasured] call. Kept free of Android types so it is unit tested on the
 * JVM.
 *
 * Dropped reports:
 * - The first frame of a new window layout. The platform documents these as expected to be slow
 *   and exempt from jank calculations.
 * - Reports without a positive total duration (the platform returns -1 for a missing value).
 *
 * @param isFirstDrawFrame Whether the platform flagged the frame as a first draw frame.
 * @param totalDurationNanos Total frame duration.
 * @param intendedVsyncNanos When the frame was meant to start; not positive when the platform
 *   does not report it (API 24 and 25).
 * @param deadlineNanos Time allotted to the frame; not positive when the platform does not
 *   report it (below API 31).
 * @param nowNanos Current `System.nanoTime()`. Used to estimate the start when
 *   [intendedVsyncNanos] is missing.
 */
internal fun deliverMeasuredFrame(
    consumer: FrameConsumer,
    isFirstDrawFrame: Boolean,
    totalDurationNanos: Long,
    intendedVsyncNanos: Long,
    deadlineNanos: Long,
    nowNanos: Long,
) {
    if (isFirstDrawFrame || totalDurationNanos <= 0L) return
    val startNanos =
        if (intendedVsyncNanos > 0L) intendedVsyncNanos else nowNanos - totalDurationNanos
    val deadline = if (deadlineNanos > 0L) deadlineNanos else FrameConsumer.NO_DEADLINE
    consumer.onFrameMeasured(startNanos, totalDurationNanos, deadline)
}
