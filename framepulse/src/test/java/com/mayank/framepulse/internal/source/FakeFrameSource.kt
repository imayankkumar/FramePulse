package com.mayank.framepulse.internal.source

/**
 * Test [FrameSource] that delivers frames only when the test calls [emit] or [emitMeasured].
 */
internal class FakeFrameSource : FrameSource {

    var startCount = 0
        private set
    var stopCount = 0
        private set

    /** When set, [start] throws this after recording the call. */
    var startFailure: Exception? = null

    private var consumer: FrameConsumer? = null

    /** The consumer passed to the most recent [start], kept even after [stop]. */
    private var lastConsumer: FrameConsumer? = null

    override val isRunning: Boolean
        get() = consumer != null

    override fun start(frameConsumer: FrameConsumer) {
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
        consumer?.onFrameTick(frameTimeNanos)
    }

    /** Delivers a measured frame like a real source would: only while running. */
    fun emitMeasured(
        frameTimeNanos: Long,
        durationNanos: Long,
        deadlineNanos: Long = -1L,
        stages: FrameStageNanos? = null,
    ) {
        consumer?.onFrameMeasured(frameTimeNanos, durationNanos, deadlineNanos, stages)
    }

    /** Simulates a callback that was already queued when the source was stopped. */
    fun emitStray(frameTimeNanos: Long) {
        lastConsumer?.onFrameTick(frameTimeNanos)
    }

    /** Like [emitStray], for a measured frame. */
    fun emitStrayMeasured(frameTimeNanos: Long, durationNanos: Long) {
        lastConsumer?.onFrameMeasured(frameTimeNanos, durationNanos, -1L, null)
    }
}
