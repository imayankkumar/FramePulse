package com.mayank.framepulse.internal.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [deliverMeasuredFrame]: which platform frame reports are passed on, and with what values. */
class MeasuredFrameTest {

    private class Recorder : FrameConsumer {
        val frames = mutableListOf<Triple<Long, Long, Long>>()

        override fun onFrameTick(frameTimeNanos: Long) {
            throw AssertionError("a measured frame must not be delivered as a tick")
        }

        override fun onFrameMeasured(
            frameTimeNanos: Long,
            durationNanos: Long,
            deadlineNanos: Long,
        ) {
            frames += Triple(frameTimeNanos, durationNanos, deadlineNanos)
        }
    }

    private val recorder = Recorder()

    private fun deliver(
        isFirstDrawFrame: Boolean = false,
        totalDurationNanos: Long = 5_000_000L,
        intendedVsyncNanos: Long = 9_000_000_000L,
        deadlineNanos: Long = 16_000_000L,
        nowNanos: Long = 9_020_000_000L,
    ) = deliverMeasuredFrame(
        consumer = recorder,
        isFirstDrawFrame = isFirstDrawFrame,
        totalDurationNanos = totalDurationNanos,
        intendedVsyncNanos = intendedVsyncNanos,
        deadlineNanos = deadlineNanos,
        nowNanos = nowNanos,
    )

    @Test
    fun passesStartDurationAndDeadlineThrough() {
        deliver()

        assertEquals(listOf(Triple(9_000_000_000L, 5_000_000L, 16_000_000L)), recorder.frames)
    }

    @Test
    fun firstDrawFramesAreDropped() {
        deliver(isFirstDrawFrame = true, totalDurationNanos = 300_000_000L)

        assertTrue(recorder.frames.isEmpty())
    }

    @Test
    fun reportsWithoutAPositiveDurationAreDropped() {
        deliver(totalDurationNanos = -1L)
        deliver(totalDurationNanos = 0L)

        assertTrue(recorder.frames.isEmpty())
    }

    @Test
    fun aMissingDeadlineIsReportedAsNoDeadline() {
        deliver(deadlineNanos = -1L)
        deliver(deadlineNanos = 0L)

        assertEquals(
            listOf(FrameConsumer.NO_DEADLINE, FrameConsumer.NO_DEADLINE),
            recorder.frames.map { it.third },
        )
    }

    @Test
    fun aMissingStartTimeIsEstimatedFromNowAndTheDuration() {
        deliver(
            intendedVsyncNanos = -1L,
            totalDurationNanos = 5_000_000L,
            nowNanos = 9_020_000_000L,
        )

        assertEquals(9_015_000_000L, recorder.frames.single().first)
    }
}
