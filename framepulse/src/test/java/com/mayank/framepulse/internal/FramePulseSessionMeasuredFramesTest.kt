package com.mayank.framepulse.internal

import com.mayank.framepulse.FramePulseConfig
import com.mayank.framepulse.internal.metrics.RollingWindowFpsCalculator
import com.mayank.framepulse.internal.source.FakeFrameSource
import com.mayank.framepulse.internal.source.FakeRefreshRateSource
import com.mayank.framepulse.FramePulseMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** A session fed by a frame source that delivers measured frames. */
class FramePulseSessionMeasuredFramesTest {

    private val frames = FakeFrameSource()
    private val received = mutableListOf<FramePulseMetrics>()

    private fun newSession(refreshRateSource: FakeRefreshRateSource? = null) =
        FramePulseSessionImpl(
            config = FramePulseConfig.Builder().reportIntervalMs(0).build(),
            listener = { received += it },
            calculator = RollingWindowFpsCalculator(),
            monitorFactory = { consumer ->
                FrameMonitorImpl(frames, consumer, isMainThread = { true })
            },
            isMainThread = { true },
            refreshRateSource = refreshRateSource,
        )

    @Test
    fun measuredFramesReachTheListenerWithTheirDurations() {
        val session = newSession()
        session.start()

        frames.emitMeasured(START, 5 * NANOS_PER_MS)
        frames.emitMeasured(START + PERIOD, 9 * NANOS_PER_MS)

        assertEquals(2, received.size)
        assertEquals(2L, received.last().totalFrameCount)
        assertEquals(9.0, received.last().maxFrameTimeMs, 0.001)
        assertEquals(7.0, received.last().averageFrameTimeMs, 0.001)
    }

    @Test
    fun aFrameOverItsDeadlineIsJank() {
        val session = newSession()
        session.start()

        frames.emitMeasured(START, 12 * NANOS_PER_MS, deadlineNanos = 11 * NANOS_PER_MS)

        assertEquals(1L, received.single().totalJankCount)
    }

    @Test
    fun noListenerCallsAfterStopEvenForStrayMeasuredFrames() {
        val session = newSession()
        session.start()
        frames.emitMeasured(START, 5 * NANOS_PER_MS)

        session.stop()
        frames.emitStrayMeasured(START + PERIOD, 5 * NANOS_PER_MS)

        assertEquals(1, received.size)
        assertFalse(session.isRunning)
    }

    @Test
    fun withoutADeadlineTheBudgetFollowsTheDisplayRefreshRate() {
        // 60 Hz: a 20 ms frame is under the 25 ms threshold. 120 Hz: it is over 12.5 ms.
        val display = FakeRefreshRateSource(60f)
        val session = newSession(display)
        session.start()

        frames.emitMeasured(START, 20 * NANOS_PER_MS)
        assertEquals(0L, received.last().totalJankCount)

        display.changeTo(120f)
        frames.emitMeasured(START + PERIOD, 20 * NANOS_PER_MS)

        assertEquals(1L, received.last().totalJankCount)
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val PERIOD = 16_666_667L
        const val START = 7_000_000_000_000L
    }
}
