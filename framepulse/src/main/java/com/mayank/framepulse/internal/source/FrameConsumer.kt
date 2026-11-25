package com.mayank.framepulse.internal.source

/**
 * Receives frame signals from a [FrameSource]. A source calls one of the two methods, always the
 * same one for its whole lifetime.
 *
 * An interface with primitive parameters, not a Kotlin function type: calling a
 * `(Long) -> Unit` boxes the timestamp into a `java.lang.Long` on every frame.
 *
 * Threading: called on the frame source's looper thread (the main thread).
 */
internal interface FrameConsumer {

    /**
     * A frame tick whose duration is not known, only its time. The time between two ticks is
     * taken as the frame time.
     *
     * @param frameTimeNanos Monotonic frame time, in the `System.nanoTime()` time base.
     */
    fun onFrameTick(frameTimeNanos: Long)

    /**
     * A frame the app drew, with the duration the platform measured for it.
     *
     * @param frameTimeNanos When the frame started. Only compared with other frame times from
     *   the same source.
     * @param durationNanos Time from the start of the frame until it was handed to the display
     *   system.
     * @param deadlineNanos Time the system allotted to this frame, or [NO_DEADLINE] when the
     *   platform does not report one.
     * @param stages Stage durations of this frame, or null when the source has none. Reused by
     *   the source: only valid during this call.
     */
    fun onFrameMeasured(
        frameTimeNanos: Long,
        durationNanos: Long,
        deadlineNanos: Long,
        stages: FrameStageNanos?,
    )

    companion object {
        const val NO_DEADLINE: Long = -1L
    }
}
