package com.mayank.framepulse.internal.source

/**
 * Abstraction over platform-specific frame signal registration.
 *
 * Responsibilities:
 * - Start and stop a recurring frame callback chain on a target [android.os.Looper].
 * - Invoke [frameConsumer] with monotonic `frameTimeNanos` on each frame tick.
 *
 * Must not:
 * - Perform FPS calculation, logging policy, or listener dispatch.
 *
 * Future role:
 * - Allows [com.mayank.framepulse.internal.FrameMonitorImpl] to stay platform-agnostic in tests
 *   via fakes, and to swap in alternate sources (e.g. platform frame metrics) later.
 */
internal interface FrameSource {

    /**
     * Registers frame callbacks and begins delivering timestamps to [frameConsumer].
     *
     * @param frameConsumer Invoked on the frame source's looper thread once per frame.
     * @throws IllegalStateException if already started.
     */
    fun start(frameConsumer: (frameTimeNanos: Long) -> Unit)

    /** Unregisters frame callbacks and stops further [frameConsumer] invocations. Idempotent. */
    fun stop()

    /** Whether frame callbacks are currently registered. */
    val isRunning: Boolean
}
