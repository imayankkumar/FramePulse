package com.mayank.framepulse.internal.source

/**
 * Abstraction over platform-specific frame signal registration.
 *
 * Responsibilities:
 * - Start and stop a recurring frame callback chain on a target [android.os.Looper].
 * - Deliver each frame signal to a [FrameConsumer].
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
     * Registers frame callbacks and begins delivering frames to [frameConsumer].
     *
     * @param frameConsumer Invoked on the frame source's looper thread once per frame.
     * @throws IllegalStateException if already started.
     */
    fun start(frameConsumer: FrameConsumer)

    /** Unregisters frame callbacks and stops further [frameConsumer] invocations. Idempotent. */
    fun stop()

    /** Whether frame callbacks are currently registered. */
    val isRunning: Boolean
}
