package com.mayank.framepulse.internal.source

/**
 * Receives frame signals from a [FrameSource].
 *
 * An interface with primitive parameters, not a Kotlin function type: calling a
 * `(Long) -> Unit` boxes the timestamp into a `java.lang.Long` on every frame.
 *
 * Threading: called on the frame source's looper thread (the main thread).
 */
internal interface FrameConsumer {

    /**
     * A frame tick whose duration is not known, only its time.
     *
     * @param frameTimeNanos Monotonic frame time, in the `System.nanoTime()` time base.
     */
    fun onFrameTick(frameTimeNanos: Long)
}
