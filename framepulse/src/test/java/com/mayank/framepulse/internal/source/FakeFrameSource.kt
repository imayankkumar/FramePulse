package com.mayank.framepulse.internal.source

/**
 * Test [FrameSource] that delivers timestamps only when the test calls [emit].
 */
internal class FakeFrameSource : FrameSource {

    var startCount = 0
        private set
    var stopCount = 0
        private set

    /** When set, [start] throws this after recording the call. */
    var startFailure: Exception? = null

    private var consumer: ((Long) -> Unit)? = null

    /** The consumer passed to the most recent [start], kept even after [stop]. */
    private var lastConsumer: ((Long) -> Unit)? = null

    override val isRunning: Boolean
        get() = consumer != null

    override fun start(frameConsumer: (frameTimeNanos: Long) -> Unit) {
        startCount++
        startFailure?.let { throw it }
        check(consumer == null) { "FakeFrameSource already running" }
        consumer = frameConsumer
        lastConsumer = frameConsumer
    }

    override fun stop() {
        stopCount++
        consumer = null
    }

    /** Delivers a frame like a real source would: only while running. */
    fun emit(frameTimeNanos: Long) {
        consumer?.invoke(frameTimeNanos)
    }

    /** Simulates a callback that was already queued when the source was stopped. */
    fun emitStray(frameTimeNanos: Long) {
        lastConsumer?.invoke(frameTimeNanos)
    }
}
